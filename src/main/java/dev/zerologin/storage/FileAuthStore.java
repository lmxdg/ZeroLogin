package dev.zerologin.storage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 以“每个账号一个 YAML 文件”的形式把数据保存在插件目录下。
 *
 * <p>选择该布局的原因：
 * <ul>
 *   <li>单账号写入只重写一个文件，登录高峰不会产生整库重写；</li>
 *   <li>文件名为 UUID，玩家改名不会造成主键冲突或数据丢失；</li>
 *   <li>人工可读、可直接备份与同步到从服。</li>
 * </ul>
 *
 * <p>内存中维护 name→uuid 索引用于按名字查询；索引在 {@link #open()} 时从磁盘重建，
 * 因此重启后无需任何迁移操作。
 */
public final class FileAuthStore implements AuthStore {

    private static final String SUBDIR = "accounts";
    private static final String SCHEMA_KEY = "schema";
    private static final int SCHEMA_VERSION = 1;

    private final File baseDir;
    private final Logger logger;
    private final Map<UUID, AuthRecord> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    public FileAuthStore(File dataFolder, Logger logger) {
        this.baseDir = new File(dataFolder, SUBDIR);
        this.logger = logger;
    }

    @Override
    public void open() throws IOException {
        if (!baseDir.exists() && !baseDir.mkdirs() && !baseDir.isDirectory()) {
            throw new IOException("无法创建账号目录：" + baseDir.getAbsolutePath());
        }
        File[] files = baseDir.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files == null) {
            return;
        }
        int skipped = 0;
        for (File file : files) {
            try {
                AuthRecord record = read(file);
                if (record != null) {
                    cache.put(record.uuid(), record);
                    nameIndex.put(key(record.name()), record.uuid());
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                skipped++;
                logger.log(Level.WARNING, "账号文件解析失败，已跳过：{0}", file.getName());
            }
        }
        if (skipped > 0) {
            logger.log(Level.WARNING, "有 {0} 个账号文件无法解析，请检查 {1}",
                    new Object[]{skipped, baseDir.getAbsolutePath()});
        }
    }

    @Override
    public void close() {
        // 每次 save 都是立即落盘的，因此这里只需要释放索引引用。
        cache.clear();
        nameIndex.clear();
    }

    @Override
    public String name() {
        return "file";
    }

    @Override
    public CompletableFuture<AuthRecord> loadByUuid(UUID uuid) {
        AuthRecord cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        AuthRecord read = read(new File(baseDir, uuid + ".yml"));
        if (read != null) {
            cache.put(read.uuid(), read);
            nameIndex.put(key(read.name()), read.uuid());
        }
        return CompletableFuture.completedFuture(read);
    }

    @Override
    public CompletableFuture<AuthRecord> loadByName(String name) {
        String key = key(name);
        UUID known = nameIndex.get(key);
        if (known != null) {
            AuthRecord record = cache.get(known);
            if (record != null) {
                if (!record.name().equals(name)) {
                    // 玩家改名后落到这里：更新记录并落盘，保证后续按新名字也能查到。
                    record.name(name);
                    nameIndex.put(key, record.uuid());
                    writeAsync(record);
                }
                return CompletableFuture.completedFuture(record);
            }
        }
        // 索引未命中时做一次全量扫描，兼容他人手工放入 accounts 目录的文件。
        File[] files = baseDir.listFiles((dir, n) -> n.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                AuthRecord record = read(file);
                if (record == null) {
                    continue;
                }
                cache.put(record.uuid(), record);
                nameIndex.put(key(record.name()), record.uuid());
                if (key(record.name()).equals(key)) {
                    return CompletableFuture.completedFuture(record);
                }
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> save(AuthRecord record) {
        Objects.requireNonNull(record, "record");
        cache.put(record.uuid(), record);
        nameIndex.put(key(record.name()), record.uuid());
        return writeAsync(record);
    }

    @Override
    public CompletableFuture<Boolean> deleteByUuid(UUID uuid) {
        AuthRecord removed = cache.remove(uuid);
        if (removed != null) {
            nameIndex.remove(key(removed.name()), uuid);
        }
        File file = new File(baseDir, uuid + ".yml");
        boolean deleted = file.exists() && !file.delete();
        if (deleted) {
            logger.log(Level.WARNING, "账号文件删除失败：{0}", file.getAbsolutePath());
        }
        return CompletableFuture.completedFuture(!deleted && (removed != null || file.exists() == false));
    }

    @Override
    public CompletableFuture<Integer> countAccounts() {
        return CompletableFuture.completedFuture(cache.size());
    }

    /** 记录数很小且写入量低，直接同步写；调用方已在异步线程上。 */
    private CompletableFuture<Void> writeAsync(AuthRecord record) {
        try {
            write(record);
            return CompletableFuture.completedFuture(null);
        } catch (IOException ex) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    private void write(AuthRecord record) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set(SCHEMA_KEY, SCHEMA_VERSION);
        yaml.set("uuid", record.uuid().toString());
        yaml.set("name", record.name());
        yaml.set("password", record.passwordHash());
        yaml.set("registered-at", record.registeredAt());
        yaml.set("last-login-at", record.lastLoginAt());
        yaml.set("last-seen-at", record.lastSeenAt());
        yaml.set("login-count", record.loginCount());
        if (!record.autoLoginIps().isEmpty()) {
            yaml.set("auto-login-ips", new java.util.ArrayList<>(record.autoLoginIps()));
        }

        Path target = new File(baseDir, record.uuid() + ".yml").toPath();
        Path tmp = target.resolveSibling(record.uuid().toString() + ".yml.tmp");
        Files.write(tmp, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private AuthRecord read(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String uuidText = yaml.getString("uuid");
            String password = yaml.getString("password");
            if (uuidText == null || password == null) {
                return null;
            }
            UUID uuid = UUID.fromString(uuidText);
            String name = yaml.getString("name", uuid.toString());
            AuthRecord record = new AuthRecord(uuid, name, password, yaml.getLong("registered-at"));
            record.lastLoginAt(yaml.getLong("last-login-at"));
            record.lastSeenAt(yaml.getLong("last-seen-at"));
            for (String ip : yaml.getStringList("auto-login-ips")) {
                record.addAutoLoginIp(ip);
            }
            // login-count 没有独立的 setter，通过累加还原，避免为读操作污染公共 API。
            int count = yaml.getInt("login-count");
            for (int i = 0; i < count; i++) {
                record.incrementLoginCount();
            }
            return record;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String key(String name) {
        return name.toLowerCase();
    }
}
