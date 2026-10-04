package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import dev.zerologin.listener.AuthListener;
import dev.zerologin.storage.AuthRecord;
import java.net.InetSocketAddress;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /login <密码> */
public final class LoginCommand implements CommandExecutor {

    private final ZeroLoginPlugin plugin;
    private final AuthListener listener;

    public LoginCommand(ZeroLoginPlugin plugin) {
        this.plugin = plugin;
        this.listener = null;
    }

    public LoginCommand(ZeroLoginPlugin plugin, AuthListener listener) {
        this.plugin = plugin;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("only-player"));
            return true;
        }
        if (plugin.sessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("already-logged-in"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(plugin.messages().get("usage-login"));
            return true;
        }
        String password = args[0];
        InetSocketAddress addr = player.getAddress();
        String ip = addr == null ? null : addr.getAddress().getHostAddress();

        plugin.store().loadByUuid(player.getUniqueId()).thenAccept(record -> {
            if (record == null) {
                player.sendMessage(plugin.messages().get("not-registered"));
                return;
            }
            if (!plugin.auth().verify(password, record.passwordHash())) {
                int left = listener == null
                        ? plugin.settings().maxLoginAttempts()
                        : listener.decrementAttempts(player.getUniqueId());
                if (plugin.settings().maxLoginAttempts() > 0 && left < 0) {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> player.kickPlayer(plugin.messages().raw("kicked-too-many-attempts", null)));
                    return;
                }
                player.sendMessage(plugin.messages().get("login-wrong-password",
                        Map.of("left", String.valueOf(Math.max(0, left)))));
                return;
            }

            // 命中：升级弱散列参数 + 更新统计
            if (plugin.auth().needsUpgrade(record.passwordHash())) {
                record.passwordHash(plugin.auth().hash(password));
            }
            record.lastLoginAt(System.currentTimeMillis());
            record.lastSeenAt(System.currentTimeMillis());
            record.incrementLoginCount();
            if (ip != null && plugin.settings().ipAutoLogin() && !record.hasAutoLoginIp(ip)) {
                record.addAutoLoginIp(ip);
            }
            final AuthRecord rec = record;
            final String fip = ip;
            plugin.store().save(rec).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.sessions().authenticate(player.getUniqueId(), fip);
                player.sendMessage(plugin.messages().get("login-success"));
            }));
        });
        return true;
    }
}
