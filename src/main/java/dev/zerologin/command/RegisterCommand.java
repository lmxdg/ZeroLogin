package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import dev.zerologin.auth.AuthService;
import dev.zerologin.locale.Messages;
import dev.zerologin.storage.AuthRecord;
import java.net.InetSocketAddress;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /register <密码> <密码> */
public final class RegisterCommand implements CommandExecutor {

    private final ZeroLoginPlugin plugin;

    public RegisterCommand(ZeroLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messagesFor(sender);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(m.get("only-player"));
            return true;
        }
        if (plugin.sessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(m.get("already-logged-in"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(m.get("usage-register"));
            return true;
        }
        if (!args[0].equals(args[1])) {
            player.sendMessage(m.get("register-mismatch"));
            return true;
        }
        String password = args[0];
        AuthService.PasswordCheck check = plugin.auth().validatePassword(password);
        switch (check) {
            case TOO_SHORT -> {
                player.sendMessage(m.get("register-too-short",
                        Map.of("min", String.valueOf(plugin.auth().minLength()))));
                return true;
            }
            case TOO_LONG -> {
                player.sendMessage(m.get("register-too-long",
                        Map.of("max", String.valueOf(plugin.auth().maxLength()))));
                return true;
            }
            case WEAK -> {
                player.sendMessage(m.get("register-weak"));
                return true;
            }
            default -> {
            }
        }

        plugin.store().loadByUuid(player.getUniqueId()).thenAccept(existing -> {
            if (existing != null) {
                player.sendMessage(m.get("already-registered"));
                return;
            }
            String hash = plugin.auth().hash(password);
            AuthRecord record = new AuthRecord(player.getUniqueId(), player.getName(), hash,
                    System.currentTimeMillis());
            InetSocketAddress addr = player.getAddress();
            String ip = addr == null ? null : addr.getAddress().getHostAddress();
            record.lastLoginAt(System.currentTimeMillis());
            record.lastSeenAt(System.currentTimeMillis());
            record.incrementLoginCount();
            if (ip != null && plugin.settings().ipAutoLogin()) {
                record.addAutoLoginIp(ip);
            }
            plugin.store().save(record).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.sessions().authenticate(player.getUniqueId(), ip);
                player.sendMessage(m.get("register-success"));
            }));
        });
        return true;
    }
}
