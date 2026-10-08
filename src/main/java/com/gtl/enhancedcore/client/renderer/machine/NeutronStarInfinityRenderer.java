package com.gtl.enhancedcore.client.renderer.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.renderer.BlackHoleRenderer;
import com.gtl.enhancedcore.client.preview.PreviewEffectAnchors;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.utils.RenderUtil;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Singularity field, preserving the compressor's real-world and JEI anchor contract. */
@OnlyIn(Dist.CLIENT)
public class NeutronStarInfinityRenderer extends WorkableCasingMachineRenderer {

    private static final double CENTER_Z = -83.0D;
    private static final double CENTER_Y = 6.5D;
    private static final ResourceLocation MAGIC_CORE_BLOCK = new ResourceLocation("kubejs", "magic_core");
    private static final int PREVIEW_MAGIC_CORE_COUNT = 4;
    private static final int PREVIEW_SEARCH_RADIUS = 260;
    private static final Map<TrackedDummyWorld, Map<BlockPos, AnchorOffset>> PREVIEW_EFFECT_CACHES =
            new WeakHashMap<>();

    public NeutronStarInfinityRenderer() {
        super(new ResourceLocation("ae2", "block/mysterious_cube_core"),
                com.gregtechceu.gtceu.GTCEu.id("block/multiblock/fusion_reactor"));
    }

    @Override
    public void render(BlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        if (!(blockEntity instanceof IMachineBlockEntity machineBlockEntity)) {
            return;
        }
        MetaMachine metaMachine = machineBlockEntity.getMetaMachine();
        if (!(metaMachine instanceof WorkableElectricMultiblockMachine machine) || !machine.isFormed()) {
            return;
        }

        float tick = RenderUtil.getSmoothTick(machine, partialTicks);

        poseStack.pushPose();
        if (!translateToEffectCenter(poseStack, machine, blockEntity)) {
            poseStack.popPose();
            return;
        }
        try {
            double tilt = Math.toRadians(12.0);
            Vector3f normal = effectDirection(machine, 0, Math.cos(tilt), Math.sin(tilt));
            Vector3f tangent = effectDirection(machine, 1, 0, 0);
            BlackHoleRenderer.render(poseStack, normal, tangent, tick,
                    blockEntity.getLevel() instanceof TrackedDummyWorld);
        } finally {
            poseStack.popPose();
        }
    }

    private static Vector3f effectDirection(WorkableElectricMultiblockMachine machine, double x, double y, double z) {
        double[] direction = toActualRelativeOffset(machine.getPattern(), x, y, z,
                machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
        return new Vector3f((float) direction[0], (float) direction[1], (float) direction[2]);
    }

    /**
     * Uses the same relative-axis transform as GTCEu's BlockPattern. The effect anchor is
     * stored in structure-local coordinates, so front/up/flip rotation must be resolved
     * before applying it to the TESR pose.
     */
    private static boolean translateToEffectCenter(PoseStack poseStack, WorkableElectricMultiblockMachine machine,
                                               BlockEntity blockEntity) {
        double[] offset;
        BlockPattern pattern = machine.getPattern();
        if (blockEntity.getLevel() instanceof TrackedDummyWorld) {
            offset = getPreviewEffectOffset((TrackedDummyWorld) blockEntity.getLevel(), blockEntity.getBlockPos());
            if (offset == null) return false;
        } else {
            offset = toActualRelativeOffset(pattern, 0.0D, CENTER_Y, CENTER_Z,
                    machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
            // Rotate the displacement, then add the controller block's center in world axes.
            for (int i = 0; i < offset.length; i++) offset[i] += 0.5D;
        }
        poseStack.translate(offset[0], offset[1], offset[2]);
        return true;
    }

    private static double[] getPreviewEffectOffset(TrackedDummyWorld world, BlockPos controllerPos) {
        synchronized (PREVIEW_EFFECT_CACHES) {
            Map<BlockPos, AnchorOffset> worldCache = PREVIEW_EFFECT_CACHES.computeIfAbsent(world, ignored -> new HashMap<>());
            long revision = PreviewEffectAnchors.revision(world);
            AnchorOffset cached = worldCache.get(controllerPos);
            if (cached != null && cached.revision() == revision) {
                return cached.offset();
            }

            double centerX = 0.0D;
            double centerY = 0.0D;
            double centerZ = 0.0D;
            int count = 0;
            for (long packed : PreviewEffectAnchors.positions(world)) {
                BlockPos pos = BlockPos.of(packed);
                if (Math.abs(pos.getX() - controllerPos.getX()) > PREVIEW_SEARCH_RADIUS
                        || Math.abs(pos.getY() - controllerPos.getY()) > PREVIEW_SEARCH_RADIUS
                        || Math.abs(pos.getZ() - controllerPos.getZ()) > PREVIEW_SEARCH_RADIUS) {
                    continue;
                }
                BlockState state = world.getBlockState(pos);
                if (!MAGIC_CORE_BLOCK.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()))) {
                    continue;
                }
                centerX += pos.getX() + 0.5D;
                centerY += pos.getY() + 0.5D;
                centerZ += pos.getZ() + 0.5D;
                count++;
            }

            if (count != PREVIEW_MAGIC_CORE_COUNT) {
                GTLEnhancedcore.LOGGER.warn("[NeutronStar] JEI preview expected {} magic_core blocks around {}, found {}",
                        PREVIEW_MAGIC_CORE_COUNT, controllerPos, count);
                worldCache.put(controllerPos.immutable(), new AnchorOffset(revision, null));
                return null;
            }

            double[] offset = {
                    centerX / count - controllerPos.getX(),
                    centerY / count - controllerPos.getY() + CENTER_Y,
                    centerZ / count - controllerPos.getZ()
            };
            worldCache.put(controllerPos.immutable(), new AnchorOffset(revision, offset));
            GTLEnhancedcore.LOGGER.debug("[NeutronStar] JEI preview anchor offset {} for controller {}",
                    offset, controllerPos);
            return offset;
        }
    }

    private record AnchorOffset(long revision, double[] offset) {}

    static double[] toActualRelativeOffset(BlockPattern pattern, double x, double y, double z,
                                                  Direction facing, Direction upwardsFacing,
                                                  boolean isFlipped) {
        double[] local = { x, y, z };
        double[] world = new double[3];
        RelativeDirection[] structureDir = pattern.structureDir;

        if (facing == Direction.UP || facing == Direction.DOWN) {
            Direction actualUp = facing == Direction.DOWN ? upwardsFacing : upwardsFacing.getOpposite();
            for (int i = 0; i < 3; i++) {
                setAxis(world, structureDir[i].getActualFacing(actualUp), local[i]);
            }
            int xOffset = upwardsFacing.getStepX();
            int zOffset = upwardsFacing.getStepZ();
            if (xOffset == 0) {
                double tmp = world[2];
                world[2] = zOffset > 0 ? world[1] : -world[1];
                world[1] = zOffset > 0 ? -tmp : tmp;
            } else {
                double tmp = world[0];
                world[0] = xOffset > 0 ? world[1] : -world[1];
                world[1] = xOffset > 0 ? -tmp : tmp;
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    world[0] = -world[0];
                } else {
                    world[2] = -world[2];
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                setAxis(world, structureDir[i].getActualFacing(facing), local[i]);
            }
            if (upwardsFacing == Direction.WEST || upwardsFacing == Direction.EAST) {
                int xOffset = upwardsFacing == Direction.EAST
                        ? facing.getClockWise().getStepX()
                        : facing.getClockWise().getOpposite().getStepX();
                int zOffset = upwardsFacing == Direction.EAST
                        ? facing.getClockWise().getStepZ()
                        : facing.getClockWise().getOpposite().getStepZ();
                if (xOffset == 0) {
                    double tmp = world[2];
                    world[2] = zOffset > 0 ? -world[1] : world[1];
                    world[1] = zOffset > 0 ? tmp : -tmp;
                } else {
                    double tmp = world[0];
                    world[0] = xOffset > 0 ? -world[1] : world[1];
                    world[1] = xOffset > 0 ? tmp : -tmp;
                }
            } else if (upwardsFacing == Direction.SOUTH) {
                world[1] = -world[1];
                if (facing.getStepX() == 0) {
                    world[0] = -world[0];
                } else {
                    world[2] = -world[2];
                }
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                        world[0] = -world[0];
                    } else {
                        world[2] = -world[2];
                    }
                } else {
                    world[1] = -world[1];
                }
            }
        }
        return world;
    }

    private static void setAxis(double[] result, Direction direction, double value) {
        switch (direction) {
            case UP -> result[1] = value;
            case DOWN -> result[1] = -value;
            case WEST -> result[0] = -value;
            case EAST -> result[0] = value;
            case NORTH -> result[2] = -value;
            case SOUTH -> result[2] = value;
        }
    }

    @Override
    public boolean hasTESR(BlockEntity blockEntity) {
        return true;
    }

    @Override
    public boolean isGlobalRenderer(BlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }
}
