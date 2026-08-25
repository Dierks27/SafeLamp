package com.dierks.safelamp.command;

import com.dierks.safelamp.SafeLampPlugin;
import com.dierks.safelamp.config.LampConfig;
import com.dierks.safelamp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /safelamp give|reload|info}. */
public final class SafeLampCommand implements CommandExecutor, TabCompleter {

    private final SafeLampPlugin plugin;

    public SafeLampCommand(SafeLampPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, label, args);
            case "reload" -> reload(sender);
            case "info" -> info(sender);
            default -> usage(sender, label);
        }
        return true;
    }

    // ─── /safelamp give <player> <tier> [amount] ───

    private void give(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("safelamp.give")) {
            sender.sendMessage(Text.of("&cYou do not have permission to do that."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.of("&cUsage: &f/" + label + " give <player> <tier> [amount]"));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.of("&cNo player online called &f" + args[1] + "&c."));
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.of("&cTier must be a number between "
                    + LampConfig.MIN_TIER + " and " + LampConfig.MAX_TIER + "."));
            return;
        }

        LampConfig config = plugin.lampConfig();
        LampConfig.Tier tier = config.tier(level);
        if (tier == null) {
            sender.sendMessage(Text.of("&cThere is no tier &f" + level + "&c. Valid tiers: "
                    + LampConfig.MIN_TIER + "–" + LampConfig.MAX_TIER + "."));
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Text.of("&cAmount must be a number."));
                return;
            }
            if (amount < 1 || amount > 64) {
                sender.sendMessage(Text.of("&cAmount must be between 1 and 64."));
                return;
            }
        }

        ItemStack lamp = plugin.itemFactory().create(tier, amount);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(lamp);
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        sender.sendMessage(Text.of("&aGave &f" + amount + "&a × tier &f" + level
                + "&a to &f" + target.getName() + "&a."));
        target.sendMessage(Text.of("&aYou received &r" + tier.displayName() + "&a."));

        if (!tier.enabled()) {
            sender.sendMessage(Text.of("&eHeads up: tier " + level
                    + " is disabled in config.yml, so that lamp will not protect anyone."));
        }
    }

    // ─── /safelamp reload ───

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("safelamp.reload")) {
            sender.sendMessage(Text.of("&cYou do not have permission to do that."));
            return;
        }
        try {
            plugin.reloadEverything();
            sender.sendMessage(Text.of("&aSafeLamp config reloaded."));
        } catch (RuntimeException ex) {
            sender.sendMessage(Text.of("&cReload failed: &f" + ex.getMessage()));
            plugin.getLogger().warning("Reload failed: " + ex);
        }
    }

    // ─── /safelamp info ───

    private void info(CommandSender sender) {
        LampConfig config = plugin.lampConfig();

        sender.sendMessage(Text.of("&e✦ SafeLamp &fv" + plugin.version() + " &e✦"));
        sender.sendMessage(Text.of("&7Scan every &f" + config.scanInterval() + " ticks &7("
                + String.format(Locale.ROOT, "%.1f", config.scanInterval() / 20.0D) + "s)"));
        sender.sendMessage(Text.of("&7Carry mode: &f" + config.carryMode()
                + " &7(offhand counts: &f" + (config.checkOffhand() ? "yes" : "no") + "&7)"));

        for (LampConfig.Tier tier : config.tiers()) {
            String state = tier.enabled() ? "&aon " : "&coff";
            sender.sendMessage(Text.of("&7  Tier " + tier.level() + " " + state
                    + " &7radius &f" + Math.round(tier.radius()) + "&7, recipe "
                    + (tier.recipeEnabled() && tier.enabled() ? "&aon" : "&coff")
                    + " &8— &r" + tier.displayName()));
        }

        Map<UUID, Integer> holders = plugin.despawnTask().activeHolders();
        sender.sendMessage(Text.of("&7Players protected by a carried lamp: &f" + holders.size()));
        for (Map.Entry<UUID, Integer> entry : holders.entrySet()) {
            Player holder = Bukkit.getPlayer(entry.getKey());
            if (holder != null) {
                sender.sendMessage(Text.of("&7  • &f" + holder.getName() + " &7(tier &f" + entry.getValue() + "&7)"));
            }
        }

        if (config.placedLampsEnabled()) {
            Map<Integer, Integer> placed = plugin.placedLamps().countsByTier();
            StringBuilder breakdown = new StringBuilder();
            for (Map.Entry<Integer, Integer> entry : placed.entrySet()) {
                if (!breakdown.isEmpty()) {
                    breakdown.append("&7, ");
                }
                breakdown.append("&ftier ").append(entry.getKey()).append(" ×").append(entry.getValue());
            }
            sender.sendMessage(Text.of("&7Lamps placed in the world: &f" + plugin.placedLamps().count()
                    + (breakdown.isEmpty() ? "" : " &7(" + breakdown + "&7)")));
        } else {
            sender.sendMessage(Text.of("&7Lamps placed in the world: &cdisabled"));
        }

        sender.sendMessage(Text.of("&7Mobs removed since startup: &f" + plugin.despawnTask().totalDespawned()
                + " &7(last scan: &f" + plugin.despawnTask().lastScanDespawned() + "&7)"));
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Text.of("&e✦ SafeLamp ✦"));
        sender.sendMessage(Text.of("&f/" + label + " give <player> <tier> [amount] &7— hand out a lamp"));
        sender.sendMessage(Text.of("&f/" + label + " reload &7— re-read config.yml"));
        sender.sendMessage(Text.of("&f/" + label + " info &7— status and active lamps"));
    }

    // ─── Tab completion ───

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefixed(List.of("give", "reload", "info"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return prefixed(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> tiers = new ArrayList<>();
            for (int level = LampConfig.MIN_TIER; level <= LampConfig.MAX_TIER; level++) {
                tiers.add(String.valueOf(level));
            }
            return prefixed(tiers, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return prefixed(List.of("1", "8", "16", "64"), args[3]);
        }
        return Collections.emptyList();
    }

    private static List<String> prefixed(List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
