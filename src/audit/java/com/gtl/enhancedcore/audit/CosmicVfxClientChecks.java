package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.client.renderer.machine.StellarVortexMachineRenderer;
import com.gtl.enhancedcore.common.machine.galaxy.StellarVortexSpaceStationMachine;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredMachineRenderer;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderContext;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderEntry;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/** Four production-renderer views in a title-screen client; does not load a saved world. */
@Mod("enhancedcore_audit")
public final class CosmicVfxClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cosmic-vfx-20261113")
            .toAbsolutePath().normalize();
    private static final int WIDTH = 1200, HEIGHT = 900;
    private boolean done;

    public CosmicVfxClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.cosmicVfx.audit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, Object>();
        var frames = new ArrayList<Map<String, Object>>();
        try {
            require(directory.startsWith(ROOT) && !directory.equals(ROOT)
                    && Files.isRegularFile(directory.resolve("cosmic-vfx.marker")), "Expected isolated fixture directory");
            require(mc.level == null && mc.getSingleplayerServer() == null, "Fixture must not open a world");
            render(directory, frames);
            result.put("status", "PASS");
        } catch (Throwable error) {
            result.put("status", "FAIL");
            result.put("error", error.toString());
            error.printStackTrace();
        }
        result.put("frames", frames);
        result.put("scope", "Registered machine BERs and production shaders; front/daylight and diagonal/dark views. No saved world, structure blocks, recipe or frame-rate measurement.");
        try {
            Files.writeString(directory.resolve("cosmic-vfx.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    private static void render(Path directory, List<Map<String, Object>> frames) throws Exception {
        var mc = Minecraft.getInstance();
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        boolean smooth = ConfigHolder.INSTANCE.graphics.enableSmoothAnimations;
        ConfigHolder.INSTANCE.graphics.enableSmoothAnimations = false;
        var target = new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX);
        try {
            target.enableStencil();
            target.resize(WIDTH, HEIGHT, Minecraft.ON_OSX);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(52),
                    WIDTH / (float) HEIGHT, .1F, 2000F), VertexSorting.DISTANCE_TO_ORIGIN);
            for (String id : List.of("gtladditions:arcanic_astrograph", "gtl_enhancedcore:stellar_vortex_space_station")) {
                renderMachine(id, directory, target, frames);
            }
        } finally {
            ConfigHolder.INSTANCE.graphics.enableSmoothAnimations = smooth;
            mc.renderBuffers().bufferSource().endBatch();
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }
    }

    @SuppressWarnings("unchecked")
    private static void renderMachine(String id, Path directory, TextureTarget target,
            List<Map<String, Object>> frames) throws Exception {
        var mc = Minecraft.getInstance();
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(id));
        require(definition != null, "Registered machine " + id);
        var state = definition.defaultBlockState().setValue(
                ((MetaMachineBlock) definition.getBlock()).rotationState.property, Direction.SOUTH);
        var entity = definition.getBlockEntityType().create(BlockPos.ZERO, state);
        var machine = (WorkableMultiblockMachine) ((IMachineBlockEntity) entity).getMetaMachine();
        var world = (AuditLevel) ((sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null))
                .allocateInstance(AuditLevel.class);
        world.state = state;
        world.entity = entity;
        ObfuscationReflectionHelper.findField(Level.class, "f_46443_").setBoolean(world, true);
        entity.setLevel(world);
        var queue = (Long2ObjectLinkedOpenHashMap<DeferredRenderEntry>) field(DeferredMachineRenderer.class, "entries").get(null);
        require(!queue.containsKey(0), "Unoccupied fixture deferred entry");
        try {
            field(MultiblockControllerMachine.class, "isFormed").setBoolean(machine, true);
            var logic = machine.getRecipeLogic();
            field(RecipeLogic.class, "isActive").setBoolean(logic, true);
            field(RecipeLogic.class, "status").set(logic, RecipeLogic.Status.WORKING);
            boolean astrograph = id.endsWith("arcanic_astrograph");
            Vec3 center;
            if (astrograph) {
                field(machine.getClass(), "parallelAmount").setInt(machine, Integer.MAX_VALUE);
                center = CelestialEffectAnchors.astrograph(machine);
            } else {
                var station = (StellarVortexSpaceStationMachine) machine;
                field(StellarVortexSpaceStationMachine.class, "fieldActive").setBoolean(station, true);
                field(StellarVortexSpaceStationMachine.class, "fieldStart").setLong(station, 9910L);
                var offset = StellarVortexMachineRenderer.class.getDeclaredMethod("offset",
                        StellarVortexSpaceStationMachine.class, double.class, double.class, double.class);
                offset.setAccessible(true);
                var delta = (double[]) offset.invoke(null, station, -.5, 49.5, -38.5);
                center = new Vec3(delta[0] + .5, delta[1] + .5, delta[2] + .5);
                // Capture the normal fully-visible state without waiting for the wall-clock fade.
                var renderer = (StellarVortexMachineRenderer) definition.getRenderer();
                var fade = Class.forName(StellarVortexMachineRenderer.class.getName() + "$Fade");
                var constructor = fade.getDeclaredConstructor(float.class, long.class);
                constructor.setAccessible(true);
                ((Map<BlockEntity, Object>) field(StellarVortexMachineRenderer.class, "visibility").get(renderer))
                        .put(entity, constructor.newInstance(1F, System.nanoTime()));
            }
            var ber = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
            require(ber != null, "Registered BER " + id);
            for (String angle : List.of("front", "diagonal")) {
                boolean front = angle.equals("front");
                double range = astrograph ? 340 : 570;
                var camera = center.add(front ? new Vec3(0, 0, range) : new Vec3(range * .68, range * .40, range * .68));
                var view = new Matrix4f().lookAt(camera.toVector3f(), center.toVector3f(), new Vector3f(0, 1, 0));
                var pose = new PoseStack();
                pose.last().pose().set(view);
                pose.last().normal().set(view).invert().transpose();
                target.setClearColor(front ? .40F : .008F, front ? .60F : .012F, front ? .85F : .02F, 1);
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Clean GL state before " + id);
                var buffers = mc.renderBuffers().bufferSource();
                ber.render(entity, .25F, pose, buffers, 0xF000F0, 0);
                buffers.endBatch();
                if (astrograph) {
                    var entry = queue.remove(0);
                    require(entry != null, "Astrograph registered BER enqueues effect");
                    entry.render(new DeferredRenderContext(camera, new PoseStack(), null));
                }
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Production renderer GL state " + id);
                String file = id.substring(id.indexOf(':') + 1) + "-" + angle + ".png";
                try (var picture = Screenshot.takeScreenshot(target)) {
                    picture.writeToFile(directory.resolve(file));
                    int background = picture.getPixelRGBA(0, 0), changed = 0;
                    for (int y = 0; y < HEIGHT; y += 3) for (int x = 0; x < WIDTH; x += 3) {
                        int pixel = picture.getPixelRGBA(x, y);
                        if (Math.abs((pixel & 255) - (background & 255)) > 8
                                || Math.abs((pixel >> 8 & 255) - (background >> 8 & 255)) > 8
                                || Math.abs((pixel >> 16 & 255) - (background >> 16 & 255)) > 8) changed++;
                    }
                    require(changed > 100, "Visible production effect " + id + " " + angle);
                    frames.add(Map.of("file", file, "machine", id, "view", angle,
                            "changedSamples", changed, "camera", camera.toString(), "center", center.toString()));
                }
            }
        } finally {
            queue.remove(0);
            entity.setLevel(null);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
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
