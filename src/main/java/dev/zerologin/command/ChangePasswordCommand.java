package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import dev.zerologin.locale.Messages;
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
        Messages m = plugin.messagesFor(sender);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(m.get("only-player"));
            return true;
        }
        if (!plugin.sessions().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(m.get("not-logged-in"));
            return true;
        }
        if (args.length != 2) {
            player.sendMessage(m.get("usage-changepassword"));
            return true;
        }
        String oldPw = args[0];
        String newPw = args[1];

        var check = plugin.auth().validatePassword(newPw);
        if (check != dev.zerologin.auth.AuthService.PasswordCheck.OK) {
            player.sendMessage(m.get("register-weak"));
            return true;
        }

        plugin.store().loadByUuid(player.getUniqueId()).thenAccept(record -> {
            if (record == null) {
                player.sendMessage(m.get("not-registered"));
                return;
            }
            if (!plugin.auth().verify(oldPw, record.passwordHash())) {
                player.sendMessage(m.get("changepassword-wrong-old"));
                return;
            }
            record.passwordHash(plugin.auth().hash(newPw));
            plugin.store().save(record).thenRun(() ->
                    player.sendMessage(m.get("changepassword-success")));
        });
        return true;
    }
}
