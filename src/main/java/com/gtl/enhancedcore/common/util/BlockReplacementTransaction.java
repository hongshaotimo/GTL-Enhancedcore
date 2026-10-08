package com.gtl.enhancedcore.common.util;

import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockReplacementTransaction {
    public enum Result { PLACED, PLACED_WITH_WARNING, CANCELED, INSUFFICIENT, FAILED }

    private static final ThreadLocal<Capture> ACTIVE = new ThreadLocal<>();

    private BlockReplacementTransaction() {}

    public static Result replace(ServerLevel level, ServerPlayer player, BlockPos position, BlockState replacement,
                                 ItemStack material, BooleanSupplier charge) {
        if (!level.getServer().isSameThread() || !level.isLoaded(position)
                || level.captureBlockSnapshots || level.restoringBlockSnapshots || ACTIVE.get() != null)
            return Result.FAILED;
        BlockSnapshot original;
        try {
            original = BlockSnapshot.create(level.dimension(), level, position, 3);
        } catch (RuntimeException failure) {
            GTLEnhancedcore.LOGGER.warn("Replacement snapshot failed at {}", position, failure);
            return Result.FAILED;
        }
        int snapshotStart = level.capturedBlockSnapshots.size();
        Capture capture = new Capture(level, position, snapshotStart);
        List<BlockSnapshot> snapshots = null;
        boolean committed = false;
        boolean deferredInitializer = replacement.getBlock() instanceof WitherSkullBlock;
        ACTIVE.set(capture);
        try {
            level.captureBlockSnapshots = true;
            if (!level.setBlock(position, replacement, 3)) return Result.FAILED;
            if (!deferredInitializer) replacement.getBlock().setPlacedBy(level, position, replacement, player, material.copyWithCount(1));
            level.captureBlockSnapshots = false;
            snapshots = captured(level, snapshotStart, original);
            for (BlockSnapshot snapshot : snapshots) capture.positions.add(snapshot.getPos());
            if (level.capturedBlockSnapshots.size() > snapshotStart)
                level.capturedBlockSnapshots.subList(snapshotStart, level.capturedBlockSnapshots.size()).clear();
            boolean canceled = snapshots.size() > 1
                    ? ForgeEventFactory.onMultiBlockPlace(player, snapshots, Direction.UP)
                    : ForgeEventFactory.onBlockPlace(player, original, Direction.UP);
            if (canceled) return Result.CANCELED;
            if (level.getBlockState(position).getBlock() != replacement.getBlock()) return Result.FAILED;
            if (!charge.getAsBoolean()) return Result.INSUFFICIENT;
            committed = true;
            boolean warned = false;
            for (BlockSnapshot snapshot : snapshots) {
                BlockState current = level.getBlockState(snapshot.getPos());
                try {
                    current.onPlace(level, snapshot.getPos(), snapshot.getReplacedBlock(), false);
                } catch (RuntimeException failure) {
                    warned = true;
                    GTLEnhancedcore.LOGGER.warn("Replacement committed at {}; block callback failed without rollback or refund", snapshot.getPos(), failure);
                }
                try {
                    level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
                            snapshot.getReplacedBlock(), level.getBlockState(snapshot.getPos()), snapshot.getFlag(), 512);
                } catch (RuntimeException failure) {
                    warned = true;
                    GTLEnhancedcore.LOGGER.warn("Replacement committed at {}; neighbor notification failed without rollback or refund", snapshot.getPos(), failure);
                }
            }
            if (deferredInitializer) {
                try {
                    replacement.getBlock().setPlacedBy(level, position, replacement, player, material.copyWithCount(1));
                } catch (RuntimeException failure) {
                    warned = true;
                    GTLEnhancedcore.LOGGER.warn("Replacement committed at {}; deferred block initialization failed without refund", position, failure);
                }
            }
            return warned ? Result.PLACED_WITH_WARNING : Result.PLACED;
        } catch (RuntimeException failure) {
            GTLEnhancedcore.LOGGER.warn("Replacement transaction failed at {}", position, failure);
            return committed ? Result.PLACED_WITH_WARNING : Result.FAILED;
        } finally {
            level.captureBlockSnapshots = false;
            try {
                if (!committed) {
                    level.restoringBlockSnapshots = true;
                    if (snapshots == null) snapshots = captured(level, snapshotStart, original);
                    for (int index = snapshots.size() - 1; index >= 0; index--) {
                        try {
                            if (!snapshots.get(index).restore(true, false))
                                GTLEnhancedcore.LOGGER.error("Replacement rollback rejected at {}", snapshots.get(index).getPos());
                        } catch (RuntimeException failure) {
                            GTLEnhancedcore.LOGGER.error("Replacement rollback failed at {}", snapshots.get(index).getPos(), failure);
                        }
                    }
                }
            } finally {
                level.restoringBlockSnapshots = false;
                if (level.capturedBlockSnapshots.size() > snapshotStart)
                    level.capturedBlockSnapshots.subList(snapshotStart, level.capturedBlockSnapshots.size()).clear();
                ACTIVE.remove();
                if (!committed) {
                    for (ItemEntity drop : capture.drops) {
                        try {
                            if (!drop.isRemoved()) drop.discard();
                        } catch (RuntimeException failure) {
                            GTLEnhancedcore.LOGGER.error("Replacement rollback could not remove a staged container drop at {}", position, failure);
                        }
                    }
                }
                if (committed) {
                    for (ItemEntity drop : capture.drops) {
                        try {
                            if (drop.isAddedToWorld() || drop.isRemoved()) continue;
                            level.addFreshEntity(drop);
                        } catch (RuntimeException failure) {
                            GTLEnhancedcore.LOGGER.warn("Replacement container drop failed at {}; trying player inventory", position, failure);
                            if (!drop.isAddedToWorld() && !drop.isRemoved()
                                    && level.getEntity(drop.getUUID()) == null) {
                                ItemStack remaining = drop.getItem().copy();
                                try {
                                    if (!player.getInventory().add(remaining)) player.drop(remaining, false);
                                } catch (RuntimeException fallbackFailure) {
                                    GTLEnhancedcore.LOGGER.error("Replacement container drop fallback failed at {}", position, fallbackFailure);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static List<BlockSnapshot> captured(ServerLevel level, int start, BlockSnapshot original) {
        List<BlockSnapshot> snapshots = new ArrayList<>(level.capturedBlockSnapshots.subList(start, level.capturedBlockSnapshots.size()));
        if (snapshots.stream().noneMatch(snapshot -> snapshot.getPos().equals(original.getPos()))) snapshots.add(original);
        return snapshots;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void capturePendingDrop(EntityJoinLevelEvent event) {
        Capture capture = ACTIVE.get();
        if (capture != null && !event.isCanceled() && event.getLevel() == capture.level
                && event.getEntity() instanceof ItemEntity item && capture.owns(item.blockPosition())) {
            capture.drops.add(item);
            event.setCanceled(true);
        }
    }

    private static final class Capture {
        private final ServerLevel level;
        private final int snapshotStart;
        private final Set<BlockPos> positions = new HashSet<>();
        private final List<ItemEntity> drops = new ArrayList<>();

        private Capture(ServerLevel level, BlockPos position, int snapshotStart) {
            this.level = level;
            this.snapshotStart = snapshotStart;
            positions.add(position.immutable());
        }

        private boolean owns(BlockPos position) {
            if (positions.contains(position)) return true;
            for (int index = snapshotStart; index < level.capturedBlockSnapshots.size(); index++) {
                if (level.capturedBlockSnapshots.get(index).getPos().equals(position)) return true;
            }
            return false;
        }
    }
}
