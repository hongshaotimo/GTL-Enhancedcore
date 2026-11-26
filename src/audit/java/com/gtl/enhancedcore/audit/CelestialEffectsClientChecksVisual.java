package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.renderer.celestial.CelestialVfxRenderer;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtl.enhancedcore.common.structure.CelestialEffectSizing;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.gtlcore.gtlcore.config.ConfigHolder;

/** Offscreen output of the Core Eye effect and native ADD Astrograph preview. */
public final class CelestialEffectsClientChecksVisual {
    private static final int SIZE = 960;
    private static final long[] TIMES = {10_000, 10_040, 10_080};
    private CelestialEffectsClientChecksVisual() {}

    public static void run(List<String> checks, Path directory) throws Exception {
        var mc = Minecraft.getInstance();
        require(mc.level == null && mc.getSingleplayerServer() == null, "Visual fixture opens no world", checks);
        Files.createDirectories(directory);
        var target = mc.getMainRenderTarget();
        int width = target.width, height = target.height;
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        boolean smooth = ConfigHolder.INSTANCE.graphics.enableSmoothAnimations;
        ConfigHolder.INSTANCE.graphics.enableSmoothAnimations = false;
        try {
            // This isolated fixture supplies matching depth/stencil attachments.
            // It does not change any production framebuffer setup or world rendering.
            target.enableStencil();
            target.resize(SIZE, SIZE, Minecraft.ON_OSX);
            require(target.isStencilEnabled(), "Isolated visual main target has compatible stencil", checks);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(55), 1, .1F, 1500),
                    VertexSorting.DISTANCE_TO_ORIGIN);
            for (String id : List.of("gtceu:eye_of_harmony", "gtladditions:arcanic_astrograph")) {
                renderMachine(id, checks, directory);
            }
        } finally {
            ConfigHolder.INSTANCE.graphics.enableSmoothAnimations = smooth;
            mc.renderBuffers().bufferSource().endBatch();
            target.resize(width, height, Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }
    }

    private static void renderMachine(String id, List<String> checks, Path directory) throws Exception {
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
        var state = definition.defaultBlockState().setValue(
                ((MetaMachineBlock) definition.getBlock()).rotationState.property, Direction.SOUTH);
        var entity = definition.getBlockEntityType().create(BlockPos.ZERO, state);
        var machine = (WorkableMultiblockMachine) ((IMachineBlockEntity) entity).getMetaMachine();
        var world = (AuditLevel) unsafe().allocateInstance(AuditLevel.class);
        world.state = state;
        world.entity = entity;
        ObfuscationReflectionHelper.findField(Level.class, "f_46443_").setBoolean(world, true);
        entity.setLevel(world);
        try {
            field(MultiblockControllerMachine.class, "isFormed").setBoolean(machine, true);
            var logic = machine.getRecipeLogic();
            field(RecipeLogic.class, "isActive").setBoolean(logic, true);
            field(RecipeLogic.class, "status").set(logic, RecipeLogic.Status.WORKING);
            boolean eye = id.endsWith("eye_of_harmony");
            require(machine.isActive() && logic.isWorking() && machine.isFormed(), id + " visual active fixture", checks);
            // The native ADD shell is 16 blocks behind the controller (facing SOUTH here).
            var center = eye ? CelestialEffectAnchors.eye(machine) : new Vec3(.5, .5, -15.5);
            var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
            require(renderer != null, id + " registered real BER for pixels", checks);
            var frames = new ArrayList<LinkedHashMap<String, Object>>();
            for (String angle : List.of("front", "diagonal", "near", "close")) {
                var camera = center.add(switch (angle) {
                    case "front" -> new Vec3(0, 0, 100);
                    case "near" -> new Vec3(43, 18, 43);
                    case "close" -> new Vec3(12, 8, 12);
                    default -> new Vec3(68, 37, 58);
                });
                var hashes = new java.util.HashSet<Long>();
                for (long time : TIMES) {
                    world.time = time;
                    var view = new Matrix4f().lookAt(camera.toVector3f(), center.toVector3f(), new Vector3f(0, 1, 0));
                    if (eye) {
                        // Match the direct production shader: global model-view * parent BER pose.
                        // The fixture global matrix is identity; lookAt must be applied exactly once.
                        var fieldView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(view)
                                .translate((float) center.x, (float) center.y, (float) center.z).scale(CelestialEffectSizing.EYE_SCALE);
                        var origin = fieldView.transformPosition(new Vector3f());
                        require(Math.abs(origin.x) < 1E-4 && Math.abs(origin.y) < 1E-4
                                        && Math.abs(origin.z + camera.distanceTo(center)) < 1E-4,
                                id + " " + angle + " direct shader field center matches camera view", checks);
                    }
                    var pose = new PoseStack();
                    pose.last().pose().set(view);
                    pose.last().normal().set(view).invert().transpose();
                    var mc = Minecraft.getInstance();
                    var target = mc.getMainRenderTarget();
                    target.setClearColor(.008F, .012F, .02F, 1);
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(true);
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                    RenderSystem.enableDepthTest();
                    RenderSystem.enableCull();
                    var buffers = mc.renderBuffers().bufferSource();
                    if (eye) {
                        // The title screen has no scene frame. The Core BER anchor is checked separately.
                        pose.translate(center.x, center.y, center.z);
                        float scale = CelestialEffectSizing.EYE_SCALE;
                        pose.scale(scale, scale, scale);
                        CelestialVfxRenderer.eye(time + .25F, pose, buffers);
                    } else {
                        renderer.render(entity, .25F, pose, buffers, 0xF000F0, 0);
                        buffers.endBatch();
                    }
                    String filename = id.substring(id.indexOf(':') + 1) + "-" + angle + "-t" + time + "-actual.png";
                    try (var picture = Screenshot.takeScreenshot(target)) {
                        picture.writeToFile(directory.resolve(filename));
                        int lit = 0;
                        long hash = 0xcbf29ce484222325L;
                        for (int y = 0; y < SIZE; y += 2) for (int x = 0; x < SIZE; x += 2) {
                            int pixel = picture.getPixelRGBA(x, y);
                            hash = (hash ^ pixel) * 0x100000001b3L;
                            if ((pixel & 255) > 28 || (pixel >> 8 & 255) > 28 || (pixel >> 16 & 255) > 28) lit++;
                        }
                        require(lit > 100, id + " " + angle + " t" + time + " real renderer has " + lit + " lit samples", checks);
                        hashes.add(hash);
                        var frame = new LinkedHashMap<String, Object>();
                        frame.put("file", filename);
                        frame.put("machine", id);
                        frame.put("angle", angle);
                        frame.put("gameTime", time);
                        frame.put("litSamples", lit);
                        frame.put("pixelHash", Long.toUnsignedString(hash, 16));
                        frame.put("camera", camera.toString());
                        frame.put("center", center.toString());
                        frames.add(frame);
                    }
                }
                require(hashes.size() > 1, id + " " + angle + " production animation changes across world times", checks);
            }
            Files.writeString(directory.resolve(id.substring(id.indexOf(':') + 1) + "-frames.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(frames));
        } finally {
            entity.setLevel(null);
        }
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        return (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void require(boolean valid, String message, List<String> checks) {
        if (!valid) throw new AssertionError(message);
        checks.add(message);
    }

    private static final class AuditLevel extends DummyWorld {
        BlockState state;
        BlockEntity entity;
        long time;
        private AuditLevel() { super(null); }
        @Override public long getGameTime() { return time; }
        @Override public BlockState getBlockState(BlockPos pos) { return state; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return entity; }
    }
}
