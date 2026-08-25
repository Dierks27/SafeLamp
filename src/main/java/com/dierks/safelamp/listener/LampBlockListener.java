package com.dierks.safelamp.listener;

import com.dierks.safelamp.SafeLampPlugin;
import com.dierks.safelamp.block.LampSite;
import com.dierks.safelamp.block.PlacedLampStore;
import com.dierks.safelamp.config.LampConfig;
import com.dierks.safelamp.item.LampItemFactory;
import com.dierks.safelamp.util.Text;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Keeps the placed-lamp registry in step with the world.
 *
 * <p>A lantern or torch is not a tile entity, so a placed lamp has no block-level
 * persistent data to identify it by. Everything hangs off this listener plus
 * {@link PlacedLampStore}: place registers, break unregisters and hands the
 * tagged item back, and the destructive edge cases (explosions, pistons, water)
 * are refused outright so a player can never silently lose an expensive lamp.
 * Anything that still slips through — a world edit, another plugin — is caught by
 * the sanity check the scan task runs over every registered site.</p>
 */
public final class LampBlockListener implements Listener {

    private final SafeLampPlugin plugin;
    private final LampItemFactory factory;
    private final PlacedLampStore store;

    public LampBlockListener(SafeLampPlugin plugin, LampItemFactory factory, PlacedLampStore store) {
        this.plugin = plugin;
        this.factory = factory;
        this.store = store;
    }

    /** Register a lamp as it goes into the ground, or refuse the placement outright. */
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        int level = factory.tierOf(event.getItemInHand());
        if (level == 0) {
            return;
        }

        LampConfig config = plugin.lampConfig();
        Player player = event.getPlayer();
        LampConfig.Tier tier = config.tier(level);

        // Every refusal below cancels the placement rather than letting the block
        // go down. A placed lamp we do not register is indistinguishable from a
        // vanilla lantern, so breaking it later would drop a plain one — the
        // player would have quietly lost their lamp. Cancelling keeps the item.
        if (!config.placedLampsEnabled()) {
            event.setCancelled(true);
            player.sendActionBar(Text.of("&cSafe Lamps cannot be placed as blocks on this server."));
            return;
        }
        if (tier == null || !tier.enabled()) {
            event.setCancelled(true);
            player.sendActionBar(Text.of("&cThat lamp tier is disabled on this server."));
            return;
        }
        if (!player.hasPermission("safelamp.place." + level)) {
            event.setCancelled(true);
            player.sendActionBar(Text.of("&cYou do not have permission to place that lamp."));
            return;
        }

        Block block = event.getBlockPlaced();
        if (!tier.isPlacedForm(block.getType())) {
            // The item turned into something we would not recognise later (an
            // unexpected block state). Refuse rather than lose track of it.
            event.setCancelled(true);
            return;
        }

        store.add(block, level, player.getUniqueId());

        if (config.activationMessage()) {
            long radius = Math.round(config.placedRadius(tier));
            player.sendActionBar(Text.of("&a✦ Lamp placed — protecting " + radius + " blocks around it ✦"));
        }
    }

    /** Unregister on break and give back the tagged lamp instead of a vanilla drop. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        LampSite site = store.at(block);
        if (site == null) {
            return;
        }
        store.remove(block);

        LampConfig.Tier tier = plugin.lampConfig().tier(site.tier());
        if (tier == null) {
            return;
        }

        // Creative breaking never drops anything in vanilla; keep that contract.
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }

        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.5D, 0.5D), factory.create(tier));
    }

    // ─── Destruction the player did not ask for ───
    // A Safe Beacon costs four nether stars; having one vanish to a stray creeper
    // or a piston would be miserable. Placed lamps simply refuse to be destroyed
    // by these, which also means the registry can never drift because of them.

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.lampConfig().explosionProofPlacedLamps()) {
            shieldFromExplosion(event.blockList());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.lampConfig().explosionProofPlacedLamps()) {
            shieldFromExplosion(event.blockList());
        }
    }

    /** Pull every registered lamp out of an explosion's demolition list. */
    private void shieldFromExplosion(List<Block> blocks) {
        blocks.removeIf(block -> store.at(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (movesALamp(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (movesALamp(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean movesALamp(List<Block> blocks) {
        for (Block block : blocks) {
            if (store.at(block) != null) {
                return true;
            }
        }
        return false;
    }

    /** Stop flowing water or lava from washing a placed torch away. */
    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (store.at(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }
}
