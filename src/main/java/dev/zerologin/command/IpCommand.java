package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import dev.zerologin.locale.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /ip <add|remove|list|clear> [ip] */
public final class IpCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_IPS = 5;
    private final ZeroLoginPlugin plugin;

    public IpCommand(ZeroLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messagesFor(sender);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(m.get("only-player"));
            return true;
        }
        if (!player.hasPermission("zerologin.ip")) {
            player.sendMessage(m.get("no-permission"));
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(m.get("usage-ip"));
            return true;
        }
        plugin.store().loadByUuid(player.getUniqueId()).thenAccept(record -> {
            if (record == null) {
                player.sendMessage(m.get("not-registered"));
                return;
            }
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "add" -> {
                    if (args.length < 2) {
                        player.sendMessage(m.get("usage-ip"));
                        return;
                    }
                    if (record.autoLoginIps().size() >= MAX_IPS) {
                        player.sendMessage(m.get("ip-limit"));
                        return;
                    }
                    String ip = record.normalizeIp(args[1]);
                    if (record.addAutoLoginIp(ip)) {
                        plugin.store().save(record).thenRun(() ->
                                player.sendMessage(m.get("ip-added", Map.of("ip", ip))));
                    } else {
                        player.sendMessage(m.get("ip-added", Map.of("ip", ip)));
                    }
                }
                case "remove" -> {
                    if (args.length < 2) {
                        player.sendMessage(m.get("usage-ip"));
                        return;
                    }
                    String ip = record.normalizeIp(args[1]);
                    if (record.removeAutoLoginIp(ip)) {
                        plugin.store().save(record).thenRun(() ->
                                player.sendMessage(m.get("ip-removed", Map.of("ip", ip))));
                    } else {
                        player.sendMessage(m.get("ip-removed", Map.of("ip", ip)));
                    }
                }
                case "list" -> player.sendMessage(m.get("ip-list",
                        Map.of("ips", record.autoLoginIps().isEmpty() ? "-" : String.join(", ", record.autoLoginIps()))));
                case "clear" -> {
                    record.clearAutoLoginIps();
                    plugin.store().save(record).thenRun(() ->
                            player.sendMessage(m.get("ip-cleared")));
                }
                default -> player.sendMessage(m.get("usage-ip"));
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = List.of("add", "remove", "list", "clear");
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
