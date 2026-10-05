package dev.zerologin.locale;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 极简本地化：把 {@code messages_<lang>.yml} 当作 {@code key: value} 纯文本读取。
 *
 * <p>不依赖 Bukkit 的 YAML 类型，因此读取行为在 1.20 与 26.x 完全一致，
 * 也能在没有服务端的情况下单测。
 *
 * <p>读取优先级：插件数据目录下的同名文件（管理员自定义） &gt; JAR 内置资源。
 * 单个键缺失时按 <b>目标语言 → en_us → zh_cn → 键名本身</b> 回退，
 * 因此自定义翻译漏几行也不会让玩家看到空白。
 */
public final class Messages {

    /** 内置语言列表（七国语言），首项为兜底语言。 */
    public static final List<String> SUPPORTED_LANGUAGES =
            List.of("en_us", "zh_cn", "zh_tw", "ja_jp", "ko_kr", "ru_ru", "es_es");

    /** {@code language: auto} 的取值。 */
    public static final String AUTO = "auto";

    /** {@code language: auto} 时服务器控制台与不可识别客户端使用的语言。 */
    public static final String AUTO_DEFAULT = "en_us";

    private static final List<String> FALLBACK_CHAIN = List.of("en_us", "zh_cn");

    private final Map<String, String> map;
    private final String prefix;
    private final String language;

    private Messages(Map<String, String> map, String prefix, String language) {
        this.map = map;
        this.prefix = prefix;
        this.language = language;
    }

    /**
     * 加载一种语言。
     *
     * @param language {@link #SUPPORTED_LANGUAGES} 中的语言标签；无法识别时按 {@code zh_cn} 处理
     */
    public static Messages load(JavaPlugin plugin, String language) {
        String lang = normalize(language);
        Map<String, String> merged = new HashMap<>();
        for (String candidate : fallbackChain(lang)) {
            mergeInto(merged, read(plugin, candidate));
        }
        String prefix = colorize(merged.getOrDefault("prefix", ""));
        return new Messages(merged, prefix, lang);
    }

    /** 目标语言 + 兜底链，靠前的优先。 */
    private static List<String> fallbackChain(String lang) {
        List<String> chain = new java.util.ArrayList<>(FALLBACK_CHAIN.size() + 1);
        chain.add(lang);
        for (String fallback : FALLBACK_CHAIN) {
            if (!chain.contains(fallback)) {
                chain.add(fallback);
            }
        }
        return chain;
    }

    /** 用后读到的语言包里<b>尚未出现</b>的键补齐，从而实现按键回退。 */
    private static void mergeInto(Map<String, String> target, Map<String, String> overlay) {
        for (Map.Entry<String, String> e : overlay.entrySet()) {
            target.putIfAbsent(e.getKey(), e.getValue());
        }
    }

    /** 把任意客户端语言标签归一到受支持的语言，未命中返回兜底语言。 */
    public static String normalize(String raw) {
        return resolve(raw, "zh_cn");
    }

    /**
     * 把客户端/配置里的语言标签解析为受支持的语言。
     *
     * <p>处理三类现实情况：
     * <ul>
     *   <li>大小写与分隔符差异：{@code zh-CN}、{@code ZH_CN} → {@code zh_cn}；</li>
     *   <li>地区变体：{@code es_419}、{@code es_mx} → {@code es_es}，中文按 {@code zh_tw}/{@code zh_hk}/{@code zh_mo}
     *       归到繁体，其余（含 {@code zh_sg}）归到简体；</li>
     *   <li>完全未知的语言 → {@code fallback}。</li>
     * </ul>
     */
    public static String resolve(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String tag = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (AUTO.equals(tag)) {
            return fallback;
        }
        for (String supported : SUPPORTED_LANGUAGES) {
            if (supported.equals(tag)) {
                return supported;
            }
        }
        String lang = tag.contains("_") ? tag.substring(0, tag.indexOf('_')) : tag;
        switch (lang) {
            case "en" -> {
                return "en_us";
            }
            case "ja" -> {
                return "ja_jp";
            }
            case "ko" -> {
                return "ko_kr";
            }
            case "es" -> {
                return "es_es";
            }
            case "ru" -> {
                return "ru_ru";
            }
            case "zh" -> {
                return isTraditional(tag) ? "zh_tw" : "zh_cn";
            }
            default -> {
                return fallback;
            }
        }
    }

    private static boolean isTraditional(String tag) {
        return tag.contains("tw") || tag.contains("hk") || tag.contains("mo")
                || tag.contains("hant");
    }

    /** 该语言是否在内置支持范围内（配置校验用）。 */
    public static boolean isSupported(String language) {
        return SUPPORTED_LANGUAGES.contains(resolve(language, ""));
    }

    private static Map<String, String> read(JavaPlugin plugin, String language) {
        String name = "messages_" + language + ".yml";
        File external = new File(plugin.getDataFolder(), name);
        if (external.isFile()) {
            Map<String, String> fromDisk = parse(readFile(external));
            if (!fromDisk.isEmpty()) {
                return fromDisk;
            }
        }
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return Map.of();
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            plugin.getLogger().warning("读取语言文件失败 " + name + ": " + ex.getMessage());
            return Map.of();
        }
    }

    private static String readFile(File file) {
        try {
            return java.nio.file.Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static Map<String, String> parse(String content) {
        Map<String, String> map = new HashMap<>();
        for (String line : content.split("\n")) {
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
        return map;
    }

    public String prefix() {
        return prefix;
    }

    /** 本实例对应的语言标签。 */
    public String language() {
        return language;
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

    /** 将文本写入数据目录，便于管理员提取模板自行翻译。 */
    public static void dumpTemplate(JavaPlugin plugin, String language, String content) {
        try {
            File dir = plugin.getDataFolder();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            java.nio.file.Files.writeString(new File(dir, "messages_" + language + ".yml").toPath(),
                    content, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // 写模板失败不影响功能，调用方只把它当作便利特性。
        }
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
