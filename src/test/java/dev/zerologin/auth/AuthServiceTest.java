package dev.zerologin.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private AuthService svc(boolean mixed) {
        return new AuthService(new PasswordHasher(5000, 256, 16), 6, 32, mixed);
    }

    @Test
    void validateLength() {
        AuthService s = svc(false);
        assertEquals(AuthService.PasswordCheck.TOO_SHORT, s.validatePassword("12345"));
        assertEquals(AuthService.PasswordCheck.TOO_LONG, s.validatePassword("a".repeat(33)));
        assertEquals(AuthService.PasswordCheck.OK, s.validatePassword("goodpass"));
    }

    @Test
    void validateMixed() {
        AuthService s = svc(true);
        assertEquals(AuthService.PasswordCheck.WEAK, s.validatePassword("onlyletters"));
        assertEquals(AuthService.PasswordCheck.WEAK, s.validatePassword("12345678"));
        assertEquals(AuthService.PasswordCheck.OK, s.validatePassword("abc12345"));
    }

    @Test
    void verifyRoundTrip() {
        AuthService s = svc(false);
        String stored = s.hash("hunter2");
        assertTrue(s.verify("hunter2", stored));
        assertFalse(s.verify("hunter3", stored));
    }

    @Test
    void rootCommandParsing() {
        assertEquals("login", AuthService.rootCommand("/login abc"));
        assertEquals("login", AuthService.rootCommand("login abc"));
        assertEquals("login", AuthService.rootCommand("/zerologin:login abc"));
        assertEquals("l", AuthService.rootCommand("/l x"));
        assertEquals("", AuthService.rootCommand(null));
    }
}
