package dev.zerologin.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageMigratorTest {

    private static final String HASH = "$pbkdf2$120000$c2FsdA==$aGFzaA==";
    private static final Logger LOGGER = Logger.getLogger("test");

    @TempDir
    Path tempDir;

    private FileAuthStore file() {
        return new FileAuthStore(tempDir, LOGGER);
    }

    private SqliteAuthStore sqlite() {
        return new SqliteAuthStore(tempDir, "zerologin.db", LOGGER);
    }

    private static AuthRecord record(String name, long registeredAt) {
        return new AuthRecord(UUID.randomUUID(), name, HASH, registeredAt);
    }

    @Test
    void fileToSqliteImportsAllAccounts() throws Exception {
        FileAuthStore source = file();
        source.open();
        SqliteAuthStore target = sqlite();
        target.open();
        try {
            source.save(record("Steve", 1L)).join();
            source.save(record("Alex", 2L)).join();

            StorageMigrator.Result result = StorageMigrator.migrate(source, target, tempDir, false, LOGGER).join();

            assertEquals(StorageMigrator.Status.OK, result.status());
            assertEquals(2, result.imported());
            assertEquals(0, result.skippedConflicts());
            assertNull(result.backupDir(), "目标为空时不需要备份");
            assertEquals(2, target.countAccounts().join());
            assertEquals(2, source.countAccounts().join(), "源数据必须保持不动");
            assertNotNull(target.loadByName("steve").join());
        } finally {
            source.close();
            target.close();
        }
    }

    @Test
    void sqliteToFileImportsAllAccounts() throws Exception {
        SqliteAuthStore source = sqlite();
        source.open();
        FileAuthStore target = file();
        target.open();
        try {
            source.save(record("Steve", 1L)).join();
            source.save(record("Alex", 2L)).join();

            StorageMigrator.Result result = StorageMigrator.migrate(source, target, tempDir, false, LOGGER).join();

            assertEquals(StorageMigrator.Status.OK, result.status());
            assertEquals(2, result.imported());
            assertEquals(2, target.countAccounts().join());
            assertEquals(2, source.countAccounts().join());
        } finally {
            source.close();
            target.close();
        }
    }

    @Test
    void emptySourceIsReportedAndChangesNothing() throws Exception {
        FileAuthStore source = file();
        source.open();
        SqliteAuthStore target = sqlite();
        target.open();
        try {
            StorageMigrator.Result result = StorageMigrator.migrate(source, target, tempDir, false, LOGGER).join();

            assertEquals(StorageMigrator.Status.SOURCE_EMPTY, result.status());
            assertEquals(0, result.imported());
            assertEquals(0, target.countAccounts().join());
        } finally {
            source.close();
            target.close();
        }
    }

    @Test
    void nonEmptyTargetAbortsWithoutForce() throws Exception {
        FileAuthStore source = file();
        source.open();
        SqliteAuthStore target = sqlite();
        target.open();
        try {
            source.save(record("Steve", 1L)).join();
            target.save(record("Alex", 2L)).join();

            StorageMigrator.Result result = StorageMigrator.migrate(source, target, tempDir, false, LOGGER).join();

            assertEquals(StorageMigrator.Status.TARGET_NOT_EMPTY, result.status());
            assertEquals(1, result.targetExisting());
            assertEquals(1, target.countAccounts().join(), "未 force 时目标不应被写入");
            assertNull(target.loadByName("steve").join());
        } finally {
            source.close();
            target.close();
        }
    }

    @Test
    void forceMergesAndBacksUpTarget() throws Exception {
        FileAuthStore source = file();
        source.open();
        SqliteAuthStore target = sqlite();
        target.open();
        try {
            source.save(record("Steve", 1L)).join();
            target.save(record("Alex", 2L)).join();
            target.save(record("Herobrine", 3L)).join();

            StorageMigrator.Result result = StorageMigrator.migrate(source, target, tempDir, true, LOGGER).join();

            assertEquals(StorageMigrator.Status.OK, result.status());
            assertEquals(1, result.imported());
            assertEquals(3, target.countAccounts().join(), "force 合并而非清空");
            assertNotNull(result.backupDir());
            assertTrue(Files.isDirectory(result.backupDir()));
            try (var files = Files.list(result.backupDir())) {
                assertEquals(2, files.count(), "备份应包含目标原有的 2 个账号");
            }
        } finally {
            source.close();
            target.close();
        }
    }

    @Test
    void duplicateNamesWithDifferentUuidsAreSkipped() throws Exception {
        FileAuthStore source = file();
        source.open();
        SqliteAuthStore target = sqlite();
        target.open();
        try {
            AuthRecord incoming = record("Steve", 1L);
            source.save(incoming).join();
            // 目标已有同名（大小写不同）但 UUID 不同的账号
            target.save(record("STEVE", 2L)).join();

            StorageMigrator.Result result = StorageMigrator.migrate(source, target, tempDir, true, LOGGER).join();

            assertEquals(StorageMigrator.Status.OK, result.status());
            assertEquals(1, result.skippedConflicts());
            assertEquals(0, result.imported());
            assertEquals(1, target.countAccounts().join());
            assertNull(target.loadByUuid(incoming.uuid()).join(), "重名冲突的账号不应被写入");
        } finally {
            source.close();
            target.close();
        }
    }

    @Test
    void backupToTextWritesReadableRecords() throws Exception {
        AuthRecord rec = record("Steve", 1L);
        Path dir = StorageMigrator.backupToText(tempDir, java.util.List.of(rec));

        Path file = dir.resolve(rec.uuid() + ".txt");
        assertTrue(Files.isRegularFile(file));
        String content = Files.readString(file);
        assertTrue(content.contains("Steve"));
        assertTrue(content.contains(rec.uuid().toString()));
    }
}
