package com.dierks.safelamp.listener;

import com.dierks.safelamp.item.LampItemFactory;
import com.dierks.safelamp.recipe.LampRecipeManager;
import com.dierks.safelamp.util.Text;
import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Guards the crafting grid.
 *
 * <p>Two jobs, both driven off the recipe's namespaced key so no display-name
 * matching is involved:</p>
 * <ol>
 *   <li><b>Permission.</b> A player without {@code safelamp.craft.&lt;tier&gt;}
 *       sees an empty result slot rather than a confusing "nothing happened".</li>
 *   <li><b>Upgrade validity.</b> The tier 2/3/4 recipes can only demand a
 *       lantern / soul lantern / beacon by material. This listener additionally
 *       requires the centre item to carry the PDC tag of the tier below, so a
 *       vanilla lantern cannot be laundered into a Safe Lantern. The tier 1
 *       recipe gets the mirror-image check: it refuses to run if a real lamp is
 *       sitting in the grid, which stops a player feeding their Safe Lamp into a
 *       recipe that would silently downgrade it.</li>
 * </ol>
 */
public final class LampCraftListener implements Listener {

    private final LampItemFactory factory;
    private final LampRecipeManager recipes;

    public LampCraftListener(LampItemFactory factory, LampRecipeManager recipes) {
        this.factory = factory;
        this.recipes = recipes;
    }

    /** Blank the result slot when the player may not craft this, or the inputs are wrong. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        int tier = tierOf(event.getRecipe());
        if (tier == 0) {
            return;
        }

        CraftingInventory inventory = event.getInventory();
        HumanEntity viewer = event.getView().getPlayer();

        if (!viewer.hasPermission("safelamp.craft." + tier)) {
            inventory.setResult(null);
            return;
        }
        if (!hasValidLampInput(inventory, tier)) {
            inventory.setResult(null);
        }
    }

    /**
     * Belt and braces: re-check on the actual craft.
     *
     * <p>The prepare handler already empties the result slot, so this should never
     * fire — but a shift-click craft or another plugin re-filling the result would
     * otherwise slip past, and here we can also tell the player why.</p>
     */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        int tier = tierOf(event.getRecipe());
        if (tier == 0) {
            return;
        }

        HumanEntity crafter = event.getWhoClicked();
        if (!crafter.hasPermission("safelamp.craft." + tier)) {
            event.setCancelled(true);
            crafter.sendMessage(Text.of("&cYou do not have permission to craft that lamp."));
            return;
        }
        if (!hasValidLampInput(event.getInventory(), tier)) {
            event.setCancelled(true);
        }
    }

    /** The tier {@code recipe} produces, or 0 if it is not one of ours. */
    private int tierOf(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return 0;
        }
        return recipes.tierForRecipe(keyed.getKey());
    }

    /**
     * Whether the grid holds exactly the lamp (or absence of lamp) this recipe needs.
     *
     * <p>The whole matrix is scanned rather than just the centre slot: the shape
     * pins the lamp to the middle anyway, and scanning everything also catches a
     * player who has parked a second lamp in a corner of the grid.</p>
     */
    private boolean hasValidLampInput(CraftingInventory inventory, int resultTier) {
        int required = LampRecipeManager.requiredInputTier(resultTier);

        int lampsFound = 0;
        int foundTier = 0;
        for (ItemStack item : inventory.getMatrix()) {
            int tier = factory.tierOf(item);
            if (tier > 0) {
                lampsFound++;
                foundTier = tier;
            }
        }

        if (required == 0) {
            // Tier 1 consumes a plain lantern. Any real lamp in the grid is a
            // mistake we should not let the player make.
            return lampsFound == 0;
        }
        return lampsFound == 1 && foundTier == required;
    }
}
