package dev.zerologin.command;

import dev.zerologin.ZeroLoginPlugin;
import dev.zerologin.locale.Messages;
import dev.zerologin.storage.AuthStore;
import dev.zerologin.storage.StorageMigrator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /zerologin &lt;reload|info|stats|migrate|lang-template&gt; */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reload", "info", "stats", "migrate", "lang-template");
    private static final List<String> STORE_TYPES = List.of("file", "sqlite");

    private final ZeroLoginPlugin plugin;

    public AdminCommand(ZeroLoginPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messagesFor(sender);
        if (!sender.hasPermission("zerologin.admin")) {
            sender.sendMessage(m.get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(m.get("usage-admin"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                sender.sendMessage(plugin.messagesFor(sender).get("reload-success"));
            }
            case "info" -> {
                sender.sendMessage(m.get("info-title",
                        Map.of("version", String.valueOf(plugin.getDescription().getVersion()))));
                sender.sendMessage(m.get("info-compat"));
                sender.sendMessage(m.get("info-storage", Map.of("storage", plugin.store().name())));
                sender.sendMessage(m.get("info-language",
                        Map.of("language", plugin.messagesFor(sender).language())));
            }
            case "stats" -> plugin.store().countAccounts().thenAccept(count -> sender.sendMessage(
                    plugin.messagesFor(sender).get("info-accounts", Map.of(
                            "count", String.valueOf(count),
                            "online", String.valueOf(plugin.sessions().online())))));
            case "migrate" -> migrate(sender, m, args);
            case "lang-template" -> langTemplate(sender, m, args);
            default -> sender.sendMessage(m.get("usage-admin"));
        }
        return true;
    }

    /**
     * 在后端之间搬运账号数据。打开/写入另一侧存储可能耗时，因此整段放在异步线程，
     * 消息实例 {@code m} 在主线程解析好后再带入，避免异步读取语言文件。
     */
    private void migrate(CommandSender sender, Messages m, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(m.get("migrate-usage"));
            return;
        }
        String from = args[1];
        String to = args[2];
        boolean force = args.length >= 4 && args[3].equalsIgnoreCase("force");
        if (from.equalsIgnoreCase(to) || plugin.newStoreInstance(from) == null || plugin.newStoreInstance(to) == null) {
            sender.sendMessage(m.get("migrate-usage"));
            return;
        }
        sender.sendMessage(m.get("migrate-start", Map.of("from", from, "to", to)));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            AuthStore source = plugin.newStoreInstance(from);
            AuthStore target = plugin.newStoreInstance(to);
            try {
                source.open();
                target.open();
                StorageMigrator.Result result = StorageMigrator
                        .migrate(source, target, plugin.dataPath(), force, plugin.getLogger())
                        .join();
                report(sender, m, from, to, result);
            } catch (Exception ex) {
                sender.sendMessage(m.get("migrate-failed", Map.of("error", ZeroLoginPlugin.causeMessage(ex))));
            } finally {
                closeQuietly(source);
                closeQuietly(target);
            }
        });
    }

    private void report(CommandSender sender, Messages m, String from, String to, StorageMigrator.Result result) {
        switch (result.status()) {
            case SOURCE_EMPTY -> sender.sendMessage(m.get("migrate-source-empty"));
            case TARGET_NOT_EMPTY -> sender.sendMessage(m.get("migrate-target-not-empty",
                    Map.of("count", String.valueOf(result.targetExisting()))));
            default -> {
                sender.sendMessage(m.get("migrate-done", Map.of(
                        "count", String.valueOf(result.imported()),
                        "to", to)));
                if (result.skippedConflicts() > 0) {
                    sender.sendMessage(m.get("migrate-conflicts",
                            Map.of("count", String.valueOf(result.skippedConflicts()))));
                }
                if (result.backupDir() != null) {
                    sender.sendMessage(m.get("migrate-backup-done",
                            Map.of("file", String.valueOf(result.backupDir()))));
                }
            }
        }
    }

    private void closeQuietly(AuthStore store) {
        if (store == null) {
            return;
        }
        try {
            store.close();
        } catch (Exception ignored) {
            // 临时存储关闭失败不影响迁移结果
        }
    }

    /** 导出内置语言文件作为模板，管理员改完放回数据目录即可生效（{@code /zerologin reload}）。 */
    private void langTemplate(CommandSender sender, Messages m, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(m.get("lang-unsupported"));
            return;
        }
        String lang = Messages.resolve(args[1], "");
        if (!Messages.SUPPORTED_LANGUAGES.contains(lang)) {
            sender.sendMessage(m.get("lang-unsupported"));
            return;
        }
        String content = Messages.builtinContent(plugin, lang);
        if (content == null) {
            sender.sendMessage(m.get("lang-unsupported"));
            return;
        }
        Messages.dumpTemplate(plugin, lang, content);
        sender.sendMessage(m.get("lang-template-done", Map.of("file", Messages.templatePath(plugin, lang))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefixMatch(SUBS, args[0]);
        }
        String sub = args[0].toLowerCase();
        if ("migrate".equals(sub)) {
            if (args.length == 2 || args.length == 3) {
                return prefixMatch(STORE_TYPES, args[args.length - 1]);
            }
            if (args.length == 4) {
                return prefixMatch(List.of("force"), args[3]);
            }
        }
        if ("lang-template".equals(sub) && args.length == 2) {
            return prefixMatch(Messages.SUPPORTED_LANGUAGES, args[1]);
        }
        return List.of();
    }

    private static List<String> prefixMatch(List<String> options, String partial) {
        String p = partial.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(p)) {
                out.add(option);
            }
        }
        return out;
    }
}
