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
import dev.zerologin.listener.AuthListener;
import dev.zerologin.locale.Messages;
import dev.zerologin.storage.AuthStore;
import dev.zerologin.storage.FileAuthStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ZeroLogin 主类。
 *
 * <p>只使用 1.20 与 26.x 共有的 API 交集，编译目标 Java 17，
 * 因而单个 JAR 可在 Minecraft 1.20 ~ 26.1.2 上运行。
 */
public final class ZeroLoginPlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private PasswordHasher hasher;
    private AuthService authService;
    private SessionManager sessionManager;
    private AuthStore store;
    private dev.zerologin.listener.AuthListener authListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfAbsent("messages_zh_cn.yml");
        saveResourceIfAbsent("messages_en_us.yml");

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
                + "，兼容 Minecraft 1.20 ~ 26.1.2");
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
        this.messages = Messages.load(this, settings.language());
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
        }
    }

    private AuthStore createStore() {
        // 目前仅支持 file；后续版本在此扩展 mysql 等后端。
        return new FileAuthStore(getDataFolder().toPath(), getLogger());
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

    public Messages messages() {
        return messages;
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
}
