package com.gtl.enhancedcore.common.world.plot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Flat terrain generated during the standard chunk pipeline, with the complete recipe in level.dat. */
public final class PlotChunkGenerator extends ChunkGenerator {
    public static final Codec<PlotSettings> SETTINGS_CODEC = PlotSettingsCodec.CODEC;
    public static final Codec<PlotChunkGenerator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome),
            SETTINGS_CODEC.fieldOf("settings").forGetter(PlotChunkGenerator::settings)
    ).apply(instance, PlotChunkGenerator::new));

    private final Holder<Biome> biome;
    private final PlotSettings settings;
    private final Map<String, BlockState> palette;

    public PlotChunkGenerator(Holder<Biome> biome, PlotSettings settings) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.settings = settings;
        this.palette = Stream.of(settings.groundBlock(), settings.plotEdgeBlock(), settings.roadBlock(), settings.borderBlock(), settings.markerBlock(), settings.lineBlock())
                .distinct().collect(Collectors.toUnmodifiableMap(id -> id, PlotChunkGenerator::resolveMaterial));
    }

    /** Configuration only accepts stable, solid, non-ticking material cubes. */
    public static BlockState resolveMaterial(String id) {
        ResourceLocation key = new ResourceLocation(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) throw new IllegalArgumentException("Unknown plot material: " + id);
        Block block = BuiltInRegistries.BLOCK.get(key);
        BlockState state = block.defaultBlockState();
        if (block instanceof EntityBlock || block instanceof FallingBlock || state.isAir()
                || !state.getFluidState().isEmpty() || state.isRandomlyTicking()
                || !Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)))
            throw new IllegalArgumentException("Plot material must be a stable solid cube: " + id);
        return state;
    }

    public PlotSettings settings() { return settings; }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getMinY() { return -64; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return -63; }
    @Override public int getSpawnHeight(LevelHeightAccessor height) { return settings.surfaceY() + 1; }
    private BlockState stateAt(int x, int y, int z) {
        if (y < settings.bedrockY() || y > settings.surfaceY()) return Blocks.AIR.defaultBlockState();
        if (y == settings.bedrockY()) return Blocks.BEDROCK.defaultBlockState();
        return palette.get(y == settings.surfaceY() ? PlotLayout.surfaceBlock(settings, x, z) : settings.groundBlock());
    }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
            RandomState random, StructureManager structures, ChunkAccess chunk) {
        int minY = Math.max(settings.bedrockY(), chunk.getMinBuildHeight());
        int maxY = Math.min(settings.surfaceY(), chunk.getMaxBuildHeight() - 1);
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int originX = chunk.getPos().getMinBlockX(), originZ = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            for (int y = minY; y <= maxY; y++) {
                BlockState state = stateAt(originX + x, y, originZ + z);
                chunk.setBlockState(position.set(originX + x, y, originZ + z), state, false);
                ocean.update(x, y, z, state);
                surface.update(x, y, z, state);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return Math.min(settings.surfaceY() + 1, height.getMaxBuildHeight());
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        int minY = height.getMinBuildHeight();
        BlockState[] column = new BlockState[height.getHeight()];
        for (int index = 0; index < column.length; index++) column[index] = stateAt(x, minY + index, z);
        return new NoiseColumn(minY, column);
    }
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving carving) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}
    @Override public void createStructures(RegistryAccess access, ChunkGeneratorStructureState state,
            StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {}
    @Override public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos position) {
        lines.add("Plot " + settings.plotChunks() + "x" + settings.plotChunks() + " / " + settings.roadStyle());
    }
}
