package com.gtl.enhancedcore.common.assembly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;

/** Collision, fluid and hazard checks for an assembly-manager destination. */
final class AssemblyManagerTeleport {
    private static final int RADIUS = 4;
    private AssemblyManagerTeleport() {}

    static BlockPos find(ServerLevel level, ServerPlayer player, BlockPos target, Direction front) {
        // Only the selected target's immediate surroundings; these are ordinary loads, never forced tickets.
        for (int cx = (target.getX() - RADIUS) >> 4; cx <= (target.getX() + RADIUS) >> 4; cx++)
            for (int cz = (target.getZ() - RADIUS) >> 4; cz <= (target.getZ() + RADIUS) >> 4; cz++)
                level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
        // Try the front face first, retaining the controller's height before nearby height adjustments.
        for (int distance = 1; distance <= RADIUS; distance++) {
            BlockPos candidate = atHeight(level, player, target.relative(front, distance));
            if (candidate != null) return candidate;
        }
        for (int radius = 1; radius <= RADIUS; radius++)
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos candidate = atHeight(level, player, target.offset(dx, 0, dz));
                if (candidate != null) return candidate;
            }
        return null;
    }

    private static BlockPos atHeight(ServerLevel level, ServerPlayer player, BlockPos pos) {
        for (int dy : new int[] {0, 1, -1, 2, -2, 3, -3, 4, -4}) {
            BlockPos feet = pos.offset(0, dy, 0);
            if (safeAt(level, player, feet)) return feet;
        }
        return null;
    }

    static boolean safeAt(ServerLevel level, ServerPlayer player, BlockPos feet) {
        if (feet.getY() < level.getMinBuildHeight() || feet.getY() + 2 > level.getMaxBuildHeight()) return false;
        var box = player.getDimensions(Pose.STANDING).makeBoundingBox(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
        var border = level.getWorldBorder();
        if (box.minX < border.getMinX() || box.maxX > border.getMaxX()
                || box.minZ < border.getMinZ() || box.maxZ > border.getMaxZ()) return false;
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX - 1.0E-7); x++)
            for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ - 1.0E-7); z++) {
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return false;
                if (hazard(level.getBlockState(new BlockPos(x, feet.getY() - 1, z)))) return false;
                for (int y = feet.getY(); y <= Mth.floor(box.maxY - 1.0E-7); y++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (!state.getFluidState().isEmpty() || hazard(state)) return false;
                }
            }
        return level.noCollision(player, box);
    }

    private static boolean hazard(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.END_PORTAL) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_GATEWAY);
    }
}
