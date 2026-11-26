package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.client.renderer.machine.WorkableCasingMachineRenderer;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;

/** Self-contained because the isolated runner packages only this class and its nested classes. */
@Mod("enhancedcore_audit")
public final class CelestialEffectsClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/celestial-vfx-20261107/validation")
            .toAbsolutePath().normalize();
    private static final BlockPos POSITION = new BlockPos(17, 23, -31);
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public CelestialEffectsClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.celestialEffectsAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, Object>();
        try {
            check(directory.startsWith(ROOT) && !directory.equals(ROOT), "Isolated task directory");
            check(Files.isRegularFile(directory.resolve("celestial-effects.marker")), "Explicit audit marker");
            check(mc.level == null && mc.getSingleplayerServer() == null, "No world opened");
            rendererChecks();
            CelestialEffectsClientChecksVisual.run(checks, directory);
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL");
            result.put("error", failure.toString());
            failure.printStackTrace();
        }
        result.put("checkCount", checks.size());
        result.put("checks", checks);
        result.put("scope", "Core Eye BER anchor and working/idle gates, plus offscreen Core Eye and native ADD Astrograph visuals.");
        try {
            Files.writeString(directory.resolve("celestial-effects-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    private void rendererChecks() throws Exception {
        var unsafe = unsafe();
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation("gtceu:eye_of_harmony"));
        check(definition != null, "Eye of Harmony registered definition");
        var renderer = (WorkableCasingMachineRenderer) definition.getRenderer();
        check(renderer.getViewDistance() == 512, "Eye of Harmony transformed view distance 512");
        for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var state = definition.defaultBlockState().setValue(
                    ((MetaMachineBlock) definition.getBlock()).rotationState.property, facing);
            var entity = definition.getBlockEntityType().create(POSITION, state);
            var machine = (WorkableMultiblockMachine) ((IMachineBlockEntity) entity).getMetaMachine();
            var world = (AuditLevel) unsafe.allocateInstance(AuditLevel.class);
            world.state = state;
            world.entity = entity;
            ObfuscationReflectionHelper.findField(Level.class, "f_46443_").setBoolean(world, true);
            entity.setLevel(world);
            try {
                field(MultiblockControllerMachine.class, "isFormed").setBoolean(machine, true);
                var expected = new Vec3(facing.getStepX() * -91 + .5, -54.5, facing.getStepZ() * -91 + .5);
                for (boolean working : List.of(false, true)) {
                    var logic = machine.getRecipeLogic();
                    field(RecipeLogic.class, "isActive").setBoolean(logic, working);
                    field(RecipeLogic.class, "status").set(logic,
                            working ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
                    String label = "Eye of Harmony " + facing + " " + (working ? "WORKING" : "IDLE");
                    check(logic.isWorking() == working && machine.isActive() == working
                                    && machine.isFormed() && machine.getFrontFacing() == facing,
                            label + " native fixture gates");
                    var pose = new CapturePose();
                    boolean captured = false;
                    try {
                        renderer.render(entity, .25F, pose,
                                Minecraft.getInstance().renderBuffers().bufferSource(), 0xF000F0, 0);
                    } catch (ScaleCaptured expectedCapture) {
                        captured = true;
                    }
                    if (working) {
                        check(captured && pose.translations == 1, label + " reaches final center and parent scale");
                        check(pose.center.distanceTo(expected) < 1E-9, label + " transformed renderer center");
                        check(pose.scale.distanceTo(new Vec3(2.75, 2.75, 2.75)) < 1E-9,
                                label + " transformed parent scale 2.75");
                        check(new Vec3(pose.matrix.m30(), pose.matrix.m31(), pose.matrix.m32())
                                        .distanceTo(expected) < 1E-6,
                                label + " scaling preserves translated center");
                    } else {
                        check(!captured && pose.translations == 0 && pose.scale == null,
                                label + " native idle gate emits no celestial effect");
                    }
                }
            } finally {
                entity.setLevel(null);
            }
        }
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        return (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }

    private static final class CapturePose extends PoseStack {
        int translations;
        Vec3 center;
        Vec3 scale;
        Matrix4f matrix;
        @Override public void translate(double x, double y, double z) {
            translations++;
            center = new Vec3(x, y, z);
            super.translate(x, y, z);
        }
        @Override public void scale(float x, float y, float z) {
            scale = new Vec3(x, y, z);
            super.scale(x, y, z);
            matrix = new Matrix4f(last().pose());
            throw new ScaleCaptured();
        }
    }

    private static final class ScaleCaptured extends RuntimeException {
        ScaleCaptured() { super(null, null, false, false); }
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
