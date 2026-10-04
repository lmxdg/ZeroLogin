package dev.zerologin.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void hashAndVerify() {
        PasswordHasher h = new PasswordHasher(10000, 256, 16);
        String stored = h.hash("S3cret!".toCharArray());
        assertTrue(h.matches("S3cret!".toCharArray(), stored));
        assertFalse(h.matches("wrong".toCharArray(), stored));
    }

    @Test
    void differentSaltsProduceDifferentHashes() {
        PasswordHasher h = new PasswordHasher(10000, 256, 16);
        String a = h.hash("same".toCharArray());
        String b = h.hash("same".toCharArray());
        assertNotEquals(a, b);
        assertTrue(h.matches("same".toCharArray(), a));
        assertTrue(h.matches("same".toCharArray(), b));
    }

    @Test
    void rejectsMalformedStored() {
        PasswordHasher h = new PasswordHasher(10000, 256, 16);
        assertFalse(h.matches("x".toCharArray(), null));
        assertFalse(h.matches("x".toCharArray(), ""));
        assertFalse(h.matches("x".toCharArray(), "garbage"));
        assertFalse(h.matches("x".toCharArray(), "$pbkdf2$abc$xx$yy"));
    }

    @Test
    void detectsNeedsUpgrade() {
        PasswordHasher weak = new PasswordHasher(5000, 256, 16);
        PasswordHasher strong = new PasswordHasher(120000, 256, 16);
        String oldHash = weak.hash("pw12345".toCharArray());
        assertTrue(strong.needsUpgrade(oldHash));
        String newHash = strong.hash("pw12345".toCharArray());
        assertFalse(strong.needsUpgrade(newHash));
    }

    @Test
    void enforcesMinimumParams() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(100, 256, 16));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(10000, 64, 16));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(10000, 256, 4));
    }

    @Test
    void wipeClearsArray() {
        char[] chars = "secret".toCharArray();
        PasswordHasher.wipe(chars);
        for (char c : chars) {
            assertEquals('\0', c);
        }
    }
}
