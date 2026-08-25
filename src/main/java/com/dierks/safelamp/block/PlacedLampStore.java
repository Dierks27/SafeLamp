package com.dierks.safelamp.block;

import com.dierks.safelamp.config.LampConfig;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The registry of lamps that are standing in the world as blocks.
 *
 * <p>Placed lamps are what make SafeLamp usable for a spawn area or a family
 * base: nobody has to carry anything, the lamp just sits there and holds the
 * area clear. Because lanterns and torches carry no block-level persistent data,
 * this class is the sole record of which blocks are lamps, and it is flushed to
 * {@code placed-lamps.yml} so the mapping survives a restart.</p>
 *
 * <p>All access happens on the main server thread (block events and the scan
 * task), so no synchronisation is needed. Writes only mark the store dirty;
 * {@link #flush()} does the actual disk IO on a timer and on shutdown, which
 * keeps a player rapidly placing lamps from causing a write per block.</p>
 */
public final class PlacedLampStore {

    private static final String FILE_NAME = "placed-lamps.yml";

    private final Plugin plugin;
    private final File file;
    private final Map<String, LampSite> sites = new LinkedHashMap<>();
    private boolean dirty;

    public PlacedLampStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /** Read {@code placed-lamps.yml}, discarding entries that no longer make sense. */
    public void load() {
        sites.clear();
        dirty = false;
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> raw = yaml.getMapList("lamps");
        int skipped = 0;

        for (Map<?, ?> entry : raw) {
            try {
                Object rawWorld = entry.get("world");
                String world = rawWorld == null ? null : String.valueOf(rawWorld);
                int x = intOf(entry.get("x"));
                int y = intOf(entry.get("y"));
                int z = intOf(entry.get("z"));
                int tier = intOf(entry.get("tier"));
                if (world == null || world.isBlank()
                        || x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE
                        || tier < LampConfig.MIN_TIER || tier > LampConfig.MAX_TIER) {
                    skipped++;
                    continue;
                }

                Object rawOwner = entry.get("owner");
                UUID owner = null;
                if (rawOwner != null && !String.valueOf(rawOwner).isBlank()) {
                    try {
                        owner = UUID.fromString(String.valueOf(rawOwner));
                    } catch (IllegalArgumentException ignored) {
                        // A corrupt owner id is not worth dropping the lamp over.
                    }
                }

                long placedAt = entry.get("placed-at") instanceof Number n ? n.longValue() : 0L;

                LampSite site = new LampSite(world, x, y, z, tier, owner, placedAt);
                sites.put(site.key(), site);
            } catch (RuntimeException ex) {
                skipped++;
            }
        }

        if (skipped > 0) {
            plugin.getLogger().warning("Skipped " + skipped + " unreadable entr"
                    + (skipped == 1 ? "y" : "ies") + " in " + FILE_NAME + ".");
        }
        plugin.getLogger().info("Loaded " + sites.size() + " placed lamp"
                + (sites.size() == 1 ? "" : "s") + ".");
    }

    private static int intOf(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.MIN_VALUE;
    }

    /** Write the registry out if anything has changed since the last flush. */
    public void flush() {
        if (!dirty) {
            return;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> out = new ArrayList<>(sites.size());
        for (LampSite site : sites.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", site.world());
            entry.put("x", site.x());
            entry.put("y", site.y());
            entry.put("z", site.z());
            entry.put("tier", site.tier());
            entry.put("owner", site.owner() == null ? "" : site.owner().toString());
            entry.put("placed-at", site.placedAt());
            out.add(entry);
        }
        yaml.set("lamps", out);

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create " + parent + " — placed lamps were not saved.");
                return;
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException ex) {
            // Keep `dirty` set so the next flush retries rather than losing the data.
            plugin.getLogger().warning("Could not save " + FILE_NAME + ": " + ex.getMessage());
        }
    }

    /**
     * Record {@code block} as a placed lamp.
     *
     * @return {@code false} if that position was already registered
     */
    public boolean add(Block block, int tier, UUID owner) {
        LampSite site = new LampSite(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                tier, owner, System.currentTimeMillis());
        if (sites.putIfAbsent(site.key(), site) != null) {
            return false;
        }
        dirty = true;
        return true;
    }

    /** Forget the lamp at {@code block}, if any. */
    public LampSite remove(Block block) {
        return remove(LampSite.key(block));
    }

    /** Forget the lamp registered under {@code key}, if any. */
    public LampSite remove(String key) {
        LampSite removed = sites.remove(key);
        if (removed != null) {
            dirty = true;
        }
        return removed;
    }

    /** The lamp registered at {@code block}, or {@code null}. */
    public LampSite at(Block block) {
        return sites.get(LampSite.key(block));
    }

    /** Every registered lamp. The returned view is a snapshot safe to iterate while removing. */
    public Collection<LampSite> all() {
        return sites.isEmpty() ? Collections.emptyList() : new ArrayList<>(sites.values());
    }

    public int count() {
        return sites.size();
    }

    /** How many placed lamps of each tier exist, indexed by tier level. */
    public Map<Integer, Integer> countsByTier() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (LampSite site : sites.values()) {
            counts.merge(site.tier(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Forget every registered lamp in {@code worldName}.
     *
     * <p>Not used on a routine reload — disabling a world only stops the scan,
     * leaving the lamps in the ground. This exists for an explicit admin purge.</p>
     *
     * @return how many registrations were dropped
     */
    public int removeAllIn(String worldName) {
        List<String> doomed = new ArrayList<>();
        for (LampSite site : sites.values()) {
            if (site.world().equalsIgnoreCase(worldName)) {
                doomed.add(site.key());
            }
        }
        doomed.forEach(this::remove);
        return doomed.size();
    }
}
