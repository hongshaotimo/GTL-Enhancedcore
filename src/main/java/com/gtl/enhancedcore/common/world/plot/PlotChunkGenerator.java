package com.gtl.enhancedcore.common.world.plot;

import com.gtl.enhancedcore.common.world.campus.CampusDeviceBlocks;
import com.gtl.enhancedcore.common.world.campus.VirtualMarkerBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StandingSignBlock;
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
import java.util.HashMap;
import java.util.ArrayList;
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
    private final Map<Long, List<CampusSign>> signsByChunk;
    private final Map<Long, Integer> signHeights;
    private final Map<Long, CampusDevice> devicesByColumn;
    private final Map<Long, List<CampusDevice>> devicesByChunk;
    private record CampusSign(BlockPos position, List<Component> lines) {}
    private record CampusDevice(BlockPos position, PlannedCampus.Plot plot) {}

    public PlotChunkGenerator(Holder<Biome> biome, PlotSettings settings) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
        this.settings = settings;
        Stream<String> materials = Stream.of(settings.groundBlock(), settings.plotEdgeBlock(), settings.roadBlock(), settings.borderBlock(), settings.markerBlock(), settings.lineBlock());
        if (settings.isCampus()) materials = Stream.concat(materials, campusMaterials().stream());
        this.palette = materials.distinct().collect(Collectors.toUnmodifiableMap(id -> id,
                id -> settings.isCampus() && campusMaterials().contains(id) ? resolveCampusMaterial(id) : resolveMaterial(id)));
        this.signsByChunk = new HashMap<>(); this.signHeights = new HashMap<>();
        this.devicesByColumn = new HashMap<>(); this.devicesByChunk = new HashMap<>();
        if (settings.isCampus()) {
            for (var plot : campusPlots()) {
                addDevice(plot.controllerX(), plot.controllerY(), plot.controllerZ(), plot);
            }
            addDevice(0, 1, 0, null);
            addSign(0, 1, 18, Component.translatable("gtl_enhancedcore.campus.sign.ae"),
                    Component.translatable("gtl_enhancedcore.campus.sign.ae_space"),
                    Component.translatable("gtl_enhancedcore.campus.sign.gallery"),
                    Component.translatable("gtl_enhancedcore.campus.sign.freedom"));
            for (var workshop : campusWorkshops())
                addSign(workshop.x() + workshop.width() / 2, 1, workshop.z() - 3,
                        Component.translatable("gtl_enhancedcore.campus.sign.shared"),
                        Component.translatable("gtl_enhancedcore.campus.sign.shared_small"),
                        Component.translatable("gtl_enhancedcore.campus.sign.shared_passive"),
                        Component.translatable("gtl_enhancedcore.campus.sign.freedom"));
        }
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
    private java.util.Set<String> campusMaterials() {
        return switch (settings.roadStyle()) {
            case CAMPUS -> LegacyPlannedCampus.materials();
            case CAMPUS_PARK -> ParkPlannedCampus.materials();
            case CAMPUS_COMPACT -> CompactPlannedCampus.materials();
            case CAMPUS_DENSE -> DensePlannedCampus.materials();
            case CAMPUS_WOODLAND -> PlannedCampus.materials();
            default -> java.util.Set.of();
        };
    }
    private List<PlannedCampus.Plot> campusPlots() {
        return switch (settings.roadStyle()) {
            case CAMPUS -> LegacyPlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                    p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                    p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList();
            case CAMPUS_PARK -> ParkPlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                    p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                    p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList();
            case CAMPUS_COMPACT -> CompactPlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                    p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                    p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList();
            case CAMPUS_DENSE -> DensePlannedCampus.plots().stream().map(p -> new PlannedCampus.Plot(
                    p.id(), p.stage(), p.x(), p.z(), p.width(), p.height(), p.depth(), p.controllerX(), p.controllerY(),
                    p.controllerZ(), p.patternResource(), p.slotIndex(), p.theme())).toList();
            case CAMPUS_WOODLAND -> PlannedCampus.plots();
            default -> List.of();
        };
    }
    private List<PlannedCampus.Workshop> campusWorkshops() {
        return switch (settings.roadStyle()) {
            case CAMPUS_PARK -> ParkPlannedCampus.workshops().stream().map(w -> new PlannedCampus.Workshop(
                    w.stage(), w.x(), w.z(), w.width(), w.depth())).toList();
            case CAMPUS_COMPACT -> CompactPlannedCampus.workshops().stream().map(w -> new PlannedCampus.Workshop(
                    w.stage(), w.x(), w.z(), w.width(), w.depth())).toList();
            case CAMPUS_DENSE -> DensePlannedCampus.workshops().stream().map(w -> new PlannedCampus.Workshop(
                    w.stage(), w.x(), w.z(), w.width(), w.depth())).toList();
            case CAMPUS_WOODLAND -> PlannedCampus.workshops();
            default -> List.of();
        };
    }
    private boolean campusTerrain(int x, int z) {
        return switch (settings.roadStyle()) {
            case CAMPUS -> LegacyPlannedCampus.hasTerrain(x, z);
            case CAMPUS_PARK -> ParkPlannedCampus.hasTerrain(x, z);
            case CAMPUS_COMPACT -> CompactPlannedCampus.hasTerrain(x, z);
            case CAMPUS_DENSE -> DensePlannedCampus.hasTerrain(x, z);
            case CAMPUS_WOODLAND -> PlannedCampus.hasTerrain(x, z);
            default -> false;
        };
    }
    private int campusCeiling(int x, int z) {
        int ceiling = switch (settings.roadStyle()) {
            case CAMPUS -> LegacyPlannedCampus.maxDecorationYAt(x, z);
            case CAMPUS_PARK -> ParkPlannedCampus.maxDecorationYAt(settings, x, z);
            case CAMPUS_COMPACT -> CompactPlannedCampus.maxDecorationYAt(settings, x, z);
            case CAMPUS_DENSE -> DensePlannedCampus.maxDecorationYAt(settings, x, z);
            case CAMPUS_WOODLAND -> PlannedCampus.maxDecorationYAt(settings, x, z);
            default -> 0;
        };
        CampusDevice device = devicesByColumn.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
        return device == null ? ceiling : Math.max(ceiling, device.position().getY() - settings.surfaceY());
    }
    private static BlockState resolveCampusMaterial(String id) {
        try {
            BlockState state = net.minecraft.commands.arguments.blocks.BlockStateParser
                    .parseForBlock(BuiltInRegistries.BLOCK.asLookup(), id, false).blockState();
            return state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException("Invalid authored campus material " + id, invalid);
        }
    }
    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getMinY() { return -64; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return -63; }
    @Override public int getSpawnHeight(LevelHeightAccessor height) { return settings.surfaceY() + 1; }
    private BlockState stateAt(int x, int y, int z) {
        CampusDevice device = devicesByColumn.get(net.minecraft.world.level.ChunkPos.asLong(x, z));
        if (device != null && device.position().getY() == y)
            return device.plot() == null ? CampusDeviceBlocks.CENTRAL_TELEPORTER.get().defaultBlockState()
                    : CampusDeviceBlocks.VIRTUAL_MARKER.get().defaultBlockState();
        if (settings.isCampus() && signHeights.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x, z), Integer.MIN_VALUE) == y)
            return Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8);
        String id = PlotLayout.blockIdAt(settings, x, y, z);
        return switch (id) {
            case "minecraft:air" -> Blocks.AIR.defaultBlockState();
            case "minecraft:bedrock" -> Blocks.BEDROCK.defaultBlockState();
            default -> palette.get(id);
        };
    }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
            RandomState random, StructureManager structures, ChunkAccess chunk) {
        int minY = Math.max(settings.bedrockY(), chunk.getMinBuildHeight());
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int originX = chunk.getPos().getMinBlockX(), originZ = chunk.getPos().getMinBlockZ();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            if (settings.isCampus() && !campusTerrain(originX + x, originZ + z)) continue;
            int maxY = Math.min(settings.surfaceY() + (settings.isCampus()
                    ? campusCeiling(originX + x, originZ + z) : 0), chunk.getMaxBuildHeight() - 1);
            for (int y = minY; y <= maxY; y++) {
                BlockState state = stateAt(originX + x, y, originZ + z);
                if (state.isAir()) continue;
                chunk.setBlockState(position.set(originX + x, y, originZ + z), state, false);
                ocean.update(x, y, z, state);
                surface.update(x, y, z, state);
            }
        }
        if (settings.isCampus()) {
            placeCampusSigns(chunk);
            placeCampusDevices(chunk);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** Devices overlay the authored plan only during new-chunk generation; existing blocks are untouched. */
    private void addDevice(int x, int relativeY, int z, PlannedCampus.Plot plot) {
        BlockPos pos = new BlockPos(x, settings.surfaceY() + relativeY, z);
        CampusDevice device = new CampusDevice(pos, plot);
        devicesByColumn.put(net.minecraft.world.level.ChunkPos.asLong(x, z), device);
        devicesByChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4), key -> new ArrayList<>()).add(device);
    }

    private void placeCampusDevices(ChunkAccess chunk) {
        for (CampusDevice device : devicesByChunk.getOrDefault(chunk.getPos().toLong(), List.of())) {
            BlockPos pos = device.position();
            CompoundTag nbt;
            if (device.plot() != null) nbt = VirtualMarkerBlockEntity.initialTag(pos, device.plot());
            else {
                nbt = new CompoundTag();
                nbt.putString("id", CampusDeviceBlocks.CENTRAL_TELEPORTER_BLOCK_ENTITY.getId().toString());
                nbt.putInt("x", pos.getX()); nbt.putInt("y", pos.getY()); nbt.putInt("z", pos.getZ());
            }
            chunk.setBlockEntityNbt(nbt);
        }
    }

    private void placeCampusSigns(ChunkAccess chunk) {
        for (var sign : signsByChunk.getOrDefault(chunk.getPos().toLong(), List.of())) sign(chunk, sign);
    }

    private void addSign(int x, int relativeY, int z, Component... lines) {
        BlockPos pos = new BlockPos(x, settings.surfaceY() + relativeY, z);
        signsByChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(x >> 4, z >> 4), key -> new ArrayList<>())
                .add(new CampusSign(pos, List.of(lines)));
        signHeights.put(net.minecraft.world.level.ChunkPos.asLong(x, z), pos.getY());
    }

    private static void sign(ChunkAccess chunk, CampusSign sign) {
        BlockPos pos = sign.position(); int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        BlockState state = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8);
        chunk.setBlockState(pos, state, false);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG).update(x & 15, y, z & 15, state);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG).update(x & 15, y, z & 15, state);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:sign"); nbt.putInt("x", x); nbt.putInt("y", y); nbt.putInt("z", z);
        nbt.putBoolean("is_waxed", true);
        CompoundTag text = new CompoundTag(); ListTag messages = new ListTag();
        for (Component line : sign.lines()) messages.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        text.put("messages", messages); text.putString("color", "black"); text.putBoolean("has_glowing_text", false);
        nbt.put("front_text", text); nbt.put("back_text", text.copy());
        chunk.setBlockEntityNbt(nbt);
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        if (settings.isCampus() && !campusTerrain(x, z)) return height.getMinBuildHeight();
        int top = settings.surfaceY() + (settings.isCampus() ? campusCeiling(x, z) : 0);
        top = Math.max(top, signHeights.getOrDefault(net.minecraft.world.level.ChunkPos.asLong(x, z), top));
        for (int y = Math.min(top, height.getMaxBuildHeight() - 1); y >= Math.max(settings.bedrockY(), height.getMinBuildHeight()); y--)
            if (type.isOpaque().test(stateAt(x, y, z))) return y + 1;
        return height.getMinBuildHeight();
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
