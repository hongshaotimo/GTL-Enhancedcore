package com.gtl.enhancedcore.client.preview;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** Index only the four effect markers, not every block in the shared JEI world. */
public final class PreviewEffectAnchors {
    private static final Map<TrackedDummyWorld, Anchors> WORLDS = new WeakHashMap<>();
    private static final ResourceLocation MAGIC = new ResourceLocation("kubejs:magic_core");

    private PreviewEffectAnchors() {}

    public static synchronized void put(TrackedDummyWorld world, BlockPos pos, BlockInfo info) {
        if (info.getBlockState().isAir()) return;
        replace(world, pos, info.getBlockState());
    }

    public static synchronized void replace(TrackedDummyWorld world, BlockPos pos, BlockState state) {
        boolean marker = state.is(ForgeRegistries.BLOCKS.getValue(MAGIC));
        var anchors = WORLDS.get(world);
        if (anchors == null && !marker) return;
        if (anchors == null) {
            anchors = new Anchors();
            WORLDS.put(world, anchors);
        }
        if (marker ? anchors.positions.add(pos.asLong()) : anchors.positions.remove(pos.asLong())) anchors.revision++;
    }

    public static synchronized void remove(TrackedDummyWorld world, BlockPos pos) {
        var anchors = WORLDS.get(world);
        if (anchors != null && anchors.positions.remove(pos.asLong())) anchors.revision++;
    }

    public static synchronized void clear(TrackedDummyWorld world) {
        var anchors = WORLDS.get(world);
        if (anchors != null) {
            anchors.positions.clear();
            anchors.revision++;
        }
    }

    public static synchronized long revision(TrackedDummyWorld world) {
        var anchors = WORLDS.get(world);
        return anchors == null ? 0 : anchors.revision;
    }

    public static synchronized long[] positions(TrackedDummyWorld world) {
        var anchors = WORLDS.get(world);
        return anchors == null ? new long[0] : anchors.positions.toLongArray();
    }

    private static final class Anchors {
        final LongOpenHashSet positions = new LongOpenHashSet();
        long revision;
    }
}
