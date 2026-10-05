package dev.zerologin.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 以“每个账号一个纯文本文件”的形式把数据保存在插件目录下。
 *
 * <p>选择该布局的原因：
 * <ul>
 *   <li>单账号写入只重写一个文件，登录高峰不会产生整库重写；</li>
 *   <li>文件名为 UUID，玩家改名不会造成主键冲突或数据丢失；</li>
 *   <li>人工可读、可直接备份与同步到从服。</li>
 * </ul>
 *
 * <p>本类只依赖 JDK（通过 {@link AccountCodec} 序列化），不依赖任何 Bukkit API，
 * 因此可以在没有运行服务端的情况下进行完整的读写单元测试。
 *
 * <p>内存中维护 name→uuid 索引用于按名字查询；索引在 {@link #open()} 时从磁盘重建，
 * 因此重启后无需任何迁移操作。
 */
public final class FileAuthStore implements AuthStore {

    private static final String SUBDIR = "accounts";
    private static final String EXT = ".txt";

    private final Path baseDir;
    private final Logger logger;
    private final Map<UUID, AuthRecord> cache = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameIndex = new ConcurrentHashMap<>();

    public FileAuthStore(Path dataFolder, Logger logger) {
        this.baseDir = dataFolder.resolve(SUBDIR);
        this.logger = logger;
    }

    @Override
    public void open() throws IOException {
        Files.createDirectories(baseDir);
        try (var stream = Files.list(baseDir)) {
            int[] skipped = {0};
            stream.filter(p -> p.getFileName().toString().endsWith(EXT)).forEach(file -> {
                try {
                    AuthRecord record = read(file);
                    if (record != null) {
                        index(record);
                    } else {
                        skipped[0]++;
                    }
                } catch (RuntimeException | IOException ex) {
                    skipped[0]++;
                    logger.log(Level.WARNING, "账号文件解析失败，已跳过：{0}", file.getFileName());
                }
            });
            if (skipped[0] > 0) {
                logger.log(Level.WARNING, "有 {0} 个账号文件无法解析，请检查 {1}",
                        new Object[]{skipped[0], baseDir.toAbsolutePath()});
            }
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
        try {
            AuthRecord read = read(baseDir.resolve(uuid + EXT));
            if (read != null) {
                index(read);
            }
            return CompletableFuture.completedFuture(read);
        } catch (IOException ex) {
            return failed(ex);
        }
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
                    return save(record).thenApply(v -> record);
                }
                return CompletableFuture.completedFuture(record);
            }
        }
        // 索引未命中时做一次全量扫描，兼容他人手工放入 accounts 目录的文件。
        try (var stream = Files.list(baseDir)) {
            var files = stream.filter(p -> p.getFileName().toString().endsWith(EXT)).toList();
            for (Path file : files) {
                AuthRecord record = read(file);
                if (record == null) {
                    continue;
                }
                index(record);
                if (key(record.name()).equals(key)) {
                    return CompletableFuture.completedFuture(record);
                }
            }
            return CompletableFuture.completedFuture(null);
        } catch (IOException ex) {
            return failed(ex);
        }
    }

    @Override
    public CompletableFuture<Void> save(AuthRecord record) {
        java.util.Objects.requireNonNull(record, "record");
        index(record);
        try {
            write(record);
            return CompletableFuture.completedFuture(null);
        } catch (IOException ex) {
            return failed(ex);
        }
    }

    @Override
    public CompletableFuture<Boolean> deleteByUuid(UUID uuid) {
        AuthRecord removed = cache.remove(uuid);
        if (removed != null) {
            nameIndex.remove(key(removed.name()), uuid);
        }
        try {
            boolean existed = Files.deleteIfExists(baseDir.resolve(uuid + EXT));
            return CompletableFuture.completedFuture(existed || removed != null);
        } catch (IOException ex) {
            return failed(ex);
        }
    }

    @Override
    public CompletableFuture<Integer> countAccounts() {
        return CompletableFuture.completedFuture(cache.size());
    }

    @Override
    public CompletableFuture<List<AuthRecord>> loadAll() {
        List<AuthRecord> out = new ArrayList<>();
        try (var stream = Files.list(baseDir)) {
            var files = stream.filter(p -> p.getFileName().toString().endsWith(EXT)).toList();
            for (Path file : files) {
                AuthRecord record = read(file);
                if (record == null) {
                    continue;
                }
                index(record);
                out.add(record);
            }
            return CompletableFuture.completedFuture(out);
        } catch (IOException ex) {
            return failed(ex);
        }
    }

    /** 当前已缓存的账号数，用于测试与诊断。 */
    public int cachedCount() {
        return cache.size();
    }

    private void index(AuthRecord record) {
        cache.put(record.uuid(), record);
        nameIndex.put(key(record.name()), record.uuid());
    }

    private void write(AuthRecord record) throws IOException {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema", "1");
        fields.put("uuid", record.uuid().toString());
        fields.put("name", record.name());
        fields.put("password", record.passwordHash());
        fields.put("registered-at", Long.toString(record.registeredAt()));
        fields.put("last-login-at", Long.toString(record.lastLoginAt()));
        fields.put("last-seen-at", Long.toString(record.lastSeenAt()));
        fields.put("login-count", Integer.toString(record.loginCount()));
        if (!record.autoLoginIps().isEmpty()) {
            fields.put("auto-login-ips", String.join(",", record.autoLoginIps()));
        }

        Path target = baseDir.resolve(record.uuid() + EXT);
        Path tmp = baseDir.resolve(record.uuid() + EXT + ".tmp");
        Files.writeString(tmp, AccountCodec.serialize(fields), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private AuthRecord read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> fields = AccountCodec.deserialize(content);
        if (fields == null) {
            return null;
        }
        try {
            String uuidText = fields.get("uuid");
            String password = fields.get("password");
            if (uuidText == null || password == null || password.isEmpty()) {
                return null;
            }
            UUID uuid = UUID.fromString(uuidText);
            String name = fields.getOrDefault("name", uuid.toString());
            AuthRecord record = new AuthRecord(uuid, name, password, parseLong(fields.get("registered-at")));
            record.lastLoginAt(parseLong(fields.get("last-login-at")));
            record.lastSeenAt(parseLong(fields.get("last-seen-at")));
            int count = (int) parseLong(fields.get("login-count"));
            for (int i = 0; i < count; i++) {
                record.incrementLoginCount();
            }
            String ips = fields.get("auto-login-ips");
            if (ips != null && !ips.isEmpty()) {
                for (String ip : ips.split(",")) {
                    if (!ip.isEmpty()) {
                        record.addAutoLoginIp(ip);
                    }
                }
            }
            return record;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static <T> CompletableFuture<T> failed(Throwable ex) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(ex);
        return f;
    }
}
