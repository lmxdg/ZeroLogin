package dev.zerologin;

import dev.zerologin.auth.AuthService;
import dev.zerologin.auth.PasswordHasher;
import dev.zerologin.auth.SessionManager;
import dev.zerologin.command.AdminCommand;
import dev.zerologin.command.ChangePasswordCommand;
import dev.zerologin.command.IpCommand;
import dev.zerologin.command.LoginCommand;
import dev.zerologin.command.RegisterCommand;
import dev.zerologin.command.UnregisterCommand;
import dev.zerologin.config.Settings;
import dev.zerologin.locale.Messages;
import dev.zerologin.storage.AuthStore;
import dev.zerologin.storage.FileAuthStore;
import dev.zerologin.storage.SqliteAuthStore;
import dev.zerologin.storage.StorageMigrator;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ZeroLogin 主类。
 *
 * <p>只使用 1.20 与 26.x 共有的 API 交集，编译目标 Java 17，
 * 因而单个 JAR 可在 Minecraft 1.20 ~ 26.1.2 上运行。
 */
public final class ZeroLoginPlugin extends JavaPlugin {

    /** 需要随 JAR 释放到数据目录的语言文件，管理员可直接编辑其中任意一份。 */
    private static final List<String> LANG_RESOURCES = Messages.SUPPORTED_LANGUAGES.stream()
            .map(lang -> "messages_" + lang + ".yml")
            .toList();

    private Settings settings;
    private PasswordHasher hasher;
    private AuthService authService;
    private SessionManager sessionManager;
    private AuthStore store;
    private dev.zerologin.listener.AuthListener authListener;

    /** 按语言缓存的消息包；{@code language: auto} 时每种语言只解析一次。 */
    private final Map<String, Messages> messageCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String resource : LANG_RESOURCES) {
            saveResourceIfAbsent(resource);
        }

        reload();

        // 监听器（先创建，命令需要引用它做尝试次数管理）
        this.authListener = new dev.zerologin.listener.AuthListener(this);
        getServer().getPluginManager().registerEvents(authListener, this);

        // 注册命令
        registerCommand("register", new RegisterCommand(this));
        registerCommand("login", new LoginCommand(this, authListener));
        registerCommand("unregister", new UnregisterCommand(this));
        registerCommand("changepassword", new ChangePasswordCommand(this));
        registerCommand("ip", new IpCommand(this));
        registerCommand("zerologin", new AdminCommand(this));

        getLogger().info("ZeroLogin " + getDescription().getVersion() + " 已启用，存储后端=" + store.name()
                + "，语言=" + describeLanguage() + "，兼容 Minecraft 1.20 ~ 26.1.2");
    }

    private String describeLanguage() {
        return Messages.AUTO.equalsIgnoreCase(settings.language())
                ? Messages.AUTO + "（" + String.join("/", Messages.SUPPORTED_LANGUAGES) + "）"
                : messages().language();
    }

    private void saveResourceIfAbsent(String name) {
        try {
            java.io.File f = new java.io.File(getDataFolder(), name);
            if (!f.exists()) {
                saveResource(name, false);
            }
        } catch (IllegalArgumentException ex) {
            getLogger().warning("无法释放资源 " + name + ": " + ex.getMessage());
        }
    }

    /** 重新加载配置、消息与（必要时）存储。 */
    public void reload() {
        reloadConfig();
        this.settings = Settings.from(getConfig());
        this.messageCache.clear();
        this.hasher = new PasswordHasher(settings.pbkdf2Iterations(), 256, 16);
        this.authService = new AuthService(hasher, settings.minPasswordLength(),
                settings.maxPasswordLength(), settings.requireMixed());
        if (this.sessionManager == null) {
            this.sessionManager = new SessionManager();
        }
        if (this.store == null) {
            this.store = createStore();
            try {
                this.store.open();
            } catch (Exception ex) {
                getLogger().severe("账号存储打开失败：" + ex.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                throw new IllegalStateException("ZeroLogin 存储初始化失败", ex);
            }
            importLegacyFileDataIfNeeded();
        }
    }

    /**
     * 按配置选择存储后端。
     *
     * <p>未识别的 {@code storage.type} 回退到 file，保证配置写错时插件仍可启用。
     */
    private AuthStore createStore() {
        String type = settings.storageType() == null ? "sqlite" : settings.storageType().trim().toLowerCase();
        return switch (type) {
            case "sqlite", "sqlite3", "db" ->
                    new SqliteAuthStore(getDataFolder().toPath(), settings.sqliteFile(), getLogger());
            case "file", "flat", "yaml", "" -> new FileAuthStore(getDataFolder().toPath(), getLogger());
            default -> {
                getLogger().warning("未知的 storage.type=" + settings.storageType() + "，已回退到 file 后端");
                yield new FileAuthStore(getDataFolder().toPath(), getLogger());
            }
        };
    }

    /**
     * 首次使用 SQLite 且数据库为空时，自动把 v1.0 的文本账号数据导入数据库。
     *
     * <p>导入是非破坏式的：{@code accounts/} 目录原样保留，失败只告警、不影响插件启用，
     * 管理员之后仍可用 {@code /zerologin migrate} 重试。
     */
    private void importLegacyFileDataIfNeeded() {
        if (!(store instanceof SqliteAuthStore) || !settings.sqliteImportFile()) {
            return;
        }
        store.countAccounts().thenAccept(count -> {
            if (count > 0) {
                return;
            }
            FileAuthStore legacy = new FileAuthStore(getDataFolder().toPath(), getLogger());
            try {
                legacy.open();
            } catch (Exception ex) {
                getLogger().warning("无法读取旧的文本账号数据：" + ex.getMessage());
                return;
            }
            StorageMigrator.migrate(legacy, store, getDataFolder().toPath(), false, getLogger())
                    .whenComplete((result, error) -> {
                        legacy.close();
                        if (error != null) {
                            getLogger().warning("自动导入旧数据失败：" + causeMessage(error));
                        } else if (result.status() == StorageMigrator.Status.OK) {
                            getLogger().info("已把 " + result.imported() + " 个账号从文本文件导入 SQLite"
                                    + (result.skippedConflicts() > 0
                                            ? "（跳过重名账号 " + result.skippedConflicts() + " 个）" : "")
                                    + "，原 accounts/ 目录未修改。");
                        }
                    });
        }).exceptionally(error -> {
            getLogger().warning("检查账号数量失败：" + causeMessage(error));
            return null;
        });
    }

    /**
     * 创建指定类型的<b>临时</b>存储实例，供迁移命令读取另一侧的数据。
     *
     * <p>调用方负责 {@link AuthStore#open()} 与 {@code close()}。返回 {@code null} 表示类型不合法。
     */
    public AuthStore newStoreInstance(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase();
        return switch (normalized) {
            case "sqlite", "sqlite3", "db" ->
                    new SqliteAuthStore(getDataFolder().toPath(), settings.sqliteFile(), getLogger());
            case "file", "flat" -> new FileAuthStore(getDataFolder().toPath(), getLogger());
            default -> null;
        };
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("命令未在 plugin.yml 中声明：" + name);
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tab) {
            cmd.setTabCompleter(tab);
        }
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.close();
        }
        getLogger().info("ZeroLogin 已卸载。");
    }

    // ===== 供命令/监听器使用的访问器 =====

    public Settings settings() {
        return settings;
    }

    /** 服务器默认语言的消息包（控制台、日志、{@code language} 非 auto 时的所有玩家）。 */
    public Messages messages() {
        return messagesFor(null);
    }

    /**
     * 面向某个发送者的消息包。
     *
     * <p>{@code language: auto} 时按玩家客户端语言返回，控制台与其他情况返回默认语言；
     * 指定了固定语言时始终返回该语言，行为与 v1.0 一致。
     */
    public Messages messagesFor(CommandSender sender) {
        String configured = settings.language();
        if (!Messages.AUTO.equalsIgnoreCase(configured)) {
            return cached(Messages.normalize(configured));
        }
        if (sender instanceof Player player) {
            return cached(Messages.resolve(player.getLocale(), Messages.AUTO_DEFAULT));
        }
        return cached(Messages.AUTO_DEFAULT);
    }

    private Messages cached(String language) {
        return messageCache.computeIfAbsent(language, lang -> Messages.load(this, lang));
    }

    public AuthService auth() {
        return authService;
    }

    public SessionManager sessions() {
        return sessionManager;
    }

    public AuthStore store() {
        return store;
    }

    public Path dataPath() {
        return getDataFolder().toPath();
    }

    public static String causeMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
