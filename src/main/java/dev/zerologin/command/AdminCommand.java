package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /zerologin <reload|info|stats> */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private final ZeroLoginPlugin plugin;

    public AdminCommand(ZeroLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zerologin.admin")) {
            sender.sendMessage(plugin.messages().get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(plugin.messages().prefix() + "§e/zerologin <reload|info|stats>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.messages().get("reload-success"));
            }
            case "info" -> {
                sender.sendMessage(plugin.messages().prefix() + "§bZeroLogin §f" + plugin.getDescription().getVersion());
                sender.sendMessage(plugin.messages().prefix() + "§7兼容 Minecraft 1.20 ~ 26.1.2");
                sender.sendMessage(plugin.messages().prefix() + "§7存储后端: §f" + plugin.store().name());
            }
            case "stats" -> plugin.store().countAccounts().thenAccept(count ->
                    sender.sendMessage(plugin.messages().prefix() + "§7注册账号: §f" + count
                            + " §7在线已认证: §f" + plugin.sessions().online()));
            default -> sender.sendMessage(plugin.messages().prefix() + "§e/zerologin <reload|info|stats>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("reload", "info", "stats");
            List<String> out = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            return out;
        }
        return List.of();
    }
}
