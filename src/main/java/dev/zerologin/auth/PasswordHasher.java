package dev.zerologin.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * 口令散列器。
 *
 * <p>存储格式：{@code $pbkdf2$<迭代次数>$<盐(Base64)>$<散列(Base64)>}
 *
 * <p>只使用 JDK 自带实现（PBKDF2-HMAC-SHA256），因此插件零外部依赖、
 * 不会因为服务端类加载隔离或依赖冲突而失败。
 */
public final class PasswordHasher {

    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final String PREFIX = "$pbkdf2$";

    private final int iterations;
    private final int keyLengthBits;
    private final int saltLengthBytes;
    private final SecureRandom random;

    public PasswordHasher(int iterations, int keyLengthBits, int saltLengthBytes) {
        if (iterations < 1000) {
            throw new IllegalArgumentException("iterations 至少为 1000，当前为 " + iterations);
        }
        if (keyLengthBits < 128) {
            throw new IllegalArgumentException("keyLengthBits 至少为 128，当前为 " + keyLengthBits);
        }
        if (saltLengthBytes < 8) {
            throw new IllegalArgumentException("saltLengthBytes 至少为 8，当前为 " + saltLengthBytes);
        }
        this.iterations = iterations;
        this.keyLengthBits = keyLengthBits;
        this.saltLengthBytes = saltLengthBytes;
        this.random = new SecureRandom();
    }

    public int iterations() {
        return iterations;
    }

    /** 生成带盐的口令散列。 */
    public String hash(char[] password) {
        byte[] salt = new byte[saltLengthBytes];
        random.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, iterations, keyLengthBits);
        Base64.Encoder enc = Base64.getEncoder();
        return PREFIX + iterations + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(dk);
    }

    /**
     * 校验口令。格式非法时返回 false 而不是抛出异常，避免损坏的存储数据把玩家踢下线。
     *
     * <p>比较使用常量时间算法，避免通过响应时间差异推断散列内容。
     */
    public boolean matches(char[] password, String stored) {
        Parsed parsed = parse(stored);
        if (parsed == null) {
            return false;
        }
        byte[] dk = pbkdf2(password, parsed.salt, parsed.iterations, parsed.hash.length * 8);
        return MessageDigest.isEqual(dk, parsed.hash);
    }

    /**
     * 判断存储的散列是否使用了弱参数，用于在玩家成功登录后透明升级为更强的参数。
     */
    public boolean needsUpgrade(String stored) {
        Parsed parsed = parse(stored);
        return parsed != null && parsed.iterations < iterations;
    }

    /** 解析存储字符串；不是本插件生成的格式时返回 {@code null}。 */
    public static Parsed parse(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = stored.split("\\$");
        // ["", "pbkdf2", iterations, salt, hash]
        if (parts.length != 5) {
            return null;
        }
        try {
            int it = Integer.parseInt(parts[2]);
            byte[] salt = Base64.getDecoder().decode(parts[3]);
            byte[] hash = Base64.getDecoder().decode(parts[4]);
            if (it < 1000 || salt.length < 8 || hash.length < 16) {
                return null;
            }
            return new Parsed(it, salt, hash);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("服务端 JVM 不支持 " + ALGORITHM, ex);
        }
    }

    /** 已解析的散列数据。 */
    public record Parsed(int iterations, byte[] salt, byte[] hash) {
    }

    /** 用于日志输出的安全摘要，绝不包含口令。 */
    public static String describe(String stored) {
        Parsed p = parse(stored);
        if (p == null) {
            return "unknown-format";
        }
        return "pbkdf2-sha256,i=" + p.iterations + ",len=" + (p.hash.length * 8);
    }

    static {
        // 在类加载阶段就确认算法可用，问题可以第一时间暴露而不是等到玩家注册。
        try {
            SecretKeyFactory.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            throw new ExceptionInInitializerError("JVM 缺少 " + ALGORITHM + " 支持：" + ex.getMessage());
        }
    }

    /** 便于测试：把字符串转为口令数组。 */
    public static char[] toChars(String s) {
        return s.toCharArray();
    }

    /** 清除口令数组内容，减少口令在内存中的驻留时间。 */
    public static void wipe(char[] chars) {
        if (chars != null) {
            java.util.Arrays.fill(chars, '\0');
        }
    }
}
