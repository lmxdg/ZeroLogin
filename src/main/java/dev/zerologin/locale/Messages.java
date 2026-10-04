package dev.zerologin.locale;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 极简本地化：把 {@code messages_<lang>.yml} 当作 {@code key: value} 纯文本读取。
 *
 * <p>不依赖 Bukkit 的 YAML 类型，因此读取行为在 1.20 与 26.x 完全一致，
 * 也能在没有服务端的情况下单测。
 */
public final class Messages {

    private final Map<String, String> map;
    private final String prefix;

    private Messages(Map<String, String> map, String prefix) {
        this.map = map;
        this.prefix = prefix;
    }

    public static Messages load(JavaPlugin plugin, String language) {
        String file = "messages_" + language.toLowerCase() + ".yml";
        Map<String, String> map = readResource(plugin, file);
        if (map.isEmpty() && !file.equals("messages_zh_cn.yml")) {
            plugin.getLogger().warning("语言文件 " + file + " 缺失或为空，回退到 zh_cn");
            map = readResource(plugin, "messages_zh_cn.yml");
        }
        String prefix = colorize(map.getOrDefault("prefix", ""));
        return new Messages(map, prefix);
    }

    private static Map<String, String> readResource(JavaPlugin plugin, String name) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return map;
            }
            Properties props = new Properties();
            // 我们的 messages 文件是合法的 key: value；转成 properties 兼容格式读取
            try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                StringBuilder sb = new StringBuilder();
                int ch;
                while ((ch = reader.read()) != -1) {
                    sb.append((char) ch);
                }
                for (String line : sb.toString().split("\n")) {
                    String t = line.strip();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    int idx = t.indexOf(':');
                    if (idx <= 0) {
                        continue;
                    }
                    String key = t.substring(0, idx).trim();
                    String value = t.substring(idx + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    map.put(key, value);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("读取语言文件失败 " + name + ": " + ex.getMessage());
        }
        return map;
    }

    public String prefix() {
        return prefix;
    }

    /** 取消息并附加前缀、做颜色转换与占位符替换。 */
    public String get(String key, Map<String, String> placeholders) {
        String raw = map.getOrDefault(key, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return prefix + colorize(raw);
    }

    public String get(String key) {
        return get(key, null);
    }

    /** 不带前缀的消息（用于踢出原因等）。 */
    public String raw(String key, Map<String, String> placeholders) {
        String raw = map.getOrDefault(key, key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                raw = raw.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return colorize(raw);
    }

    /** 把 & 颜色代码转换为 §。 */
    public static String colorize(String s) {
        if (s == null) {
            return "";
        }
        char[] b = s.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }
}
