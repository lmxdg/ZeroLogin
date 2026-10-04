package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /unregister <当前密码> */
public final class UnregisterCommand implements CommandExecutor {

    private final ZeroLoginPlugin plugin;

    public UnregisterCommand(ZeroLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("only-player"));
            return true;
        }
        if (!plugin.sessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.messages().get("not-logged-in"));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(plugin.messages().get("usage-unregister"));
            return true;
        }
        plugin.store().loadByUuid(player.getUniqueId()).thenAccept(record -> {
            if (record == null) {
                player.sendMessage(plugin.messages().get("not-registered"));
                return;
            }
            if (!plugin.auth().verify(args[0], record.passwordHash())) {
                player.sendMessage(plugin.messages().get("unregister-wrong-password"));
                return;
            }
            plugin.store().deleteByUuid(player.getUniqueId()).thenRun(() ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.sessions().deauthenticate(player.getUniqueId());
                        player.sendMessage(plugin.messages().get("unregister-success"));
                    }));
        });
        return true;
    }
}
