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
package vn.madoez.antixag.protocol;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedBlockData;

import org.bukkit.Material;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridge between Bukkit materials and the numeric block state ids that are
 * used on the wire.
 *
 * <p>The chunk packet stores block states as plain integers. Those integers
 * are the ids of the global block state registry of the server
 * ({@code net.minecraft.core.IdMapper}). This class resolves the mapping in
 * both directions.</p>
 *
 * <p>The lookup is implemented with plain Java reflection and matches methods
 * by their signature instead of their name. This keeps the plugin working on
 * both obfuscated (Spigot) and Mojang mapped (Paper 1.20.5+) servers without
 * any version specific code.</p>
 */
public final class BlockStateRegistry {

    private final Class<?> blockClass;
    private final Class<?> blockStateClass;
    private final Method stateByIdMethod;
    private final Method getIdMethod;
    private final Method stateGetBlockMethod;
    private final Method magicNumbersGetBlockMethod;
    private final Method magicNumbersGetMaterialMethod;
    private final int registrySize;

    private final Map<Material, Integer> idCache = new HashMap<>();
    private final Map<Integer, Material> materialCache = new HashMap<>();

    private BlockStateRegistry(Class<?> blockClass,
                               Class<?> blockStateClass,
                               Method stateByIdMethod,
                               Method getIdMethod,
                               Method stateGetBlockMethod,
                               Method magicNumbersGetBlockMethod,
                               Method magicNumbersGetMaterialMethod,
                               int registrySize) {
        this.blockClass = blockClass;
        this.blockStateClass = blockStateClass;
        this.stateByIdMethod = stateByIdMethod;
        this.getIdMethod = getIdMethod;
        this.stateGetBlockMethod = stateGetBlockMethod;
        this.magicNumbersGetBlockMethod = magicNumbersGetBlockMethod;
        this.magicNumbersGetMaterialMethod = magicNumbersGetMaterialMethod;
        this.registrySize = registrySize;
    }

    /**
     * Creates a registry that talks to the block state registry of the
     * running server.
     *
     * @throws IllegalStateException when the server internals cannot be found
     */
    public static BlockStateRegistry create() {
        Class<?> blockClass = MinecraftReflection.getBlockClass();
        if (blockClass == null) {
            throw new IllegalStateException("Unable to locate the Minecraft Block class");
        }

        Class<?> blockStateClass;
        try {
            blockStateClass = WrappedBlockData.createData(Material.STONE).getHandle().getClass();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to locate the Minecraft BlockState class", exception);
        }

        Method stateById = findStatic(blockClass,
                method -> method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == int.class
                        && isStateType(method.getReturnType(), blockStateClass),
                "stateById(int)");
        Method getId = findStatic(blockClass,
                method -> method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isAssignableFrom(blockStateClass)
                        && method.getReturnType() == int.class,
                "getId(BlockState)");

        Method stateGetBlock = findInstance(blockStateClass,
                method -> method.getParameterCount() == 0
                        && method.getReturnType() == blockClass,
                "BlockState.getBlock()");

        Class<?> magicNumbers = MinecraftReflection.getCraftBukkitClass("util.CraftMagicNumbers");
        Method magicGetBlock = findStatic(magicNumbers,
                method -> method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Material.class
                        && method.getReturnType() == blockClass,
                "CraftMagicNumbers.getBlock(Material)");
        Method magicGetMaterial = findStatic(magicNumbers,
                method -> method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == blockClass
                        && method.getReturnType() == Material.class,
                "CraftMagicNumbers.getMaterial(Block)");

        int size = resolveRegistrySize(blockClass);

        return new BlockStateRegistry(blockClass, blockStateClass, stateById, getId,
                stateGetBlock, magicGetBlock, magicGetMaterial, size);
    }

    private static boolean isStateType(Class<?> candidate, Class<?> blockStateClass) {
        return candidate == blockStateClass || blockStateClass.isAssignableFrom(candidate);
    }

    private static Method findStatic(Class<?> owner,
                                     java.util.function.Predicate<Method> predicate,
                                     String description) {
        Method fallback = null;
        for (Method method : owner.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!predicate.test(method)) {
                continue;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("Unable to locate the method " + description
                    + " on " + owner.getName());
        }
        fallback.setAccessible(true);
        return fallback;
    }

    private static Method findInstance(Class<?> owner,
                                       java.util.function.Predicate<Method> predicate,
                                       String description) {
        Method fallback = null;
        for (Method method : owner.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (!predicate.test(method)) {
                continue;
            }
            if (fallback == null) {
                fallback = method;
            }
        }
        if (fallback == null) {
            throw new IllegalStateException("Unable to locate the method " + description
                    + " on " + owner.getName());
        }
        fallback.setAccessible(true);
        return fallback;
    }

    /**
     * Resolves the size of the global block state registry.
     *
     * <p>The size is required to compute the amount of bits that are used by
     * the global container variant.</p>
     */
    private static int resolveRegistrySize(Class<?> blockClass) {
        for (Field field : blockClass.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            Class<?> type = field.getType();
            Method sizeMethod;
            try {
                sizeMethod = type.getMethod("size");
            } catch (NoSuchMethodException exception) {
                continue;
            }
            if (sizeMethod.getReturnType() != int.class) {
                continue;
            }
            boolean hasGetId = false;
            for (Method method : type.getMethods()) {
                if (method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Object.class
                        && method.getReturnType() == int.class) {
                    hasGetId = true;
                    break;
                }
            }
            if (!hasGetId) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object mapper = field.get(null);
                if (mapper == null) {
                    continue;
                }
                sizeMethod.setAccessible(true);
                return (Integer) sizeMethod.invoke(mapper);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next candidate.
            }
        }
        throw new IllegalStateException("Unable to determine the size of the block state registry");
    }

    /**
     * Returns the global block state id of the default state of a material.
     *
     * @param material the material to look up
     * @return the protocol id, or {@code -1} when the material is unknown
     */
    public int idOf(Material material) {
        if (material == null) {
            return -1;
        }
        Integer cached = idCache.get(material);
        if (cached != null) {
            return cached;
        }
        int id;
        try {
            Object state = WrappedBlockData.createData(material).getHandle();
            id = ((Integer) getIdMethod.invoke(null, state));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return -1;
        }
        idCache.put(material, id);
        return id;
    }

    /**
     * Returns the material that belongs to a global block state id.
     *
     * @param id the protocol id
     * @return the material, or {@code null} when the id is unknown
     */
    public Material materialOf(int id) {
        Material cached = materialCache.get(id);
        if (cached != null) {
            return cached;
        }
        Material material;
        try {
            Object state = stateByIdMethod.invoke(null, id);
            if (state == null) {
                return null;
            }
            Object block = stateGetBlockMethod.invoke(state);
            material = (Material) magicNumbersGetMaterialMethod.invoke(null, block);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
        if (material != null) {
            materialCache.put(id, material);
        }
        return material;
    }

    /**
     * Returns the size of the global block state registry.
     *
     * @return the number of registered block states
     */
    public int registrySize() {
        return registrySize;
    }

    /**
     * Returns the amount of bits that are required to address a single entry
     * of the global registry.
     *
     * @return the ceiling of the binary logarithm of the registry size
     */
    public int globalBits() {
        return ceilLog2(registrySize);
    }

    /**
     * Computes the ceiling of the binary logarithm.
     *
     * @param value the value to inspect
     * @return {@code ceil(log2(value))}, at least 1
     */
    public static int ceilLog2(int value) {
        if (value <= 1) {
            return 1;
        }
        return 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    /**
     * Returns the class that represents a block state on this server.
     *
     * @return the block state class
     */
    public Class<?> blockStateClass() {
        return blockStateClass;
    }

    /**
     * Returns the class that represents a block on this server.
     *
     * @return the block class
     */
    public Class<?> blockClass() {
        return blockClass;
    }
}
