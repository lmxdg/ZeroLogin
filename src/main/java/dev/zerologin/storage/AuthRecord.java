package dev.zerologin.storage;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 一个玩家账号的持久化数据。
 *
 * <p>该类是不可变的数据载体，修改后需要通过 {@link AuthStore#save(AuthRecord)} 落盘。
 */
public final class AuthRecord {

    private final UUID uuid;
    private String name;
    private String passwordHash;
    private long registeredAt;
    private long lastLoginAt;
    private long lastSeenAt;
    private int loginCount;
    private final Set<String> autoLoginIps;

    public AuthRecord(UUID uuid, String name, String passwordHash, long registeredAt) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.registeredAt = registeredAt;
        this.autoLoginIps = new LinkedHashSet<>();
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public void passwordHash(String passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
    }

    public long registeredAt() {
        return registeredAt;
    }

    public long lastLoginAt() {
        return lastLoginAt;
    }

    public void lastLoginAt(long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    public void lastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public int loginCount() {
        return loginCount;
    }

    public void incrementLoginCount() {
        this.loginCount++;
    }

    public Set<String> autoLoginIps() {
        return Collections.unmodifiableSet(autoLoginIps);
    }

    public boolean addAutoLoginIp(String ip) {
        return autoLoginIps.add(normalizeIp(ip));
    }

    public boolean removeAutoLoginIp(String ip) {
        return autoLoginIps.remove(normalizeIp(ip));
    }

    public boolean hasAutoLoginIp(String ip) {
        return ip != null && autoLoginIps.contains(normalizeIp(ip));
    }

    public void clearAutoLoginIps() {
        autoLoginIps.clear();
    }

    /**
     * IPv6 地址的十六进制分组大小写不敏感，直接字符串比较会把 {@code FE80::1} 与
     * {@code fe80::1} 判定为两个不同地址，因此统一转小写。
     *
     * <p>不做完整的 RFC 规范化：压缩写法（{@code ::}）与前导零的等价形式在纯字符串层面
     * 无法可靠合并，交给上层按“同一字符串即同一地址”处理，避免误判导致越权自动登录。
     */
    public static String normalizeIp(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        int slash = trimmed.indexOf('/');
        if (slash >= 0) {
            trimmed = trimmed.substring(0, slash);
        }
        int percent = trimmed.indexOf('%');
        if (percent >= 0) {
            trimmed = trimmed.substring(0, percent);
        }
        return trimmed.toLowerCase();
    }

    @Override
    public String toString() {
        return "AuthRecord{" + name + "/" + uuid + ", ips=" + autoLoginIps.size() + ", logins=" + loginCount + "}";
    }
}
