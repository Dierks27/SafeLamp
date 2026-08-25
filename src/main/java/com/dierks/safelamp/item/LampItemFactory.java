package com.dierks.safelamp.item;

import com.dierks.safelamp.config.LampConfig;
import com.dierks.safelamp.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds SafeLamp {@link ItemStack}s and identifies them again later.
 *
 * <p>Identification is done purely through a persistent data container tag
 * ({@code safelamp:tier}), never by display name. Anvil-renaming a lamp keeps it
 * working, and naming a plain lantern "Safe Lamp" does not turn it into one.</p>
 */
public final class LampItemFactory {

    private final NamespacedKey tierKey;

    public LampItemFactory(Plugin plugin) {
        // The plugin is named "SafeLamp", so this key is exactly `safelamp:tier`.
        this.tierKey = new NamespacedKey(plugin, "tier");
    }

    /** The PDC key every SafeLamp item carries. */
    public NamespacedKey tierKey() {
        return tierKey;
    }

    /** One lamp of the given tier. */
    public ItemStack create(LampConfig.Tier tier) {
        return create(tier, 1);
    }

    /** {@code amount} lamps of the given tier. */
    public ItemStack create(LampConfig.Tier tier, int amount) {
        ItemStack item = new ItemStack(tier.material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // Only AIR has no meta, and no tier uses it — but never NPE here.
            return item;
        }

        meta.displayName(Text.of(tier.displayName()));

        List<Component> lore = new ArrayList<>(tier.lore().size());
        for (String line : tier.lore()) {
            lore.add(Text.of(line));
        }
        meta.lore(lore);

        // Paper's glint override gives the enchanted shimmer without attaching a
        // real dummy enchantment, so there is nothing to hide with HIDE_ENCHANTS
        // and no chance of the enchantment leaking into a grindstone or anvil.
        meta.setEnchantmentGlintOverride(true);

        applyCustomModelData(meta, tier.customModelData());

        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.INTEGER, tier.level());

        item.setItemMeta(meta);
        return item;
    }

    // setCustomModelData(Integer) is deprecated in favour of the richer
    // CustomModelDataComponent, but it is still the one-line way to set the plain
    // integer that resource packs key off, and it is still present in Paper 26.2.
    @SuppressWarnings("deprecation")
    private static void applyCustomModelData(ItemMeta meta, int value) {
        meta.setCustomModelData(value);
    }

    /**
     * The tier tagged on {@code stack}.
     *
     * @return 1..{@link LampConfig#MAX_TIER}, or 0 if this is not a SafeLamp (or
     *         carries a tier this build does not know about, e.g. after a downgrade)
     */
    public int tierOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer tier = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        if (tier == null || tier < LampConfig.MIN_TIER || tier > LampConfig.MAX_TIER) {
            return 0;
        }
        return tier;
    }

    /** Whether {@code stack} is a SafeLamp of any tier. */
    public boolean isLamp(ItemStack stack) {
        return tierOf(stack) > 0;
    }
}
