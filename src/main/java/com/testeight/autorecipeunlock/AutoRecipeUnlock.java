package com.testeight.autorecipeunlock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Unlocks (shows in the recipe book) every recipe from the configured
 * namespaces - by default Craftorithm's - for every player, automatically.
 */
public final class AutoRecipeUnlock extends JavaPlugin implements Listener {

    private Set<String> namespaces = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);

        // Craftorithm registers its recipes a little after startup, and again after
        // /craftorithm reload - so re-check online players every so often.
        long interval = Math.max(200L, getConfig().getLong("recheck-interval-ticks", 1200L));
        Bukkit.getScheduler().runTaskTimer(this, this::unlockForEveryone, 200L, interval);
    }

    private void loadSettings() {
        namespaces = new HashSet<>();
        for (String ns : getConfig().getStringList("namespaces")) {
            namespaces.add(ns.toLowerCase(Locale.ROOT));
        }
        if (namespaces.isEmpty()) namespaces.add("craftorithm");
    }

    private List<NamespacedKey> collectKeys() {
        List<NamespacedKey> keys = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof Keyed keyed && namespaces.contains(keyed.getKey().getNamespace())) {
                keys.add(keyed.getKey());
            }
        }
        return keys;
    }

    private int unlock(Player player, List<NamespacedKey> keys) {
        return keys.isEmpty() ? 0 : player.discoverRecipes(keys);
    }

    private void unlockForEveryone() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        List<NamespacedKey> keys = collectKeys();
        for (Player p : Bukkit.getOnlinePlayers()) unlock(p, keys);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Wait a few seconds so Craftorithm's own saved-recipe loading runs first.
        long delay = Math.max(1L, getConfig().getLong("join-delay-ticks", 100L));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) unlock(player, collectKeys());
        }, delay);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSettings();
        }
        List<NamespacedKey> keys = collectKeys();
        int players = 0, total = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            total += unlock(p, keys);
            players++;
        }
        sender.sendMessage(Component.text("Found " + keys.size() + " recipes; unlocked " + total
                + " new recipe(s) across " + players + " online player(s).", NamedTextColor.GREEN));
        return true;
    }
}
