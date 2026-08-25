package com.dierks.safelamp;

import com.dierks.safelamp.block.PlacedLampStore;
import com.dierks.safelamp.command.SafeLampCommand;
import com.dierks.safelamp.config.LampConfig;
import com.dierks.safelamp.item.LampItemFactory;
import com.dierks.safelamp.listener.LampBlockListener;
import com.dierks.safelamp.listener.LampCraftListener;
import com.dierks.safelamp.recipe.LampRecipeManager;
import com.dierks.safelamp.task.DespawnTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * SafeLamp — a craftable lamp that keeps hostile mobs away from whoever carries
 * it, or from wherever it is placed.
 *
 * <p>Built for family servers: a young player can carry the lamp and be safe in
 * survival while everyone else still gets a normal game, and a lamp planted at
 * spawn or in a base holds that area clear with nobody carrying anything.</p>
 *
 * <p>No dependencies, no database, no economy. Just config.yml and this.</p>
 */
public final class SafeLampPlugin extends JavaPlugin {

    /** How often the placed-lamp registry is written to disk, in ticks (30s). */
    private static final long SAVE_INTERVAL_TICKS = 20L * 30L;

    private LampConfig lampConfig;
    private LampItemFactory itemFactory;
    private PlacedLampStore placedLamps;
    private LampRecipeManager recipeManager;
    private DespawnTask despawnTask;

    private BukkitTask scanTask;
    private BukkitTask saveTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lampConfig = LampConfig.load(getConfig(), getLogger());

        itemFactory = new LampItemFactory(this);

        placedLamps = new PlacedLampStore(this);
        placedLamps.load();

        recipeManager = new LampRecipeManager(this, itemFactory);
        recipeManager.register(lampConfig);

        getServer().getPluginManager().registerEvents(
                new LampCraftListener(itemFactory, recipeManager), this);
        getServer().getPluginManager().registerEvents(
                new LampBlockListener(this, itemFactory, placedLamps), this);

        despawnTask = new DespawnTask(this, itemFactory, placedLamps);
        startScanTask();

        saveTask = getServer().getScheduler().runTaskTimer(
                this, placedLamps::flush, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        PluginCommand command = getCommand("safelamp");
        if (command == null) {
            getLogger().severe("The 'safelamp' command is missing from plugin.yml — commands are disabled.");
        } else {
            SafeLampCommand executor = new SafeLampCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("SafeLamp enabled. Scanning every " + lampConfig.scanInterval()
                + " ticks in " + lampConfig.carryMode() + " carry mode.");
    }

    @Override
    public void onDisable() {
        stopTask(scanTask);
        scanTask = null;
        stopTask(saveTask);
        saveTask = null;

        if (recipeManager != null) {
            // Leaving recipes registered would make a plugin reload fail to
            // re-add them under the same keys.
            recipeManager.unregisterAll();
        }
        if (placedLamps != null) {
            placedLamps.flush();
        }
    }

    /**
     * Re-read config.yml and apply everything that can change at runtime.
     *
     * <p>The placed-lamp registry is deliberately <em>not</em> reloaded: it is
     * live world state, not configuration, and re-reading it from disk would
     * discard lamps placed since the last flush.</p>
     */
    public void reloadEverything() {
        reloadConfig();
        lampConfig = LampConfig.load(getConfig(), getLogger());

        recipeManager.register(lampConfig);

        // Radii and tiers may have moved, so let everyone be told again.
        despawnTask.resetAnnouncements();

        stopTask(scanTask);
        startScanTask();
    }

    private void startScanTask() {
        long interval = lampConfig.scanInterval();
        scanTask = getServer().getScheduler().runTaskTimer(this, despawnTask, interval, interval);
    }

    private static void stopTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    // ─── Accessors used by the listeners, task and command ───

    /** The version string from plugin.yml, for {@code /safelamp info}. */
    @SuppressWarnings("deprecation") // getPluginMeta() is the modern spelling; this one is stable across builds
    public String version() {
        return getDescription().getVersion();
    }

    /** The current config snapshot. Re-read this on every use; reload replaces it. */
    public LampConfig lampConfig() {
        return lampConfig;
    }

    public LampItemFactory itemFactory() {
        return itemFactory;
    }

    public PlacedLampStore placedLamps() {
        return placedLamps;
    }

    public LampRecipeManager recipeManager() {
        return recipeManager;
    }

    public DespawnTask despawnTask() {
        return despawnTask;
    }
}
