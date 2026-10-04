package dev.zerologin.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountCodecTest {

    @Test
    void roundTrip() {
        Map<String, String> in = new LinkedHashMapBuilder()
                .put("uuid", "123e4567-e89b-12d3-a456-426614174000")
                .put("name", "Steve")
                .put("password", "$pbkdf2$120000$c2FsdA==$aGFzaA==")
                .build();
        String text = AccountCodec.serialize(in);
        Map<String, String> out = AccountCodec.deserialize(text);
        assertNotNull(out);
        assertEquals(in.get("uuid"), out.get("uuid"));
        assertEquals(in.get("name"), out.get("name"));
        assertEquals(in.get("password"), out.get("password"));
    }

    @Test
    void escapesNewlinesAndBackslashes() {
        String original = "line1\nline2\\tab\tcr\r";
        String esc = AccountCodec.escape(original);
        assertEquals(original, AccountCodec.unescape(esc));
    }

    @Test
    void rejectsTruncatedEscape() {
        assertNull(AccountCodec.unescape("abc\\"));
        assertNull(AccountCodec.unescape("abc\\q"));
    }

    @Test
    void skipsCommentsAndBlankLines() {
        Map<String, String> out = AccountCodec.deserialize("# comment\n\nkey value\n");
        assertNotNull(out);
        assertEquals("value", out.get("key"));
    }

    @Test
    void returnsNullOnGarbage() {
        assertNull(AccountCodec.deserialize(""));
        assertNull(AccountCodec.deserialize("noseparator"));
    }

    private static final class LinkedHashMapBuilder {
        private final Map<String, String> map = new java.util.LinkedHashMap<>();

        LinkedHashMapBuilder put(String k, String v) {
            map.put(k, v);
            return this;
        }

        Map<String, String> build() {
            return map;
        }
    }
}
