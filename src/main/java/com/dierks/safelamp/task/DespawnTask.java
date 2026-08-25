package com.dierks.safelamp.task;

import com.dierks.safelamp.SafeLampPlugin;
import com.dierks.safelamp.block.LampSite;
import com.dierks.safelamp.block.PlacedLampStore;
import com.dierks.safelamp.config.LampConfig;
import com.dierks.safelamp.item.LampItemFactory;
import com.dierks.safelamp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The heart of the plugin: every {@code scan-interval} ticks, clear hostile mobs
 * out of the bubble around each lamp.
 *
 * <p>Two sources of protection are swept in one pass — lamps players are carrying
 * and lamps standing in the world as blocks. Removal is {@link Entity#remove()},
 * never damage: no death event, no drops, no XP, no sound. A creeper near a
 * six-year-old should simply stop existing.</p>
 */
public final class DespawnTask implements Runnable {

    /**
     * Bosses named outright. Never removed, no matter what {@code despawn-mobs} says.
     *
     * <p>Hardcoded rather than configurable: silently deleting a boss would eat
     * the fight, the loot and (for the dragon) the end portal, and no
     * family-friendly setting is worth that failure mode. A lamp must never
     * become a one-click boss-skip button.</p>
     *
     * <p>This list is only the belt. The braces is an {@code instanceof}
     * {@link Boss} test, which covers every current and future boss the server
     * implements without this list having to be updated.</p>
     */
    private static final Set<EntityType> PROTECTED_BOSSES = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER);

    private final SafeLampPlugin plugin;
    private final LampItemFactory factory;
    private final PlacedLampStore store;

    /** Player → tier we last told them about, so the action bar is not spammed. */
    private final Map<UUID, Integer> announcedTier = new HashMap<>();

    private long totalDespawned;
    private int lastScanDespawned;

    public DespawnTask(SafeLampPlugin plugin, LampItemFactory factory, PlacedLampStore store) {
        this.plugin = plugin;
        this.factory = factory;
        this.store = store;
    }

    @Override
    public void run() {
        LampConfig config = plugin.lampConfig();
        lastScanDespawned = 0;

        scanCarriedLamps(config);

        if (config.placedLampsEnabled()) {
            scanPlacedLamps(config);
        }
    }

    // ─── Carried lamps ───

    private void scanCarriedLamps(LampConfig config) {
        Set<UUID> online = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());

            if (!config.isWorldEnabled(player.getWorld())) {
                announcedTier.remove(player.getUniqueId());
                continue;
            }

            int level = highestUsableTier(player, config);
            if (level == 0) {
                // Dropping the lamp clears the announcement, so picking one back
                // up greets the player again rather than staying silent forever.
                announcedTier.remove(player.getUniqueId());
                continue;
            }

            LampConfig.Tier tier = config.tier(level);
            announce(player, tier, config);
            despawnAround(player.getLocation(), tier.radius(), config);
        }

        // Players who logged off should not keep an entry alive.
        announcedTier.keySet().retainAll(online);
    }

    /**
     * The best tier this player can actually benefit from right now.
     *
     * <p>"Best" means highest, but a tier only counts if it is enabled in config
     * <em>and</em> the player holds {@code safelamp.use.&lt;tier&gt;}. A player
     * carrying a Safe Beacon they lack permission for still gets the protection
     * of the Safe Lamp in the next slot over.</p>
     *
     * @return 1..{@link LampConfig#MAX_TIER}, or 0 for no usable lamp
     */
    private int highestUsableTier(Player player, LampConfig config) {
        PlayerInventory inventory = player.getInventory();
        int best = 0;

        switch (config.carryMode()) {
            case MAIN_HAND -> best = consider(best, inventory.getItemInMainHand(), player, config);
            case HAND -> {
                best = consider(best, inventory.getItemInMainHand(), player, config);
                if (config.checkOffhand()) {
                    best = consider(best, inventory.getItemInOffHand(), player, config);
                }
            }
            case INVENTORY -> {
                // getStorageContents() is the 36 backpack + hotbar slots. Armor
                // slots and the ender chest are deliberately not included.
                for (ItemStack item : inventory.getStorageContents()) {
                    best = consider(best, item, player, config);
                }
                if (config.checkOffhand()) {
                    best = consider(best, inventory.getItemInOffHand(), player, config);
                }
            }
        }
        return best;
    }

    private int consider(int best, ItemStack item, Player player, LampConfig config) {
        int level = factory.tierOf(item);
        if (level <= best) {
            return best;
        }
        LampConfig.Tier tier = config.tier(level);
        if (tier == null || !tier.enabled()) {
            return best;
        }
        if (!player.hasPermission("safelamp.use." + level)) {
            return best;
        }
        return level;
    }

    private void announce(Player player, LampConfig.Tier tier, LampConfig config) {
        Integer previous = announcedTier.put(player.getUniqueId(), tier.level());
        if (!config.activationMessage()) {
            return;
        }
        if (previous == null || previous != tier.level()) {
            player.sendActionBar(Text.of(tier.activationMessage()));
        }
    }

    // ─── Placed lamps ───

    private void scanPlacedLamps(LampConfig config) {
        for (LampSite site : store.all()) {
            World world = Bukkit.getWorld(site.world());
            if (world == null) {
                // The world is not loaded right now (or was renamed). Leave the
                // record alone — it becomes valid again if the world comes back.
                continue;
            }
            if (!config.isWorldEnabled(world)) {
                continue;
            }

            LampConfig.Tier tier = config.tier(site.tier());
            if (tier == null || !tier.enabled()) {
                continue;
            }

            // An unloaded chunk cannot hold mobs that matter, and touching it
            // would force a load on every scan.
            if (!world.isChunkLoaded(site.x() >> 4, site.z() >> 4)) {
                continue;
            }

            Block block = world.getBlockAt(site.x(), site.y(), site.z());
            if (!tier.isPlacedForm(block.getType())) {
                // Destroyed by something we do not hook (world edit, another
                // plugin, a manual block change). Forget it.
                store.remove(site.key());
                continue;
            }

            despawnAround(block.getLocation().add(0.5D, 0.5D, 0.5D),
                    config.placedRadius(tier), config);
        }
    }

    // ─── The sweep itself ───

    /**
     * Remove every despawnable mob within {@code radius} of {@code centre}.
     *
     * <p>{@code getNearbyEntities} works on a bounding box, so the results are
     * filtered down to an actual sphere afterwards. Without that, "Radius: 10
     * blocks" would quietly mean "up to 17 blocks away, diagonally".</p>
     */
    private void despawnAround(Location centre, double radius, LampConfig config) {
        World world = centre.getWorld();
        if (world == null) {
            return;
        }

        double radiusSquared = radius * radius;
        for (Entity entity : world.getNearbyEntities(centre, radius, radius, radius)) {
            if (!shouldDespawn(entity, config)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(centre) > radiusSquared) {
                continue;
            }

            if (config.particles()) {
                world.spawnParticle(Particle.SMOKE,
                        entity.getLocation().add(0.0D, entity.getHeight() / 2.0D, 0.0D),
                        5, 0.3D, 0.3D, 0.3D, 0.0D);
            }
            entity.remove();

            totalDespawned++;
            lastScanDespawned++;
        }
    }

    /** Every reason a mob may or may not be removed, in cheapest-check-first order. */
    private boolean shouldDespawn(Entity entity, LampConfig config) {
        // Overlapping lamps see the same mob twice in one pass. Without this the
        // second lamp would puff particles at an already-deleted mob and count it
        // again in the despawn total.
        if (!entity.isValid()) {
            return false;
        }
        if (!(entity instanceof LivingEntity living) || entity instanceof Player) {
            return false;
        }

        // Bosses are off limits unless an admin has explicitly opted in, by
        // interface and by name. The interface catches anything the server counts
        // as a boss (including any boss a future version adds); the name list is
        // the fallback for a boss that does not implement it.
        EntityType type = entity.getType();
        if (!config.allowBossDespawn() && (entity instanceof Boss || PROTECTED_BOSSES.contains(type))) {
            return false;
        }
        if (!config.despawnTypes().contains(type)) {
            return false;
        }
        if (config.protectNamedMobs() && entity.customName() != null) {
            return false;
        }
        // ─── Custom mobs from other plugins ───
        // A boss plugin's boss is usually a reskinned vanilla mob — a ZOMBIE with
        // 4000 health and a boss bar — so it sails straight through the type
        // whitelist above. These two checks are what keep a lamp from quietly
        // deleting somebody's raid boss.
        if (isTooHealthyToBeOrdinary(living, config) || isTaggedByAnotherPlugin(entity, config)) {
            return false;
        }
        // Part of a ride: a mob on a boat or minecart, or one carrying a rider.
        // Removing half of a ride leaves the other half in a broken state.
        if (entity.isInsideVehicle() || !entity.getPassengers().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Whether this mob is far beefier than the vanilla mob it claims to be.
     *
     * <p>The threshold ships at a Warden's 500 health, so nothing vanilla trips
     * it; anything above that was built by a plugin and is not ours to delete.</p>
     */
    private boolean isTooHealthyToBeOrdinary(LivingEntity living, LampConfig config) {
        double threshold = config.protectAboveMaxHealth();
        if (threshold <= 0.0D) {
            return false;
        }
        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        return maxHealth != null && maxHealth.getValue() > threshold;
    }

    /** Whether another plugin has claimed this mob with a persistent-data tag. */
    private boolean isTaggedByAnotherPlugin(Entity entity, LampConfig config) {
        Set<String> protectedNamespaces = config.protectedNamespaces();
        if (protectedNamespaces.isEmpty()) {
            return false;
        }
        for (NamespacedKey key : entity.getPersistentDataContainer().getKeys()) {
            if (protectedNamespaces.contains(key.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    // ─── Stats for /safelamp info ───

    /** Snapshot of who is currently protected by a carried lamp, and at what tier. */
    public Map<UUID, Integer> activeHolders() {
        return Map.copyOf(announcedTier);
    }

    public long totalDespawned() {
        return totalDespawned;
    }

    public int lastScanDespawned() {
        return lastScanDespawned;
    }

    /** Forget every cached activation so the next scan re-greets everyone. */
    public void resetAnnouncements() {
        announcedTier.clear();
    }
}
