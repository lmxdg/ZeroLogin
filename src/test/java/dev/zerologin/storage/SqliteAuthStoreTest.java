package dev.zerologin.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAuthStoreTest {

    private static final String HASH = "$pbkdf2$120000$c2FsdA==$aGFzaA==";

    @TempDir
    Path tempDir;

    private SqliteAuthStore newStore() {
        return new SqliteAuthStore(tempDir, "zerologin.db", Logger.getLogger("test"));
    }

    @Test
    void createsDatabaseFileAndRoundTripsRecord() throws Exception {
        SqliteAuthStore store = newStore();
        store.open();
        try {
            UUID uuid = UUID.randomUUID();
            AuthRecord rec = new AuthRecord(uuid, "Steve", HASH, 111L);
            rec.lastLoginAt(222L);
            rec.lastSeenAt(333L);
            rec.incrementLoginCount();
            rec.incrementLoginCount();
            rec.addAutoLoginIp("1.2.3.4");
            rec.addAutoLoginIp("FE80::1");
            store.save(rec).join();

            assertTrue(Files.isRegularFile(store.databaseFile()), "应创建数据库文件");

            AuthRecord loaded = store.loadByUuid(uuid).join();
            assertNotNull(loaded);
            assertEquals("Steve", loaded.name());
            assertEquals(HASH, loaded.passwordHash());
            assertEquals(111L, loaded.registeredAt());
            assertEquals(222L, loaded.lastLoginAt());
            assertEquals(333L, loaded.lastSeenAt());
            assertEquals(2, loaded.loginCount());
            assertTrue(loaded.hasAutoLoginIp("1.2.3.4"));
            assertTrue(loaded.hasAutoLoginIp("fe80::1"));
        } finally {
            store.close();
        }
    }

    @Test
    void dataSurvivesReopen() throws Exception {
        UUID uuid = UUID.randomUUID();
        SqliteAuthStore store = newStore();
        store.open();
        store.save(new AuthRecord(uuid, "Steve", HASH, 1L)).join();
        store.close();

        SqliteAuthStore reopened = newStore();
        reopened.open();
        try {
            assertNotNull(reopened.loadByUuid(uuid).join());
            assertEquals(1, reopened.countAccounts().join());
        } finally {
            reopened.close();
        }
    }

    @Test
    void saveUpsertsSameUuid() throws Exception {
        SqliteAuthStore store = newStore();
        store.open();
        try {
            UUID uuid = UUID.randomUUID();
            AuthRecord rec = new AuthRecord(uuid, "Steve", HASH, 1L);
            store.save(rec).join();
            rec.passwordHash(HASH.replace("aGFzaA", "bmV3"));
            rec.incrementLoginCount();
            store.save(rec).join();

            assertEquals(1, store.countAccounts().join(), "同一 UUID 应原地更新而非新增");
            AuthRecord loaded = store.loadByUuid(uuid).join();
            assertNotNull(loaded);
            assertEquals(HASH.replace("aGFzaA", "bmV3"), loaded.passwordHash());
            assertEquals(1, loaded.loginCount());
        } finally {
            store.close();
        }
    }

    @Test
    void loadByNameIsCaseInsensitiveAndRewritesRenamedAccount() throws Exception {
        SqliteAuthStore store = newStore();
        store.open();
        try {
            UUID uuid = UUID.randomUUID();
            store.save(new AuthRecord(uuid, "Steve", HASH, 1L)).join();

            assertNotNull(store.loadByName("steve").join());
            assertNotNull(store.loadByName("STEVE").join());
            assertNull(store.loadByName("alex").join());

            // 模拟玩家改名：同 UUID 换名字保存后，按新名字可查、旧名字消失
            AuthRecord renamed = store.loadByUuid(uuid).join();
            assertNotNull(renamed);
            renamed.name("Notch");
            store.save(renamed).join();

            assertEquals("Notch", store.loadByUuid(uuid).join().name());
            assertNotNull(store.loadByName("Notch").join());
            assertNull(store.loadByName("Steve").join(), "改名后旧名字不应再命中");
        } finally {
            store.close();
        }
    }

    @Test
    void deleteAndCountAndLoadAll() throws Exception {
        SqliteAuthStore store = newStore();
        store.open();
        try {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            store.save(new AuthRecord(a, "A", HASH, 1L)).join();
            store.save(new AuthRecord(b, "B", HASH, 1L)).join();
            assertEquals(2, store.countAccounts().join());

            List<AuthRecord> all = store.loadAll().join();
            assertEquals(2, all.size());

            assertTrue(store.deleteByUuid(a).join());
            assertFalse(store.deleteByUuid(a).join(), "重复删除应返回 false");
            assertEquals(1, store.countAccounts().join());
            assertNull(store.loadByUuid(a).join());
        } finally {
            store.close();
        }
    }

    @Test
    void saveAllWritesInSingleTransaction() throws Exception {
        SqliteAuthStore store = newStore();
        store.open();
        try {
            List<AuthRecord> records = List.of(
                    new AuthRecord(UUID.randomUUID(), "A", HASH, 1L),
                    new AuthRecord(UUID.randomUUID(), "B", HASH, 2L),
                    new AuthRecord(UUID.randomUUID(), "C", HASH, 3L));
            store.saveAll(records).join();
            assertEquals(3, store.countAccounts().join());
        } finally {
            store.close();
        }
    }

    @Test
    void storeNameIsSqlite() throws Exception {
        SqliteAuthStore store = newStore();
        store.open();
        try {
            assertEquals("sqlite", store.name());
        } finally {
            store.close();
        }
    }
}
