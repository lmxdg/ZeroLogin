package dev.zerologin.auth;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存中的会话状态。
 *
 * <p>跟踪每个在线玩家是否已认证、以及基于 IP 的短期“记住登录”窗口。
 * 会话不持久化，重启后所有玩家需重新认证（除非命中 IP 自动登录）。
 */
public final class SessionManager {

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();

    /** ip -> 最近认证时间（毫秒），用于 remember 窗口。 */
    private final Map<String, Long> recentByIp = new ConcurrentHashMap<>();

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.contains(uuid);
    }

    public void authenticate(UUID uuid, String ip) {
        authenticated.add(uuid);
        if (ip != null) {
            recentByIp.put(normalize(ip), System.currentTimeMillis());
        }
    }

    public void deauthenticate(UUID uuid) {
        authenticated.remove(uuid);
    }

    public void clear(UUID uuid) {
        authenticated.remove(uuid);
    }

    /** 判断某 IP 是否仍在 remember 窗口内。 */
    public boolean withinRememberWindow(String ip, long windowMillis) {
        if (ip == null || windowMillis <= 0) {
            return false;
        }
        Long ts = recentByIp.get(normalize(ip));
        return ts != null && (System.currentTimeMillis() - ts) <= windowMillis;
    }

    public void touchIp(String ip) {
        if (ip != null) {
            recentByIp.put(normalize(ip), System.currentTimeMillis());
        }
    }

    public int online() {
        return authenticated.size();
    }

    private static String normalize(String ip) {
        return ip.toLowerCase();
    }
}
