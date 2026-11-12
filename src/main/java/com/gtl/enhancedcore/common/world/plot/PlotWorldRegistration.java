package com.gtl.enhancedcore.common.world.plot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Called during mod registration and during new-world setup, never against a live server registry. */
public final class PlotWorldRegistration {
    public static final String MOD_ID = "gtl_enhancedcore";
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> PLOT_GENERATOR =
            GENERATORS.register("plot", () -> PlotChunkGenerator.CODEC);

    private PlotWorldRegistration() {}
    public static void register(IEventBus bus) { GENERATORS.register(bus); }

    public static WorldDimensions withPlotOverworld(RegistryAccess access, WorldDimensions dimensions, PlotSettings settings) {
        if (!settings.isAvailableForCreation()) throw new IllegalArgumentException("Retired campus preset: " + settings.campusPresetId());
        return new WorldDimensions(WorldDimensions.withOverworld(dimensions.dimensions(),
                access.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD),
                new PlotChunkGenerator(access.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS), settings)));
    }
}
