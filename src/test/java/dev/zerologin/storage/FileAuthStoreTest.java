package dev.zerologin.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileAuthStoreTest {

    @TempDir
    Path tempDir;

    private FileAuthStore newStore() {
        return new FileAuthStore(tempDir, Logger.getLogger("test"));
    }

    @Test
    void saveAndLoadByUuid() throws Exception {
        FileAuthStore store = newStore();
        store.open();
        UUID uuid = UUID.randomUUID();
        AuthRecord rec = new AuthRecord(uuid, "Steve", "$pbkdf2$120000$c2FsdA==$aGFzaA==", 111L);
        rec.lastLoginAt(222L);
        rec.lastSeenAt(333L);
        rec.incrementLoginCount();
        rec.incrementLoginCount();
        rec.addAutoLoginIp("1.2.3.4");
        rec.addAutoLoginIp("FE80::1");
        store.save(rec).join();

        FileAuthStore reloaded = newStore();
        reloaded.open();
        AuthRecord loaded = reloaded.loadByUuid(uuid).join();
        assertNotNull(loaded);
        assertEquals("Steve", loaded.name());
        assertEquals("$pbkdf2$120000$c2FsdA==$aGFzaA==", loaded.passwordHash());
        assertEquals(111L, loaded.registeredAt());
        assertEquals(222L, loaded.lastLoginAt());
        assertEquals(333L, loaded.lastSeenAt());
        assertEquals(2, loaded.loginCount());
        assertTrue(loaded.hasAutoLoginIp("1.2.3.4"));
        assertTrue(loaded.hasAutoLoginIp("fe80::1"));
    }

    @Test
    void loadByNameIsCaseInsensitive() throws Exception {
        FileAuthStore store = newStore();
        store.open();
        UUID uuid = UUID.randomUUID();
        store.save(new AuthRecord(uuid, "Steve", "$pbkdf2$120000$c2FsdA==$aGFzaA==", 1L)).join();
        assertNotNull(store.loadByName("steve").join());
        assertNotNull(store.loadByName("STEVE").join());
        assertNull(store.loadByName("alex").join());
    }

    @Test
    void deleteRemovesRecord() throws Exception {
        FileAuthStore store = newStore();
        store.open();
        UUID uuid = UUID.randomUUID();
        store.save(new AuthRecord(uuid, "Steve", "$pbkdf2$120000$c2FsdA==$aGFzaA==", 1L)).join();
        assertTrue(store.deleteByUuid(uuid).join());

        FileAuthStore reloaded = newStore();
        reloaded.open();
        assertNull(reloaded.loadByUuid(uuid).join());
    }

    @Test
    void countAccounts() throws Exception {
        FileAuthStore store = newStore();
        store.open();
        store.save(new AuthRecord(UUID.randomUUID(), "A", "$pbkdf2$120000$c2FsdA==$aGFzaA==", 1L)).join();
        store.save(new AuthRecord(UUID.randomUUID(), "B", "$pbkdf2$120000$c2FsdA==$aGFzaA==", 1L)).join();
        assertEquals(2, store.countAccounts().join());
    }

    @Test
    void skipsCorruptFiles() throws Exception {
        FileAuthStore store = newStore();
        store.open();
        store.save(new AuthRecord(UUID.randomUUID(), "Good", "$pbkdf2$120000$c2FsdA==$aGFzaA==", 1L)).join();
        java.nio.file.Files.writeString(tempDir.resolve("accounts").resolve(UUID.randomUUID() + ".txt"), "garbage-no-separator");
        FileAuthStore reloaded = newStore();
        reloaded.open();
        assertEquals(1, reloaded.countAccounts().join());
    }

    @Test
    void ipNormalization() {
        assertEquals("fe80::1", AuthRecord.normalizeIp("FE80::1"));
        assertEquals("1.2.3.4", AuthRecord.normalizeIp("1.2.3.4"));
        assertNull(AuthRecord.normalizeIp(null));
    }
}
