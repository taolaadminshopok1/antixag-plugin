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
import vn.madoez.antixag.protocol.BlockStateRegistry;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Rewrites the batched block change packets.
 *
 * <p>Explosions, pistons and a few other mechanics do not send a single
 * block change per block. Instead they send one packet that carries a list
 * of blocks. Without this adapter the ores around a blast would be revealed
 * to every x-ray client.</p>
 */
public final class MultiBlockChangeAdapter extends PacketAdapter {

    private final AntiXagPlugin plugin;
    private final BlockStateRegistry registry;

    public MultiBlockChangeAdapter(AntiXagPlugin plugin, BlockStateRegistry registry) {
        super(plugin, ListenerPriority.LOW, PacketType.Play.Server.MULTI_BLOCK_CHANGE);
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public void onPacketSending(PacketEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        World world = player.getWorld();
        WorldSettings settings = plugin.config().settingsFor(world);
        if (settings == null || !settings.enabled()) {
            return;
        }
        if (plugin.config().hideOnlyInSurvival() && player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (player.hasPermission("antixag.bypass")) {
            return;
        }

        PacketContainer packet = event.getPacket();

        MultiBlockChangeInfo[] records;
        try {
            records = packet.getMultiBlockChangeInfoArrays().read(0);
        } catch (RuntimeException exception) {
            return;
        }
        if (records == null || records.length == 0) {
            return;
        }

        int rewritten = 0;
        for (MultiBlockChangeInfo record : records) {
            if (record == null) {
                continue;
            }
            WrappedBlockData data;
            try {
                data = record.getData();
            } catch (RuntimeException exception) {
                continue;
            }
            if (data == null) {
                continue;
            }
            Material material = data.getType();
            if (!settings.hiddenBlocks().contains(material)) {
                continue;
            }
            Material camouflage = plugin.obfuscator().camouflageFor(settings,
                    world.getEnvironment(), record.getY());
            if (registry.idOf(camouflage) < 0) {
                continue;
            }
            try {
                record.setData(WrappedBlockData.createData(camouflage));
                rewritten++;
            } catch (RuntimeException exception) {
                plugin.debug("Unable to replace a multi block change entry: "
                        + exception.getMessage());
            }
        }

        if (rewritten > 0) {
            try {
                packet.getMultiBlockChangeInfoArrays().write(0, records);
            } catch (RuntimeException exception) {
                plugin.debug("Unable to write the multi block change packet: "
                        + exception.getMessage());
                return;
            }
            plugin.countOres(rewritten);
            if (plugin.config().debug()) {
                plugin.debug("Hid " + rewritten + " ores in a multi block change for "
                        + player.getName());
            }
        }
    }
}