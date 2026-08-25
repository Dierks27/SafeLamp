package com.dierks.safelamp.block;

import org.bukkit.block.Block;

import java.util.UUID;

/**
 * A lamp that has been placed in the world as a block.
 *
 * <p>Ordinary lanterns and torches are not tile entities, so there is nowhere on
 * the block itself to keep a persistent data container. Placed lamps are instead
 * tracked in {@link PlacedLampStore} and saved to {@code placed-lamps.yml}.</p>
 *
 * @param world  world name (not UUID — worlds are addressed by name in config too)
 * @param owner  who placed it, or {@code null} if unknown (e.g. an older save)
 */
public record LampSite(String world, int x, int y, int z, int tier, UUID owner, long placedAt) {

    /** Map key: unique per block position. */
    public String key() {
        return key(world, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + ':' + x + ':' + y + ':' + z;
    }

    public static String key(Block block) {
        return key(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /** Human-readable position for {@code /safelamp info}. */
    public String describe() {
        return world + " " + x + ", " + y + ", " + z;
    }
}
