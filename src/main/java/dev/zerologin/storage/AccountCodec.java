package dev.zerologin.storage;

import java.util.LinkedHashMap;
import java.util.Map;

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
