package dev.zerologin.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * 存储后端之间的数据迁移（file ↔ sqlite）。
 *
 * <p>安全约束：
 * <ul>
 *   <li><b>非破坏式</b>：迁移从不修改或删除源后端的数据；</li>
 *   <li>目标后端已有账号时必须显式 {@code force}，否则中止，避免误覆盖；</li>
 *   <li>同名（大小写不敏感）但 UUID 不同的账号一律跳过——无论保留哪一方都会丢数据；</li>
 *   <li>{@code force} 覆盖前先向 {@code backups/} 目录导出目标后端的现有账号作为文本备份。</li>
 * </ul>
 *
 * <p>迁移只通过 {@link AuthStore} 公开接口读写，因此任何后端（包括未来新增的）
 * 都不需要为迁移写专门的代码。
 */
public final class StorageMigrator {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private StorageMigrator() {
    }

    /** 迁移结果。{@link Status#TARGET_NOT_EMPTY} 时 {@code targetExisting} 说明目标已有多少账号。 */
    public record Result(Status status, int imported, int skippedConflicts, int targetExisting, Path backupDir) {

        public static Result of(Status status) {
            return new Result(status, 0, 0, 0, null);
        }
    }

    public enum Status {
        /** 迁移完成（或部分账号因重名被跳过）。 */
        OK,
        /** 源后端没有账号。 */
        SOURCE_EMPTY,
        /** 目标后端已有账号且未指定 force，已中止。 */
        TARGET_NOT_EMPTY
    }

    /**
     * 把 {@code source} 的全部账号导入 {@code target}。
     *
     * @param force 目标非空时是否继续（覆盖同 UUID 的记录，先做文本备份）
     */
    public static CompletableFuture<Result> migrate(AuthStore source, AuthStore target,
                                                    Path dataFolder, boolean force, Logger logger) {
        return source.loadAll().thenCompose(records -> target.loadAll().thenCompose(existing -> {
            if (records.isEmpty()) {
                return CompletableFuture.completedFuture(Result.of(Status.SOURCE_EMPTY));
            }
            if (!existing.isEmpty() && !force) {
                return CompletableFuture.completedFuture(
                        new Result(Status.TARGET_NOT_EMPTY, 0, 0, existing.size(), null));
            }
            Path backup;
            if (!existing.isEmpty()) {
                try {
                    backup = backupToText(dataFolder, existing);
                } catch (IOException ex) {
                    logger.warning("迁移前备份失败：" + ex.getMessage());
                    return failed(ex);
                }
            } else {
                backup = null;
            }
            Map<String, UUID> targetNames = new HashMap<>();
            for (AuthRecord record : existing) {
                targetNames.put(key(record.name()), record.uuid());
            }
            List<AuthRecord> toWrite = new ArrayList<>();
            int conflicts = 0;
            for (AuthRecord record : records) {
                UUID owner = targetNames.get(key(record.name()));
                if (owner != null && !owner.equals(record.uuid())) {
                    conflicts++;
                    continue;
                }
                toWrite.add(record);
            }
            final Path backupDir = backup;
            final int skipped = conflicts;
            return writeAll(target, toWrite)
                    .thenApply(v -> new Result(Status.OK, toWrite.size(), skipped, existing.size(), backupDir));
        }));
    }

    /** 把账号集合写成与 file 后端同格式的文本文件，人工可读、可回滚。 */
    public static Path backupToText(Path dataFolder, List<AuthRecord> records) throws IOException {
        Path dir = dataFolder.toAbsolutePath()
                .resolve("backups").resolve("migrate-" + STAMP.format(LocalDateTime.now()));
        Files.createDirectories(dir);
        for (AuthRecord record : records) {
            String content = AccountCodec.serialize(AccountCodec.toFields(record));
            Files.writeString(dir.resolve(record.uuid() + ".txt"), content, StandardCharsets.UTF_8);
        }
        return dir;
    }

    private static CompletableFuture<Void> writeAll(AuthStore target, List<AuthRecord> records) {
        if (target instanceof SqliteAuthStore sqlite) {
            return sqlite.saveAll(records);
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>(records.size());
        for (AuthRecord record : records) {
            futures.add(target.save(record));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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
