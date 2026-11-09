/*
 * AntiXag - Anti X-Ray protection for Paper/Spigot servers.
 * Copyright (C) 2026 Madoez Vn, BakiCode, Alexander Vn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package vn.madoez.antixag.listener;

import vn.madoez.antixag.AntiXagPlugin;
import vn.madoez.antixag.config.WorldSettings;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.List;

/**
 * Reacts on the block events of the server.
 *
 * <p>The listener keeps track of the ores that become visible because a
 * player dug into them. Those ores are revealed to the player that caused
 * the change, so that mining still feels natural.</p>
 */
public final class BlockListener implements Listener {

    private final AntiXagPlugin plugin;

    public BlockListener(AntiXagPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Reveals the ores that were exposed by the broken block.
     *
     * <p>The work is delayed by one tick. At the moment the break event is
     * fired the block is not yet removed from the world, so the neighbour
     * ores would still look buried.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        WorldSettings settings = plugin.config().settingsFor(event.getBlock().getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                plugin.revealer().revealAround(player, x, y, z);
            } catch (RuntimeException exception) {
                plugin.debug("Unable to reveal the ores around " + x + "/" + y + "/" + z
                        + ": " + exception.getMessage());
            }
        });
    }

    /**
     * Hides the ores that were placed by a player.
     *
     * <p>A placed ore would reveal itself through the block change packet.
     * The packet adapter takes care of that, so this handler only exists to
     * log the event while the debug mode is enabled.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        WorldSettings settings = plugin.config().settingsFor(event.getBlock().getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        if (!settings.hidePlacedBlocks()) {
            return;
        }
        if (!settings.hiddenBlocks().contains(event.getBlock().getType())) {
            return;
        }
        plugin.debug("A hidden block was placed at " + event.getBlock().getX() + "/"
                + event.getBlock().getY() + "/" + event.getBlock().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        handlePiston(event.getBlocks());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        handlePiston(event.getBlocks());
    }

    private void handlePiston(List<Block> blocks) {
        for (Block block : blocks) {
            WorldSettings settings = plugin.config().settingsFor(block.getWorld());
            if (settings == null || !settings.enabled()) {
                continue;
            }
            if (settings.hiddenBlocks().contains(block.getType())) {
                plugin.debug("A piston moved a hidden block in " + block.getWorld().getName());
            }
        }
    }

    /**
     * Prevents explosions from exposing the ores around the blast.
     *
     * <p>The block change packets of the destroyed blocks are rewritten by the
     * packet adapter anyway. This handler makes sure that the ores that were
     * already exposed by the blast are hidden again for the players that did
     * not trigger it.</p>
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.config().protectFromExplosions()) {
            return;
        }
        WorldSettings settings = plugin.config().settingsFor(event.getEntity().getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        plugin.debug("An explosion changed " + event.blockList().size()
                + " blocks in " + event.getEntity().getWorld().getName());
    }

    /**
     * Hides the ores that are exposed by a burning block.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        WorldSettings settings = plugin.config().settingsFor(event.getBlock().getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        plugin.debug("A block burned at " + event.getBlock().getX() + "/"
                + event.getBlock().getY() + "/" + event.getBlock().getZ());
    }

    /**
     * Handles the ores that are exposed by a flowing liquid.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        WorldSettings settings = plugin.config().settingsFor(event.getBlock().getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        if (!settings.hiddenBlocks().contains(event.getToBlock().getType())) {
            return;
        }
        plugin.debug("A liquid reached a hidden block at " + event.getToBlock().getX() + "/"
                + event.getToBlock().getY() + "/" + event.getToBlock().getZ());
    }

    /**
     * Handles the ores that are exposed by an entity, for example a falling
     * block.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        WorldSettings settings = plugin.config().settingsFor(event.getBlock().getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        if (!settings.hiddenBlocks().contains(event.getBlock().getType())) {
            return;
        }
        plugin.debug("An entity changed a hidden block at " + event.getBlock().getX() + "/"
                + event.getBlock().getY() + "/" + event.getBlock().getZ());
    }
}