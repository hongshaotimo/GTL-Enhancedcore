package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.EmptyBlockGetter;

/** Removes only fixed, full-occlusion casing cells hidden from every exterior 26-neighbor view. */
public final class NativeStructureHollowing {
    private static final int MAX_CELLS = 10_000_000;
    private static final int MAX_REPETITION_VARIANTS = 256;
    private static final long MAX_REPETITION_ANALYSIS_CELLS = 5_000_000L;
    private static final Map<ResourceLocation, Integer> REMOVED = new ConcurrentHashMap<>();

    private NativeStructureHollowing() {}

    public static BlockPattern optimize(ResourceLocation id, BlockPattern original) {
        if (id == null || original == null || id.getNamespace().equals("gtl_enhancedcore")) return original;
        TraceabilityPredicate[][][] source = ((BlockPatternAccessor) original).gtlEnhancedcore$getBlockMatches();
        if (source.length == 0 || original.aisleRepetitions.length != source.length) return original;
        int depth = source.length, height = source[0].length, width = height == 0 ? 0 : source[0][0].length;
        long totalLong = (long) depth * height * width;
        if (width == 0 || height == 0 || totalLong > MAX_CELLS) return original;
        int total = (int) totalLong;

        boolean[] opaque = new boolean[total];
        for (int z = 0; z < depth; z++) {
            if (source[z].length != height) return original;
            for (int y = 0; y < height; y++) {
                if (source[z][y].length != width) return original;
                for (int x = 0; x < width; x++) opaque[index(x, y, z, width, height)] = isFixedOpaque(source[z][y][x]);
            }
        }

        boolean[] remove = hiddenOpaqueAcrossRepetitions(opaque, width, height, depth, original.aisleRepetitions);
        int removed = 0;
        for (boolean cell : remove) if (cell) removed++;
        if (removed == 0) {
            REMOVED.putIfAbsent(id, 0);
            return original;
        }

        TraceabilityPredicate[][][] optimized = source.clone();
        for (int z = 0; z < depth; z++) {
            optimized[z] = source[z].clone();
            for (int y = 0; y < height; y++) optimized[z][y] = source[z][y].clone();
        }
        for (int z = 0; z < depth; z++) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (remove[index(x, y, z, width, height)]) optimized[z][y][x] = Predicates.any();
        }
        REMOVED.put(id, removed);
        return new BlockPattern(optimized, original.structureDir, original.aisleRepetitions,
                ((BlockPatternAccessor) original).enhanced$getCenterOffset().clone());
    }

    public static Map<ResourceLocation, Integer> removedCellsByMachine() {
        return Map.copyOf(REMOVED);
    }

    public static int[] minimumRepetitions(int[][] aisleRepetitions) {
        int[] minimums = new int[aisleRepetitions.length];
        for (int aisle = 0; aisle < aisleRepetitions.length; aisle++) {
            int[] range = aisleRepetitions[aisle];
            if (range == null || range.length < 2 || range[0] < 1 || range[1] < range[0])
                throw new IllegalArgumentException("Invalid aisle repetition range");
            minimums[aisle] = range[0];
        }
        return minimums;
    }

    public static boolean[] hiddenOpaque(boolean[] opaque, int width, int height, int depth) {
        long totalLong = (long) width * height * depth;
        if (width <= 0 || height <= 0 || depth <= 0 || totalLong != opaque.length || totalLong > MAX_CELLS)
            throw new IllegalArgumentException("Invalid structure dimensions");
        int total = opaque.length;
        boolean[] exterior = new boolean[total];
        int[] queue = new int[total];
        int tail = 0;
        for (int z = 0; z < depth; z++) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if (!onBoundary(x, y, z, width, height, depth)) continue;
            int cell = index(x, y, z, width, height);
            if (!opaque[cell] && !exterior[cell]) {
                exterior[cell] = true;
                queue[tail++] = cell;
            }
        }
        for (int head = 0; head < tail; head++) {
            int cell = queue[head];
            int z = cell / (width * height);
            int rem = cell - z * width * height;
            int y = rem / width;
            int x = rem - y * width;
            for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                int nx = x + dx, ny = y + dy, nz = z + dz;
                if (nx < 0 || nx >= width || ny < 0 || ny >= height || nz < 0 || nz >= depth) continue;
                int next = index(nx, ny, nz, width, height);
                if (!opaque[next] && !exterior[next]) {
                    exterior[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        boolean[] hidden = new boolean[total];
        for (int z = 1; z < depth - 1; z++) for (int y = 1; y < height - 1; y++)
            for (int x = 1; x < width - 1; x++) {
                int cell = index(x, y, z, width, height);
                if (!opaque[cell]) continue;
                boolean exposed = false;
                for (int dz = -1; dz <= 1 && !exposed; dz++) for (int dy = -1; dy <= 1 && !exposed; dy++)
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (exterior[index(x + dx, y + dy, z + dz, width, height)]) {
                            exposed = true;
                            break;
                        }
                    }
                hidden[cell] = !exposed;
            }
        return hidden;
    }

    public static boolean[] hiddenOpaqueAcrossRepetitions(boolean[] opaque, int width, int height, int depth,
                                                           int[][] repetitions) {
        long baseCells = (long) width * height * depth;
        if (width <= 0 || height <= 0 || depth <= 0 || baseCells != opaque.length || repetitions.length != depth)
            throw new IllegalArgumentException("Invalid repeated structure dimensions");
        long variants = 1;
        boolean repeated = false;
        for (int[] range : repetitions) {
            if (range == null || range.length < 2 || range[0] < 1 || range[1] < range[0])
                throw new IllegalArgumentException("Invalid aisle repetition range");
            long count = (long) range[1] - range[0] + 1;
            if (count > MAX_REPETITION_VARIANTS / variants) return new boolean[opaque.length];
            variants *= count;
            repeated |= range[0] != 1 || range[1] != 1;
        }
        if (!repeated) return hiddenOpaque(opaque, width, height, depth);

        boolean[] hiddenInEveryLayout = new boolean[opaque.length];
        Arrays.fill(hiddenInEveryLayout, true);
        long[] analyzedCells = {0};
        analyzeRepetitions(opaque, width, height, depth, repetitions, new int[depth], 0,
                hiddenInEveryLayout, analyzedCells);
        if (analyzedCells[0] < 0) return new boolean[opaque.length];
        return hiddenInEveryLayout;
    }

    private static void analyzeRepetitions(boolean[] opaque, int width, int height, int depth, int[][] ranges,
                                            int[] counts, int aisle, boolean[] hiddenInEveryLayout,
                                            long[] analyzedCells) {
        if (analyzedCells[0] < 0) return;
        if (aisle < depth) {
            for (int count = ranges[aisle][0]; count <= ranges[aisle][1]; count++) {
                counts[aisle] = count;
                analyzeRepetitions(opaque, width, height, depth, ranges, counts, aisle + 1,
                        hiddenInEveryLayout, analyzedCells);
                if (analyzedCells[0] < 0) return;
            }
            return;
        }

        long expandedDepthLong = 0;
        for (int count : counts) expandedDepthLong += count;
        long expandedCellsLong = (long) width * height * expandedDepthLong;
        analyzedCells[0] += expandedCellsLong;
        if (expandedCellsLong > MAX_CELLS || analyzedCells[0] > MAX_REPETITION_ANALYSIS_CELLS) {
            analyzedCells[0] = -1;
            return;
        }

        int expandedDepth = (int) expandedDepthLong;
        boolean[] expandedOpaque = new boolean[(int) expandedCellsLong];
        int[] starts = new int[depth];
        int offset = 0;
        for (int z = 0; z < depth; z++) {
            starts[z] = offset;
            for (int copy = 0; copy < counts[z]; copy++) {
                System.arraycopy(opaque, z * width * height, expandedOpaque,
                        (offset + copy) * width * height, width * height);
            }
            offset += counts[z];
        }
        boolean[] visible = hiddenOpaque(expandedOpaque, width, height, expandedDepth);
        for (int z = 0; z < depth; z++) for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int base = index(x, y, z, width, height);
            if (!hiddenInEveryLayout[base]) continue;
            for (int copy = 0; copy < counts[z]; copy++) {
                if (!visible[index(x, y, starts[z] + copy, width, height)]) {
                    hiddenInEveryLayout[base] = false;
                    break;
                }
            }
        }
    }

    private static boolean isFixedOpaque(TraceabilityPredicate predicate) {
        if (predicate == null || predicate.isController || predicate.common.size() != 1 || !predicate.limited.isEmpty())
            return false;
        SimplePredicate simple = predicate.common.getFirst();
        if (simple.getClass() != PredicateBlocks.class || simple.minCount != -1 || simple.maxCount != -1
                || simple.minLayerCount != -1 || simple.maxLayerCount != -1 || simple.previewCount != -1
                || simple.disableRenderFormed || simple.io != IO.BOTH || simple.slotName != null || simple.nbtParser != null)
            return false;
        Block[] blocks = ((PredicateBlocks) simple).blocks;
        if (blocks == null || blocks.length != 1) return false;
        try {
            BlockInfo[] candidates = simple.candidates == null ? null : simple.candidates.get();
            if (candidates == null || candidates.length != 1 || candidates[0] == null || candidates[0].hasBlockEntity())
                return false;
            BlockState state = candidates[0].getBlockState();
            if (state.getBlock() != blocks[0] || !state.canOcclude()) return false;
            return Block.isShapeFullBlock(state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean onBoundary(int x, int y, int z, int width, int height, int depth) {
        return x == 0 || x == width - 1 || y == 0 || y == height - 1 || z == 0 || z == depth - 1;
    }

    private static int index(int x, int y, int z, int width, int height) {
        return (z * height + y) * width + x;
    }
}
