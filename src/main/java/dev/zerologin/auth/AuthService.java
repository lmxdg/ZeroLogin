package dev.zerologin.auth;

import java.util.Locale;

/**
 * 纯的认证决策逻辑，与 Bukkit 完全解耦，便于单元测试。
 */
public final class AuthService {

    private final PasswordHasher hasher;
    private final int minLength;
    private final int maxLength;
    private final boolean requireMixed;

    public AuthService(PasswordHasher hasher, int minLength, int maxLength, boolean requireMixed) {
        this.hasher = hasher;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.requireMixed = requireMixed;
    }

    public enum PasswordCheck {
        OK,
        TOO_SHORT,
        TOO_LONG,
        WEAK
    }

    public PasswordCheck validatePassword(String password) {
        if (password == null) {
            return PasswordCheck.TOO_SHORT;
        }
        int len = password.length();
        if (len < minLength) {
            return PasswordCheck.TOO_SHORT;
        }
        if (len > maxLength) {
            return PasswordCheck.TOO_LONG;
        }
        if (requireMixed) {
            boolean hasLetter = false;
            boolean hasDigit = false;
            for (int i = 0; i < len; i++) {
                char c = password.charAt(i);
                if (Character.isLetter(c)) {
                    hasLetter = true;
                } else if (Character.isDigit(c)) {
                    hasDigit = true;
                }
            }
            if (!hasLetter || !hasDigit) {
                return PasswordCheck.WEAK;
            }
        }
        return PasswordCheck.OK;
    }

    public String hash(String password) {
        char[] chars = password.toCharArray();
        try {
            return hasher.hash(chars);
        } finally {
            PasswordHasher.wipe(chars);
        }
    }

    public boolean verify(String password, String stored) {
        if (password == null || stored == null) {
            return false;
        }
        char[] chars = password.toCharArray();
        try {
            return hasher.matches(chars, stored);
        } finally {
            PasswordHasher.wipe(chars);
        }
    }

    public boolean needsUpgrade(String stored) {
        return hasher.needsUpgrade(stored);
    }

    public PasswordHasher hasher() {
        return hasher;
    }

    public int minLength() {
        return minLength;
    }

    public int maxLength() {
        return maxLength;
    }

    /** 把命令根名（去掉斜杠、取第一个 token）规范化为小写。 */
    public static String rootCommand(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int space = s.indexOf(' ');
        String root = space < 0 ? s : s.substring(0, space);
        // 兼容 /pluginname:command 形式
        int colon = root.indexOf(':');
        if (colon >= 0) {
            root = root.substring(colon + 1);
        }
        return root.toLowerCase(Locale.ROOT);
    }
}
