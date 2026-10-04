package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import dev.zerologin.storage.AuthRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /changepassword <旧密码> <新密码> */
public final class ChangePasswordCommand implements CommandExecutor {

    private final ZeroLoginPlugin plugin;

    public ChangePasswordCommand(ZeroLoginPlugin plugin) {
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
        if (args.length != 2) {
            player.sendMessage(plugin.messages().get("usage-changepassword"));
            return true;
        }
        String oldPw = args[0];
        String newPw = args[1];

        var check = plugin.auth().validatePassword(newPw);
        if (check != dev.zerologin.auth.AuthService.PasswordCheck.OK) {
            player.sendMessage(plugin.messages().get("register-weak"));
            return true;
        }

        plugin.store().loadByUuid(player.getUniqueId()).thenAccept(record -> {
            if (record == null) {
                player.sendMessage(plugin.messages().get("not-registered"));
                return;
            }
            if (!plugin.auth().verify(oldPw, record.passwordHash())) {
                player.sendMessage(plugin.messages().get("changepassword-wrong-old"));
                return;
            }
            record.passwordHash(plugin.auth().hash(newPw));
            plugin.store().save(record).thenRun(() ->
                    player.sendMessage(plugin.messages().get("changepassword-success")));
        });
        return true;
    }
}
