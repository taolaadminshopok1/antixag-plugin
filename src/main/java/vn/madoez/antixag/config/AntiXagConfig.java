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
package vn.madoez.antixag.config;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Root configuration holder for AntiXag.
 */
public final class AntiXagConfig {

    /** Default list of ores that are concealed out of the box. */
    public static final List<String> DEFAULT_HIDDEN_BLOCKS = List.of(
            "COAL_ORE", "DEEPSLATE_COAL_ORE",
            "COPPER_ORE", "DEEPSLATE_COPPER_ORE",
            "IRON_ORE", "DEEPSLATE_IRON_ORE",
            "GOLD_ORE", "DEEPSLATE_GOLD_ORE",
            "NETHER_GOLD_ORE",
            "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE",
            "LAPIS_ORE", "DEEPSLATE_LAPIS_ORE",
            "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE",
            "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE",
            "ANCIENT_DEBRIS"
    );

    private boolean debug;
    private String language = "en";
    private int autosaveSeconds = 60;
    private boolean restoreOnUnload = true;
    private boolean hidePlacedBlocks = true;
    private boolean protectFromExplosions = true;
    private boolean hideOnlyInSurvival = false;
    private Set<String> excludedWorlds = new HashSet<>();
    private WorldSettings defaults = WorldSettings.defaults();
    private final Map<String, WorldSettings> worldOverrides = new HashMap<>();

    public void load(FileConfiguration config) {
        debug = config.getBoolean("settings.debug", false);
        language = config.getString("settings.language", "en").toLowerCase(Locale.ROOT);
        autosaveSeconds = Math.max(0, config.getInt("settings.autosave-seconds", 60));
        restoreOnUnload = config.getBoolean("settings.restore-on-unload", true);
        hidePlacedBlocks = config.getBoolean("settings.hide-placed-blocks", true);
        protectFromExplosions = config.getBoolean("settings.protect-from-explosions", true);
        hideOnlyInSurvival = config.getBoolean("settings.hide-only-in-survival", false);
        excludedWorlds = new HashSet<>(config.getStringList("settings.excluded-worlds"));

        defaults = readWorldSettings(config.getConfigurationSection("defaults"), defaults);
        worldOverrides.clear();
        ConfigurationSection worlds = config.getConfigurationSection("worlds");
        if (worlds != null) {
            for (String key : worlds.getKeys(false)) {
                ConfigurationSection section = worlds.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                WorldSettings resolved = readWorldSettings(section, defaults);
                worldOverrides.put(key.toLowerCase(Locale.ROOT), resolved);
            }
        }
    }

    private WorldSettings readWorldSettings(ConfigurationSection section, WorldSettings fallback) {
        if (section == null) {
            return fallback;
        }
        boolean enabled = section.getBoolean("enabled", fallback.enabled());
        WorldSettings.HideMode hideMode = fallback.hideMode();
        WorldSettings.RevealMode revealMode = fallback.revealMode();
        boolean randomize = section.getBoolean("camouflage.randomize", fallback.randomizeCamouflage());
        boolean placed = section.getBoolean("hide-placed-blocks", fallback.hidePlacedBlocks());
        boolean restore = section.getBoolean("restore-on-unload", fallback.restoreOnUnload());

        String rawHide = section.getString("hide-mode");
        if (rawHide != null) {
            try {
                hideMode = WorldSettings.HideMode.valueOf(rawHide.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // keep fallback
            }
        }
        String rawReveal = section.getString("reveal-mode");
        if (rawReveal != null) {
            try {
                revealMode = WorldSettings.RevealMode.valueOf(rawReveal.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // keep fallback
            }
        }

        Set<Material> hidden = new LinkedHashSet<>();
        List<String> rawHidden = section.getStringList("hidden-blocks");
        if (!rawHidden.isEmpty()) {
            for (String name : rawHidden) {
                Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
                if (material != null) {
                    hidden.add(material);
                }
            }
        } else {
            hidden.addAll(fallback.hiddenBlocks());
        }

        ConfigurationSection camouflage = section.getConfigurationSection("camouflage");
        List<Material> overworld = readPalette(camouflage, "overworld", fallback.overworldPalette());
        List<Material> deepslate = readPalette(camouflage, "deepslate", fallback.deepslatePalette());
        List<Material> nether = readPalette(camouflage, "nether", fallback.netherPalette());
        List<Material> end = readPalette(camouflage, "end", fallback.endPalette());

        return new WorldSettings(enabled, hidden, hideMode, revealMode, randomize, placed, restore,
                overworld, deepslate, nether, end);
    }

    private List<Material> readPalette(ConfigurationSection camouflage, String key, List<Material> fallback) {
        if (camouflage == null) {
            return fallback;
        }
        List<String> raw = camouflage.getStringList(key);
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        List<Material> result = new ArrayList<>();
        for (String name : raw) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material != null && material.isBlock()) {
                result.add(material);
            }
        }
        return result.isEmpty() ? fallback : result;
    }

    /**
     * Resolves the effective settings for a world, taking excluded worlds and
     * per-world overrides into account.
     *
     * @param world the world to resolve
     * @return the effective settings or {@code null} when the world is excluded
     */
    public WorldSettings settingsFor(World world) {
        if (world == null || excludedWorlds.contains(world.getName().toLowerCase(Locale.ROOT))) {
            return null;
        }
        WorldSettings override = worldOverrides.get(world.getName().toLowerCase(Locale.ROOT));
        return override != null ? override : defaults;
    }

    public boolean debug() {
        return debug;
    }

    public String language() {
        return language;
    }

    public int autosaveSeconds() {
        return autosaveSeconds;
    }

    public boolean restoreOnUnload() {
        return restoreOnUnload;
    }

    public boolean hidePlacedBlocks() {
        return hidePlacedBlocks;
    }

    public boolean protectFromExplosions() {
        return protectFromExplosions;
    }

    public boolean hideOnlyInSurvival() {
        return hideOnlyInSurvival;
    }

    public Set<String> excludedWorlds() {
        return excludedWorlds;
    }

    public WorldSettings defaults() {
        return defaults;
    }

    /**
     * Builds the effective default ore set from the built-in list.
     *
     * @return immutable set of default hidden materials
     */
    public static Set<Material> defaultHiddenBlocks() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : DEFAULT_HIDDEN_BLOCKS) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }
}
