package com.dierks.safelamp.recipe;

import com.dierks.safelamp.config.LampConfig;
import com.dierks.safelamp.item.LampItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers and tears down the four vanilla shaped recipes.
 *
 * <p>Tiers 2, 3 and 4 are <em>upgrade</em> recipes: the centre slot must hold a
 * real lamp of the tier below. The recipe itself can only require the right
 * <em>material</em> there (a lantern, a soul lantern, a beacon) — the check that
 * it is genuinely a SafeLamp and not a vanilla lookalike happens in
 * {@code LampCraftListener}, which reads the PDC tag and blanks the result if it
 * does not match. Doing it that way rather than with an exact-ItemStack choice
 * means a lamp still works in the grid after an admin edits its display name.</p>
 */
public final class LampRecipeManager {

    private final Plugin plugin;
    private final LampItemFactory factory;

    /** Recipe key → tier the recipe produces. Also drives cleanup on disable. */
    private final Map<NamespacedKey, Integer> registered = new LinkedHashMap<>();

    public LampRecipeManager(Plugin plugin, LampItemFactory factory) {
        this.plugin = plugin;
        this.factory = factory;
    }

    /** Drop any previously registered recipes and register the ones config asks for. */
    public void register(LampConfig config) {
        unregisterAll();

        for (LampConfig.Tier tier : config.tiers()) {
            if (!tier.enabled() || !tier.recipeEnabled()) {
                continue;
            }

            NamespacedKey key = keyFor(tier.level());
            // A previous load of the plugin (or a server /reload) can leave the
            // recipe behind; removing first keeps addRecipe from being rejected.
            Bukkit.removeRecipe(key);

            ShapedRecipe recipe = new ShapedRecipe(key, factory.create(tier));
            switch (tier.level()) {
                case 1 -> {
                    recipe.shape("GLG", "GSG", "III");
                    recipe.setIngredient('G', Material.GOLD_INGOT);
                    recipe.setIngredient('L', Material.LANTERN);
                    recipe.setIngredient('S', Material.GLOWSTONE);
                    recipe.setIngredient('I', Material.IRON_INGOT);
                }
                case 2 -> {
                    recipe.shape("DPD", "PTP", "DPD");
                    recipe.setIngredient('D', Material.DIAMOND);
                    recipe.setIngredient('P', Material.PRISMARINE_SHARD);
                    recipe.setIngredient('T', Material.LANTERN);
                }
                case 3 -> {
                    recipe.shape("ENE", "NTN", "ENE");
                    recipe.setIngredient('E', Material.EMERALD);
                    recipe.setIngredient('N', Material.NETHER_STAR);
                    recipe.setIngredient('T', Material.SOUL_LANTERN);
                }
                case 4 -> {
                    recipe.shape("GNG", "NTN", "GNG");
                    recipe.setIngredient('G', Material.GLOWSTONE);
                    recipe.setIngredient('N', Material.NETHERITE_INGOT);
                    recipe.setIngredient('T', Material.BEACON);
                }
                default -> {
                    // No recipe shape defined for this tier — skip it rather than
                    // registering a half-built recipe.
                    continue;
                }
            }

            if (Bukkit.addRecipe(recipe)) {
                registered.put(key, tier.level());
            } else {
                plugin.getLogger().warning("The server refused the tier " + tier.level()
                        + " recipe. Another plugin may already own the key " + key + ".");
            }
        }

        plugin.getLogger().info("Registered " + registered.size() + " lamp recipe"
                + (registered.size() == 1 ? "" : "s") + ".");
    }

    /** Remove every recipe this manager registered. */
    public void unregisterAll() {
        for (NamespacedKey key : registered.keySet()) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
    }

    /**
     * The tier produced by one of our recipes.
     *
     * @return 1..{@link LampConfig#MAX_TIER}, or 0 if the key is not ours
     */
    public int tierForRecipe(NamespacedKey key) {
        if (key == null) {
            return 0;
        }
        Integer tier = registered.get(key);
        return tier == null ? 0 : tier;
    }

    /**
     * The tier a lamp in the centre of the grid must be for {@code resultTier}'s
     * recipe to be legitimate.
     *
     * @return the required input tier, or 0 for tier 1 (which consumes no lamp)
     */
    public static int requiredInputTier(int resultTier) {
        return resultTier <= LampConfig.MIN_TIER ? 0 : resultTier - 1;
    }

    private NamespacedKey keyFor(int tier) {
        return new NamespacedKey(plugin, "tier" + tier);
    }
}
