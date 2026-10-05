package dev.zerologin.listener;

import dev.zerologin.ZeroLoginPlugin;
import dev.zerologin.auth.AuthService;
import dev.zerologin.storage.AuthRecord;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 认证拦截器：未认证玩家的各类行为被拦截，已认证玩家放行。
 *
 * <p>仅使用 1.20 与 26.x 共有的事件与方法。
 */
public final class AuthListener implements Listener {

    private final ZeroLoginPlugin plugin;

    /** 玩家剩余尝试次数。 */
    private final Map<UUID, Integer> attemptsLeft = new HashMap<>();

    public AuthListener(ZeroLoginPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean authed(Player p) {
        return plugin.sessions().isAuthenticated(p.getUniqueId());
    }

    private String ip(Player p) {
        InetSocketAddress addr = p.getAddress();
        return addr == null ? null : addr.getAddress().getHostAddress();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String ip = ip(player);

        attemptsLeft.put(uuid, Math.max(1, plugin.settings().maxLoginAttempts()));

        // 免登陆权限
        if (player.hasPermission("zerologin.bypass")) {
            plugin.sessions().authenticate(uuid, ip);
            return;
        }

        // IP 自动登录 / 会话恢复
        plugin.store().loadByUuid(uuid).thenAccept(record -> {
            boolean auto = false;
            if (record != null && ip != null) {
                if (plugin.settings().ipAutoLogin() && record.hasAutoLoginIp(ip)) {
                    auto = true;
                } else if (plugin.sessions().withinRememberWindow(ip, plugin.settings().rememberSeconds() * 1000L)) {
                    auto = true;
                }
            }
            if (auto) {
                final AuthRecord rec = record;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.sessions().authenticate(uuid, ip);
                    if (rec != null) {
                        rec.lastLoginAt(System.currentTimeMillis());
                        rec.lastSeenAt(System.currentTimeMillis());
                        rec.incrementLoginCount();
                        plugin.store().save(rec);
                    }
                    player.sendMessage(plugin.messagesFor(player).get("auto-login"));
                });
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (record == null) {
                        player.sendMessage(plugin.messagesFor(player).get("must-register"));
                    } else {
                        player.sendMessage(plugin.messagesFor(player).get("must-login"));
                    }
                    startTimeout(player);
                });
            }
        });
    }

    private void startTimeout(Player player) {
        int seconds = plugin.settings().loginTimeoutSeconds();
        if (seconds <= 0) {
            return;
        }
        UUID uuid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online != null && online.isOnline() && !authed(online)) {
                online.kickPlayer(plugin.messagesFor(online).raw("kicked-timeout", null));
            }
        }, seconds * 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.sessions().clear(uuid);
        attemptsLeft.remove(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.settings().freezeMovement()) {
            return;
        }
        if (authed(event.getPlayer())) {
            return;
        }
        // 允许视角转动，仅阻止位移，避免“卡死”体验
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (authed(event.getPlayer())) {
            return;
        }
        String root = AuthService.rootCommand(event.getMessage());
        if (!plugin.settings().isCommandAllowed(root)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage(plugin.messagesFor(player).get("command-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        // 异步线程：不做文件读取，使用固定语言的消息实例
        if (!authed(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.messages().get("action-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!authed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!authed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!authed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!authed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && !authed(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p && !authed(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player p && !authed(p)) {
            event.setCancelled(true);
        }
    }

    // ===== 供 LoginCommand 使用的尝试次数管理 =====

    public int decrementAttempts(UUID uuid) {
        int left = attemptsLeft.getOrDefault(uuid, plugin.settings().maxLoginAttempts()) - 1;
        attemptsLeft.put(uuid, left);
        return left;
    }

    public int attemptsLeft(UUID uuid) {
        return attemptsLeft.getOrDefault(uuid, plugin.settings().maxLoginAttempts());
    }
}
