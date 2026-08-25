package com.dierks.safelamp.config;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * An immutable snapshot of config.yml.
 *
 * <p>A fresh instance is built on enable and on every {@code /safelamp reload};
 * nothing mutates one after {@link #load}. That keeps the scan task free of
 * synchronisation concerns — it always reads one coherent set of settings.</p>
 */
public final class LampConfig {

    /** Lowest and highest tier this plugin knows about. */
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 4;

    // ─── Per-tier constants that are NOT configurable ───
    // The base item and custom-model-data are part of the plugin's identity: a
    // resource pack keys off them, and the upgrade recipes are written against
    // these materials. Radius and display name ARE configurable (see load()).
    private static final Material[] MATERIALS = {
            Material.LANTERN, Material.SOUL_LANTERN, Material.BEACON, Material.SOUL_TORCH,
    };
    private static final int[] MODEL_DATA = {7001, 7002, 7003, 7004};

    /**
     * Block forms each tier can legitimately take once placed.
     *
     * <p>Only the torch differs from its item: a torch placed against the side of
     * a block becomes {@code SOUL_WALL_TORCH}, so a placed Safe Torch has to be
     * recognised in either orientation or the periodic sanity check would decide
     * a wall-mounted one had been destroyed and forget it.</p>
     */
    private static final Set<Material>[] PLACED_FORMS = placedForms();

    @SuppressWarnings("unchecked")
    private static Set<Material>[] placedForms() {
        return new Set[]{
                Set.of(Material.LANTERN),
                Set.of(Material.SOUL_LANTERN),
                Set.of(Material.BEACON),
                Set.of(Material.SOUL_TORCH, Material.SOUL_WALL_TORCH),
        };
    }

    private static final String[] DEFAULT_NAMES = {
            "&e✦ Safe Lamp ✦",
            "&b✦ Safe Lantern ✦",
            "&d✦ Safe Beacon ✦",
            "&6✦ Safe Torch ✦",
    };

    /** Flavour lore, minus the radius line — that is generated from the live radius. */
    private static final String[][] LORE_FLAVOUR = {
            {"&7A warm glow that keeps", "&7the monsters away."},
            {"&7A soulful light that", "&7banishes darkness further."},
            {"&7A radiant shield of pure", "&7light. Nothing dark survives."},
            {"&7An everburning flame for", "&7hearth and home. Place it down", "&7and rest easy."},
    };

    /** Colour code the "Radius: N blocks" lore line is printed in, per tier. */
    private static final String[] RADIUS_COLOUR = {"&6", "&b", "&d", "&6"};

    private static final String[] ACTIVATION = {
            "&e✦ Your Safe Lamp glows warmly ✦",
            "&b✦ Your Safe Lantern pulses with light ✦",
            "&d✦ Your Safe Beacon radiates protection ✦",
            "&6✦ Your Safe Torch burns steady and bright ✦",
    };

    private static final int[] DEFAULT_RADIUS = {10, 25, 50, 100};

    /**
     * The despawn list shipped in config.yml, duplicated here as a fallback.
     *
     * <p>Used only when {@code despawn-mobs} is missing entirely — which is what
     * an older config file looks like after an upgrade that adds the key. Without
     * this the plugin would load happily and protect nobody. An admin who
     * genuinely wants nothing despawned can still set the key to an empty list;
     * that is respected (with a warning), because it was written deliberately.</p>
     */
    private static final List<String> DEFAULT_DESPAWN_MOBS = List.of(
            "ZOMBIE", "SKELETON", "CREEPER", "SPIDER", "CAVE_SPIDER", "WITCH", "PHANTOM",
            "DROWNED", "HUSK", "STRAY", "ZOMBIE_VILLAGER", "SILVERFISH", "ENDERMITE",
            "SLIME", "MAGMA_CUBE", "BLAZE", "GHAST", "WITHER_SKELETON", "PIGLIN_BRUTE",
            "VINDICATOR", "PILLAGER", "RAVAGER", "VEX", "EVOKER", "GUARDIAN",
            "ELDER_GUARDIAN", "ENDERMAN", "SHULKER", "HOGLIN", "ZOGLIN", "WARDEN",
            "BREEZE", "BOGGED");

    /**
     * One tier's resolved settings.
     *
     * @param level             1 through {@link #MAX_TIER}
     * @param enabled           whether this tier protects its holder at all
     * @param radius            protection radius in blocks (spherical)
     * @param displayName       '&amp;'-coded item name
     * @param recipeEnabled     whether the crafting recipe is registered
     * @param material          base vanilla item
     * @param placedForms       block materials this tier can appear as once placed
     * @param customModelData   resource-pack hook
     * @param lore              '&amp;'-coded lore lines, radius line included
     * @param activationMessage '&amp;'-coded action-bar text shown when the tier engages
     */
    public record Tier(int level, boolean enabled, double radius, String displayName,
                       boolean recipeEnabled, Material material, Set<Material> placedForms,
                       int customModelData, List<String> lore, String activationMessage) {

        /** Whether {@code blockType} is a legitimate placed form of this tier. */
        public boolean isPlacedForm(Material blockType) {
            return placedForms.contains(blockType);
        }
    }

    private final List<Tier> tiers;
    private final int scanInterval;
    private final CarryMode carryMode;
    private final boolean checkOffhand;
    private final boolean placedLampsEnabled;
    private final double placedRadiusMultiplier;
    private final boolean explosionProofPlacedLamps;
    private final Set<EntityType> despawnTypes;
    private final boolean protectNamedMobs;
    private final boolean allowBossDespawn;
    private final double protectAboveMaxHealth;
    private final Set<String> protectedNamespaces;
    private final boolean particles;
    private final boolean activationMessage;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;

    private LampConfig(List<Tier> tiers, int scanInterval, CarryMode carryMode, boolean checkOffhand,
                       boolean placedLampsEnabled, double placedRadiusMultiplier,
                       boolean explosionProofPlacedLamps, Set<EntityType> despawnTypes,
                       boolean protectNamedMobs, boolean allowBossDespawn,
                       double protectAboveMaxHealth, Set<String> protectedNamespaces, boolean particles,
                       boolean activationMessage, Set<String> enabledWorlds, Set<String> disabledWorlds) {
        this.tiers = List.copyOf(tiers);
        this.scanInterval = scanInterval;
        this.carryMode = carryMode;
        this.checkOffhand = checkOffhand;
        this.placedLampsEnabled = placedLampsEnabled;
        this.placedRadiusMultiplier = placedRadiusMultiplier;
        this.explosionProofPlacedLamps = explosionProofPlacedLamps;
        this.despawnTypes = Set.copyOf(despawnTypes);
        this.protectNamedMobs = protectNamedMobs;
        this.allowBossDespawn = allowBossDespawn;
        this.protectAboveMaxHealth = protectAboveMaxHealth;
        this.protectedNamespaces = Set.copyOf(protectedNamespaces);
        this.particles = particles;
        this.activationMessage = activationMessage;
        this.enabledWorlds = Set.copyOf(enabledWorlds);
        this.disabledWorlds = Set.copyOf(disabledWorlds);
    }

    /**
     * Read every key out of {@code cfg}, warning (never throwing) on anything
     * malformed so a typo in one field cannot stop the plugin from loading.
     */
    public static LampConfig load(FileConfiguration cfg, Logger log) {
        List<Tier> tiers = new ArrayList<>(MAX_TIER);
        for (int level = MIN_TIER; level <= MAX_TIER; level++) {
            int i = level - 1;
            ConfigurationSection section = cfg.getConfigurationSection("lamps.tier" + level);

            boolean enabled = section == null || section.getBoolean("enabled", true);
            boolean recipeEnabled = section == null || section.getBoolean("recipe-enabled", true);
            String name = section == null ? DEFAULT_NAMES[i] : section.getString("display-name", DEFAULT_NAMES[i]);

            int radius = section == null ? DEFAULT_RADIUS[i] : section.getInt("radius", DEFAULT_RADIUS[i]);
            if (radius < 1) {
                log.warning("lamps.tier" + level + ".radius was " + radius
                        + " — a radius below 1 would protect nothing. Using " + DEFAULT_RADIUS[i] + ".");
                radius = DEFAULT_RADIUS[i];
            } else if (radius > 256) {
                // getNearbyEntities cost grows with the cube of the radius; past a
                // few hundred blocks a single scan can stall the main thread.
                log.warning("lamps.tier" + level + ".radius was " + radius
                        + " — clamped to 256 to keep the scan affordable.");
                radius = 256;
            }

            List<String> lore = new ArrayList<>(List.of(LORE_FLAVOUR[i]));
            lore.add(RADIUS_COLOUR[i] + "Radius: " + radius + " blocks");

            tiers.add(new Tier(level, enabled, radius, name, recipeEnabled,
                    MATERIALS[i], PLACED_FORMS[i], MODEL_DATA[i],
                    List.copyOf(lore), ACTIVATION[i]));
        }

        int scanInterval = cfg.getInt("scan-interval", 40);
        if (scanInterval < 1) {
            log.warning("scan-interval was " + scanInterval + " — must be at least 1 tick. Using 40.");
            scanInterval = 40;
        }

        String rawMode = cfg.getString("carry-mode", "INVENTORY");
        CarryMode carryMode = CarryMode.parse(rawMode);
        if (carryMode == null) {
            log.warning("carry-mode '" + rawMode + "' is not one of INVENTORY, HAND or MAIN_HAND."
                    + " Falling back to INVENTORY.");
            carryMode = CarryMode.INVENTORY;
        }

        boolean checkOffhand = cfg.getBoolean("check-offhand", true);

        boolean placedEnabled = cfg.getBoolean("placed-lamps.enabled", true);
        boolean explosionProof = cfg.getBoolean("placed-lamps.explosion-proof", true);
        double placedMultiplier = cfg.getDouble("placed-lamps.radius-multiplier", 1.0D);
        if (placedMultiplier <= 0.0D) {
            log.warning("placed-lamps.radius-multiplier was " + placedMultiplier
                    + " — must be greater than 0. Using 1.0.");
            placedMultiplier = 1.0D;
        }

        boolean despawnListWritten = cfg.isSet("despawn-mobs");
        List<String> rawDespawnList = despawnListWritten
                ? cfg.getStringList("despawn-mobs")
                : DEFAULT_DESPAWN_MOBS;
        if (!despawnListWritten) {
            log.warning("despawn-mobs is missing from config.yml — falling back to the"
                    + " built-in hostile-mob list. Add the key back to customise it.");
        }

        Set<EntityType> despawn = new HashSet<>();
        for (String raw : rawDespawnList) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String name = raw.trim().toUpperCase(Locale.ROOT);
            try {
                despawn.add(EntityType.valueOf(name));
            } catch (IllegalArgumentException ex) {
                log.warning("despawn-mobs: '" + raw + "' is not a known entity type — skipping it.");
            }
        }
        // Boss handling. The default is absolute protection: a lamp must not be a
        // one-click way to delete the Ender Dragon and skip the fight, the loot and
        // the return portal. An admin who genuinely wants that has to say so out
        // loud, and gets told at every startup that they did.
        double protectAboveMaxHealth = cfg.getDouble("protect-above-max-health", 500.0D);
        if (protectAboveMaxHealth < 0.0D) {
            log.warning("protect-above-max-health was negative — treating it as 0 (disabled).");
            protectAboveMaxHealth = 0.0D;
        }

        Set<String> protectedNamespaces = new HashSet<>();
        for (String raw : cfg.getStringList("protect-tagged-mobs")) {
            if (raw != null && !raw.isBlank()) {
                protectedNamespaces.add(raw.trim().toLowerCase(Locale.ROOT));
            }
        }

        boolean allowBossDespawn = cfg.getBoolean("allow-boss-despawn", false);
        if (allowBossDespawn) {
            log.warning("allow-boss-despawn is ON. Lamps will delete the Ender Dragon,"
                    + " the Wither and any other boss outright — no fight, no loot, no portal."
                    + " Set it back to false unless this is really what you want.");
        } else {
            for (EntityType boss : List.of(EntityType.ENDER_DRAGON, EntityType.WITHER)) {
                if (despawn.remove(boss)) {
                    log.warning("despawn-mobs lists " + boss.name() + ", but boss mobs are"
                            + " protected while allow-boss-despawn is false. Ignoring that entry.");
                }
            }
        }

        if (despawn.isEmpty()) {
            log.warning("despawn-mobs resolved to nothing, so lamps will not remove any mob."
                    + " If that was not intended, check the entity names in config.yml.");
        }

        return new LampConfig(
                tiers,
                scanInterval,
                carryMode,
                checkOffhand,
                placedEnabled,
                placedMultiplier,
                explosionProof,
                despawn,
                cfg.getBoolean("protect-named-mobs", true),
                allowBossDespawn,
                protectAboveMaxHealth,
                protectedNamespaces,
                cfg.getBoolean("particles", true),
                cfg.getBoolean("activation-message", true),
                lowercased(cfg.getStringList("enabled-worlds")),
                lowercased(cfg.getStringList("disabled-worlds")));
    }

    private static Set<String> lowercased(List<String> raw) {
        Set<String> out = new HashSet<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** @return the settings for {@code level}, or {@code null} if it is out of range */
    public Tier tier(int level) {
        if (level < MIN_TIER || level > MAX_TIER) {
            return null;
        }
        return tiers.get(level - 1);
    }

    /** All tiers, lowest first. */
    public List<Tier> tiers() {
        return tiers;
    }

    /** The protection radius a placed lamp of {@code tier} projects. */
    public double placedRadius(Tier tier) {
        return tier.radius() * placedRadiusMultiplier;
    }

    /**
     * Whether lamps work in {@code world}. {@code disabled-worlds} wins over
     * {@code enabled-worlds}; an empty {@code enabled-worlds} means "everywhere".
     */
    public boolean isWorldEnabled(World world) {
        String name = world.getName().toLowerCase(Locale.ROOT);
        if (disabledWorlds.contains(name)) {
            return false;
        }
        return enabledWorlds.isEmpty() || enabledWorlds.contains(name);
    }

    public int scanInterval() {
        return scanInterval;
    }

    public CarryMode carryMode() {
        return carryMode;
    }

    public boolean checkOffhand() {
        return checkOffhand;
    }

    public boolean placedLampsEnabled() {
        return placedLampsEnabled;
    }

    public double placedRadiusMultiplier() {
        return placedRadiusMultiplier;
    }

    public boolean explosionProofPlacedLamps() {
        return explosionProofPlacedLamps;
    }

    public Set<EntityType> despawnTypes() {
        return despawnTypes;
    }

    public boolean protectNamedMobs() {
        return protectNamedMobs;
    }

    /**
     * Whether bosses may be despawned. Ships {@code false} and should stay there:
     * see the warning printed by {@link #load} when it is turned on.
     */
    public boolean allowBossDespawn() {
        return allowBossDespawn;
    }

    /**
     * Protect any mob whose max health exceeds this, or 0 to disable the check.
     *
     * <p>A blunt but effective way to spare custom bosses: a boss plugin's mobs
     * are usually reskinned vanilla types (a ZOMBIE with 2000 health and a boss
     * bar), which the {@code despawn-mobs} whitelist would otherwise happily
     * delete. The default of 500 is exactly a Warden's health, so no vanilla mob
     * is ever protected by it.</p>
     */
    public double protectAboveMaxHealth() {
        return protectAboveMaxHealth;
    }

    /**
     * Plugin namespaces whose tagged mobs are never despawned.
     *
     * <p>Boss and custom-mob plugins mark their entities with a persistent-data
     * key in their own namespace. Matching on the namespace means SafeLamp can
     * leave those mobs alone without depending on, or even knowing about, the
     * plugin that made them.</p>
     */
    public Set<String> protectedNamespaces() {
        return protectedNamespaces;
    }

    public boolean particles() {
        return particles;
    }

    public boolean activationMessage() {
        return activationMessage;
    }
}
