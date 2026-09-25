package com.midnightsmp.midnightcombatlog;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MidnightCombatLog extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Long> combatTags = new HashMap<>();
    private int combatDuration;
    private List<String> blockedCommands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        combatDuration = getConfig().getInt("combat-duration-seconds", 10);
        blockedCommands = getConfig().getStringList("blocked-commands");

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("combattag").setExecutor(this);

        getLogger().info("MidnightCombatLog enabled with 10s combat timer!");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null && !attacker.equals(victim)) {
            tagPlayer(victim);
            tagPlayer(attacker);
        }
    }

    private void tagPlayer(Player player) {
        boolean wasInCombat = isTagged(player);
        long expireTime = System.currentTimeMillis() + (combatDuration * 1000L);
        combatTags.put(player.getUniqueId(), expireTime);

        if (!wasInCombat) {
            player.sendMessage(ChatColor.RED + "⚔️ You are now in COMBAT! Do NOT log out for " + combatDuration + " seconds!");
        }
    }

    public boolean isTagged(Player player) {
        if (!combatTags.containsKey(player.getUniqueId())) return false;
        long expireTime = combatTags.get(player.getUniqueId());
        if (System.currentTimeMillis() >= expireTime) {
            combatTags.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isTagged(player)) return;

        String rawCommand = event.getMessage().toLowerCase().replace("/", "").split(" ")[0];
        if (blockedCommands.contains(rawCommand)) {
            event.setCancelled(true);
            long remaining = (combatTags.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            player.sendMessage(ChatColor.RED + "❌ You cannot use /" + rawCommand + " while in combat! (" + remaining + "s left)");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isTagged(player)) {
            player.setHealth(0.0);
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "☠️ " + player.getName() + " combat logged and was slain!");
            combatTags.remove(player.getUniqueId());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player player) {
            if (isTagged(player)) {
                long remaining = (combatTags.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "⚔️ You are currently in combat for " + remaining + " more seconds!");
            } else {
                player.sendMessage(ChatColor.GREEN + "🛡️ You are currently safe and not in combat.");
            }
            return true;
        }
        return false;
    }
}
