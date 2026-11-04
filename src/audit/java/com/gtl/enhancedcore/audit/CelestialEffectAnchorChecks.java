package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph;
import com.gtladd.gtladditions.utils.CommonUtils;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Exercises actual transformed renderer translations and the server collection method, without a world. */
public final class CelestialEffectAnchorChecks {
    private CelestialEffectAnchorChecks() {}

    public static void run(List<String> checks) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        for (String id : List.of("gtceu:eye_of_harmony", "gtladditions:arcanic_astrograph")) {
            var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
            boolean eye = id.endsWith("eye_of_harmony");
            for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                var state = definition.defaultBlockState().setValue(
                        ((MetaMachineBlock) definition.getBlock()).rotationState.property, facing);
                var entity = definition.getBlockEntityType().create(new BlockPos(17, 23, -31), state);
                var machine = (WorkableMultiblockMachine) ((IMachineBlockEntity) entity).getMetaMachine();
                var world = (AuditLevel) unsafe.allocateInstance(AuditLevel.class);
                world.state = state;
                world.entity = entity;
                ObfuscationReflectionHelper.findField(Level.class, "f_46443_").setBoolean(world, true);
                entity.setLevel(world);
                try {
                    var logic = machine.getRecipeLogic();
                    var active = RecipeLogic.class.getDeclaredField("isActive");
                    active.setAccessible(true);
                    active.setBoolean(logic, true);
                    // In GTCEu 1.4.4 the private isActive field only matters while
                    // SUSPENDed; an IDLE logic remains inactive even when it is true.
                    var status = RecipeLogic.class.getDeclaredField("status");
                    status.setAccessible(true);
                    status.set(logic, RecipeLogic.Status.WORKING);
                    var formed = MultiblockControllerMachine.class.getDeclaredField("isFormed");
                    formed.setAccessible(true);
                    formed.setBoolean(machine, true);
                    String gates = id + " " + facing + " renderer=" + definition.getRenderer().getClass().getName()
                            + " controller=" + machine.getClass().getName() + " logic=" + logic.getClass().getName()
                            + " status=" + logic.getStatus() + " activeField=" + active.getBoolean(logic)
                            + " logicActive=" + logic.isActive() + " machineActive=" + machine.isActive()
                            + " working=" + logic.isWorking() + " enabled=" + logic.isWorkingEnabled()
                            + " formed=" + machine.isFormed() + " remote=" + machine.isRemote()
                            + " front=" + machine.getFrontFacing() + " up=" + machine.getUpwardsFacing()
                            + " flipped=" + machine.isFlipped();
                    checks.add("Celestial renderer gates: " + gates);
                    require(machine.isActive() && logic.isWorking() && machine.isFormed()
                                    && machine.getFrontFacing() == facing,
                            "Celestial fixture reaches native active gate: " + gates, checks);
                    int depth = eye ? -91 : -75;
                    int height = eye ? -55 : 103;
                    var expected = new Vec3(facing.getStepX() * depth + .5, height + .5,
                            facing.getStepZ() * depth + .5);
                    var anchor = eye ? CelestialEffectAnchors.eye(machine) : CelestialEffectAnchors.astrograph(machine);
                    require(anchor.distanceTo(expected) < 1E-9, id + " blueprint center " + facing, checks);
                    var pose = new CapturePose();
                    try {
                        ((WorkableCasingMachineRenderer) definition.getRenderer()).render(entity, 0, pose,
                                Minecraft.getInstance().renderBuffers().bufferSource(), 0xF000F0, 0);
                        throw new AssertionError("Actual renderer never reached its effect translation: " + gates);
                    } catch (TranslationCaptured captured) {
                        require(pose.center.distanceTo(expected) < 1E-9,
                                id + " transformed real renderer center " + facing, checks);
                    }
                    if (!eye) {
                        var trait = ((ArcanicAstrograph) machine).getAstralArrayCompression();
                        var method = trait.getClass().getDeclaredMethod("getCompressionCenter");
                        method.setAccessible(true);
                        var collected = (Vec3) method.invoke(trait);
                        require(collected.distanceTo(Vec3.atLowerCornerOf(entity.getBlockPos()).add(expected)) < 1E-9,
                                id + " transformed real collection center matches renderer " + facing, checks);
                    }
                } finally {
                    entity.setLevel(null);
                }
            }
        }
        // The heart deliberately retains ADD's original offset and rendering implementation.
        for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var actual = CommonUtils.INSTANCE.getRotatedRenderPosition(Direction.SOUTH, facing, 0, 36, -39);
            var expected = new Vec3(-39 * facing.getStepX() + .5, 36.5, -39 * facing.getStepZ() + .5);
            require(actual.distanceTo(expected) < 1E-9, "heart_of_the_universe native center " + facing, checks);
        }
    }

    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label);
        checks.add(label);
    }

    private static final class CapturePose extends PoseStack {
        Vec3 center;
        @Override public void translate(double x, double y, double z) {
            center = new Vec3(x, y, z);
            throw new TranslationCaptured();
        }
    }

    private static final class TranslationCaptured extends RuntimeException {
        TranslationCaptured() { super(null, null, false, false); }
    }

    private static final class AuditLevel extends DummyWorld {
        BlockState state;
        BlockEntity entity;
        private AuditLevel() { super(null); }
        @Override public long getGameTime() { return 10_000; }
        @Override public BlockState getBlockState(BlockPos pos) { return state; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return entity; }
    }
}
