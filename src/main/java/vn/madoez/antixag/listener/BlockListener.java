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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        WorldSettings settings = plugin.config().settingsFor(event.getBlock().getWorld());
        if (settings == null || !settings.enabled()) {
            return;
        }
        plugin.revealer().reveal(event.getPlayer().getUniqueId(),
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
    }

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
}
