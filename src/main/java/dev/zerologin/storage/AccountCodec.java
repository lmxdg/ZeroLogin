package dev.zerologin.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 账号文件的最小序列化器。
 *
 * <p>格式刻意保持为“一行一个 {@code key value}”的纯文本，而不是完整 YAML：
 * <ul>
 *   <li>不依赖任何 Bukkit/SnakeYAML 类型，因此不受 1.20 与 26.x 之间配置 API 变动影响；</li>
 *   <li>无需 YAML 解析器即可稳定读写，代码路径短、可完整单测；</li>
 *   <li>仍可被人工阅读和编辑。</li>
 * </ul>
 *
 * <p>值中的换行与空白用反斜杠转义，保证单行往返无损。
 */
final class AccountCodec {

    private static final String KEY_VALUE_SEPARATOR = " ";

    private AccountCodec() {
    }

    /**
     * 把账号记录展开成有序的文本字段。
     *
     * <p>字段映射放在这里而不是 {@link FileAuthStore} 内部，是为了让后端迁移与备份
     * （{@code StorageMigrator}）复用同一套序列化规则，避免两处实现漂移。
     */
    static Map<String, String> toFields(AuthRecord record) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema", "1");
        fields.put("uuid", record.uuid().toString());
        fields.put("name", record.name());
        fields.put("password", record.passwordHash());
        fields.put("registered-at", Long.toString(record.registeredAt()));
        fields.put("last-login-at", Long.toString(record.lastLoginAt()));
        fields.put("last-seen-at", Long.toString(record.lastSeenAt()));
        fields.put("login-count", Integer.toString(record.loginCount()));
        if (!record.autoLoginIps().isEmpty()) {
            fields.put("auto-login-ips", String.join(",", record.autoLoginIps()));
        }
        return fields;
    }

    /** 字段还原为账号记录；数据缺失或损坏时返回 {@code null}。 */
    static AuthRecord fromFields(Map<String, String> fields) {
        if (fields == null) {
            return null;
        }
        try {
            String uuidText = fields.get("uuid");
            String password = fields.get("password");
            if (uuidText == null || password == null || password.isEmpty()) {
                return null;
            }
            UUID uuid = UUID.fromString(uuidText);
            String name = fields.getOrDefault("name", uuid.toString());
            AuthRecord record = new AuthRecord(uuid, name, password, parseLong(fields.get("registered-at")));
            record.lastLoginAt(parseLong(fields.get("last-login-at")));
            record.lastSeenAt(parseLong(fields.get("last-seen-at")));
            int count = (int) parseLong(fields.get("login-count"));
            for (int i = 0; i < count; i++) {
                record.incrementLoginCount();
            }
            String ips = fields.get("auto-login-ips");
            if (ips != null && !ips.isEmpty()) {
                for (String ip : ips.split(",")) {
                    if (!ip.isEmpty()) {
                        record.addAutoLoginIp(ip);
                    }
                }
            }
            return record;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static long parseLong(String s) {
        if (s == null || s.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    static String serialize(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("# ZeroLogin account record. Edit at your own risk.\n");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            sb.append(entry.getKey()).append(KEY_VALUE_SEPARATOR).append(escape(entry.getValue())).append('\n');
        }
        return sb.toString();
    }

    /** 解析失败时返回 {@code null}，由调用方决定如何报告。 */
    static Map<String, String> deserialize(String content) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String rawLine : content.split("\n")) {
            String line = rawLine.replace("\r", "");
            if (line.isEmpty()) {
                continue;
            }
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) {
                continue;
            }
            int sep = line.indexOf(KEY_VALUE_SEPARATOR);
            if (sep <= 0) {
                // 没有分隔符说明文件被写坏或来自不兼容版本，交由调用方跳过而不是抛异常。
                return null;
            }
            String key = line.substring(0, sep);
            String value = unescape(line.substring(sep + 1));
            if (value == null) {
                return null;
            }
            fields.put(key, value);
        }
        return fields.isEmpty() ? null : fields;
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 遇到非法转义序列时返回 {@code null} 表示数据损坏。 */
    static String unescape(String value) {
        if (value.isEmpty()) {
            return "";
        }
        if (value.indexOf('\\') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (++i >= value.length()) {
                return null;
            }
            char next = value.charAt(i);
            switch (next) {
                case '\\' -> sb.append('\\');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> {
                    return null;
                }
            }
        }
        return sb.toString();
    }
}
