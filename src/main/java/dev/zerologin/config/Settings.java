package dev.zerologin.config;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 从 config.yml 读取的强类型设置。
 *
 * <p>所有读取集中在 {@link #from(FileConfiguration)}，并使用安全的默认值，
 * 配置缺失或类型错误时不会让插件崩溃。
 */
public record Settings(
        String language,
        String storageType,
        int pbkdf2Iterations,
        int minPasswordLength,
        int maxPasswordLength,
        int maxLoginAttempts,
        int loginTimeoutSeconds,
        boolean requireMixed,
        long rememberSeconds,
        boolean ipAutoLogin,
        boolean freezeMovement,
        List<String> allowedCommands,
        String prefix) {

    public static Settings from(FileConfiguration c) {
        return new Settings(
                c.getString("language", "zh_cn"),
                c.getString("storage.type", "file"),
                Math.max(1000, c.getInt("security.pbkdf2-iterations", 120000)),
                Math.max(1, c.getInt("security.min-password-length", 6)),
                Math.max(1, c.getInt("security.max-password-length", 32)),
                c.getInt("security.max-login-attempts", 3),
                c.getInt("security.login-timeout-seconds", 60),
                c.getBoolean("security.require-mixed", false),
                Math.max(0, c.getLong("session.remember-seconds", 300)),
                c.getBoolean("session.ip-auto-login", true),
                c.getBoolean("protection.freeze-movement", true),
                c.getStringList("protection.allowed-commands"),
                c.getString("messages-prefix", "&8[&bZeroLogin&8] "));
    }

    public boolean isCommandAllowed(String rootCommandLowerCase) {
        for (String allowed : allowedCommands) {
            if (allowed.equalsIgnoreCase(rootCommandLowerCase)) {
                return true;
            }
        }
        return false;
    }
}
