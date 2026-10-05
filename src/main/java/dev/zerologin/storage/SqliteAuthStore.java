package dev.zerologin.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 以 SQLite 单文件数据库保存账号数据。
 *
 * <p>并发模型：所有 SQL 都在<b>单条专用线程</b>上串行执行，因此一个 JDBC 连接即可保证线程安全，
 * 也不会出现 SQLite 的多写锁竞争；调用方拿到的一直是 {@link CompletableFuture}，主线程不会被阻塞。
 *
 * <p>数据库参数选择：
 * <ul>
 *   <li>{@code journal_mode=WAL}：读写并发更好，注册/登录高峰不阻塞读取；</li>
 *   <li>{@code synchronous=NORMAL}：WAL 下兼顾安全与写入延迟，配合 {@code busy_timeout} 避免瞬时锁冲突；</li>
 *   <li>{@code name_lc} 列与唯一索引：Java 版玩家名大小写不敏感，用独立小写列建索引即可走索引查询。</li>
 * </ul>
 *
 * <p>驱动由 Maven 打包时内联并 relocate 到独立包名，运行时不依赖服务端或其他插件提供的 sqlite-jdbc。
 */
public final class SqliteAuthStore implements AuthStore {

    private static final String SCHEMA_VERSION = "1";

    private final Path dbFile;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread t = new Thread(runnable, "ZeroLogin-SQLite");
        t.setDaemon(true);
        return t;
    });

    /** 仅在 IO 线程上访问；{@code null} 表示尚未 open 或已 close。 */
    private Connection connection;

    public SqliteAuthStore(Path dataFolder, String file, Logger logger) {
        this.dbFile = dataFolder.toAbsolutePath().resolve(file);
        this.logger = logger;
    }

    /** 实际数据库文件路径，用于日志与 /zerologin info。 */
    public Path databaseFile() {
        return dbFile;
    }

    @Override
    public void open() throws Exception {
        loadDriver();
        Files.createDirectories(dbFile.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        connection.setAutoCommit(true);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA foreign_keys=ON");
        }
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                      uuid           TEXT    NOT NULL PRIMARY KEY,
                      name           TEXT    NOT NULL,
                      name_lc        TEXT    NOT NULL,
                      password       TEXT    NOT NULL,
                      registered_at  INTEGER NOT NULL DEFAULT 0,
                      last_login_at  INTEGER NOT NULL DEFAULT 0,
                      last_seen_at   INTEGER NOT NULL DEFAULT 0,
                      login_count    INTEGER NOT NULL DEFAULT 0,
                      auto_login_ips TEXT    NOT NULL DEFAULT ''
                    )""");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_accounts_name_lc ON accounts(name_lc)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS meta (
                      key   TEXT NOT NULL PRIMARY KEY,
                      value TEXT NOT NULL
                    )""");
        }
        upsertMeta("schema", SCHEMA_VERSION);
        // 只用于校验迁移期间的异常记录，不参与在线业务流程。
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
            if (rs.next() && !"ok".equalsIgnoreCase(rs.getString(1))) {
                logger.log(Level.WARNING, "SQLite 完整性检查结果异常：{0}", rs.getString(1));
            }
        }
    }

    @Override
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException ex) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "关闭 SQLite 连接时出错：" + ex.getMessage());
        } finally {
            connection = null;
        }
    }

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    public CompletableFuture<AuthRecord> loadByUuid(UUID uuid) {
        return submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM accounts WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? map(rs) : null;
                }
            }
        });
    }

    @Override
    public CompletableFuture<AuthRecord> loadByName(String name) {
        return submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM accounts WHERE name_lc = ?")) {
                ps.setString(1, key(name));
                AuthRecord record = null;
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        record = map(rs);
                    }
                }
                if (record != null && !record.name().equals(name)) {
                    // 玩家改名后落到这里：更新记录并落盘，保证后续按新名字也能查到。
                    record.name(name);
                    write(record);
                }
                return record;
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(AuthRecord record) {
        java.util.Objects.requireNonNull(record, "record");
        return submit(() -> {
            write(record);
            return null;
        });
    }

    /** 批量保存：单事务提交，用于迁移与导入。 */
    public CompletableFuture<Void> saveAll(Collection<AuthRecord> records) {
        return submit(() -> {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (AuthRecord record : records) {
                    write(record);
                }
                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previous);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteByUuid(UUID uuid) {
        return submit(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM accounts WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                return ps.executeUpdate() > 0;
            }
        });
    }

    @Override
    public CompletableFuture<Integer> countAccounts() {
        return submit(() -> {
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    @Override
    public CompletableFuture<List<AuthRecord>> loadAll() {
        return submit(() -> {
            List<AuthRecord> out = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM accounts")) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        });
    }

    /** 批量读取 {@code name_lc} 在集合中的行，迁移时一次性检测所有名字冲突。 */
    public CompletableFuture<List<AuthRecord>> loadByNameKeys(Collection<String> lowerNames) {
        if (lowerNames.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return submit(() -> {
            List<AuthRecord> out = new ArrayList<>();
            String placeholders = String.join(",", java.util.Collections.nCopies(lowerNames.size(), "?"));
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM accounts WHERE name_lc IN (" + placeholders + ")")) {
                int i = 1;
                for (String name : lowerNames) {
                    ps.setString(i++, name);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(map(rs));
                    }
                }
            }
            return out;
        });
    }

    // ===== 内部实现 =====

    /** 把一段抛异常的数据库操作包装成异步任务，统一在 IO 线程上串行执行。 */
    private <T> CompletableFuture<T> submit(SqlTask<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            io.execute(() -> {
                try {
                    future.complete(task.run());
                } catch (Throwable ex) {
                    future.completeExceptionally(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(new IllegalStateException("存储已关闭", ex));
        }
        return future;
    }

    @FunctionalInterface
    private interface SqlTask<T> {
        T run() throws Exception;
    }

    private void write(AuthRecord record) throws SQLException {
        // SQLite 3.24+ 的 UPSERT；先按 uuid 覆盖，改名场景下同一行原地更新。
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO accounts (uuid, name, name_lc, password, registered_at,
                                      last_login_at, last_seen_at, login_count, auto_login_ips)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                  name = excluded.name,
                  name_lc = excluded.name_lc,
                  password = excluded.password,
                  registered_at = excluded.registered_at,
                  last_login_at = excluded.last_login_at,
                  last_seen_at = excluded.last_seen_at,
                  login_count = excluded.login_count,
                  auto_login_ips = excluded.auto_login_ips""")) {
            ps.setString(1, record.uuid().toString());
            ps.setString(2, record.name());
            ps.setString(3, key(record.name()));
            ps.setString(4, record.passwordHash());
            ps.setLong(5, record.registeredAt());
            ps.setLong(6, record.lastLoginAt());
            ps.setLong(7, record.lastSeenAt());
            ps.setInt(8, record.loginCount());
            ps.setString(9, String.join(",", record.autoLoginIps()));
            ps.executeUpdate();
        }
    }

    private void upsertMeta(String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private AuthRecord map(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        AuthRecord record = new AuthRecord(uuid,
                rs.getString("name"),
                rs.getString("password"),
                rs.getLong("registered_at"));
        record.lastLoginAt(rs.getLong("last_login_at"));
        record.lastSeenAt(rs.getLong("last_seen_at"));
        int count = rs.getInt("login_count");
        for (int i = 0; i < count; i++) {
            record.incrementLoginCount();
        }
        String ips = rs.getString("auto_login_ips");
        if (ips != null && !ips.isEmpty()) {
            for (String ip : ips.split(",")) {
                if (!ip.isEmpty()) {
                    record.addAutoLoginIp(ip);
                }
            }
        }
        return record;
    }

    /**
     * 显式加载驱动类。
     *
     * <p>打包时驱动被 relocate 到 {@code dev.zerologin.libs.sqlite}，而未打包（单元测试）时
     * 是原始的 {@code org.sqlite}，两者都尝试一次即可，避免依赖 services 文件是否被重写。
     */
    private void loadDriver() {
        for (String candidate : new String[]{
                "dev.zerologin.libs.sqlite.JDBC", "org.sqlite.JDBC"}) {
            try {
                Class.forName(candidate);
                return;
            } catch (ClassNotFoundException ignored) {
                // 继续尝试下一个
            }
        }
        // 交给 DriverManager 的自动发现兜底；真不可用时后续的 getConnection 会给出明确错误。
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
