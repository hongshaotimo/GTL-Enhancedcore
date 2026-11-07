package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.lowdragmc.lowdraglib.utils.DummyWorld;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredMachineRenderer;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderContext;
import com.gtladd.gtladditions.client.render.machine.deferred.DeferredRenderEntry;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.GameRenderer;
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
import org.lwjgl.opengl.*;

/** State and successor-draw audit. Deliberately never repairs state between BER and probes. */
@Mod("enhancedcore_audit")
public final class CelestialRenderStateChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/astrograph-black-screen-20261107/validation");
    private static final int SIZE = 640;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> scenarios = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private final Map<String, int[]> idleCrosshairs = new HashMap<>();
    private int phase;
    private boolean done;
    private CompletableFuture<Void> reload;
    private Class<?> iris;
    private boolean initialShaders;
    private GLDebugMessageCallback debugCallback;
    private String currentScenario = "startup";

    public CelestialRenderStateChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.celestialState.audit")) return;
        var mc = Minecraft.getInstance();
        if (mc.screen == null || mc.getOverlay() != null) return;
        var dir = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (phase == 0) {
                if (!dir.startsWith(ROOT) || !Files.isRegularFile(dir.resolve("celestial-state.marker"))
                        || mc.level != null || mc.getSingleplayerServer() != null) throw new AssertionError("Isolation boundary");
                report.put("scope", "Actual registered astrograph BER, actual GL snapshots and successor GPU draws; no player save or stencil modification. Oculus native toggle/reload exercised when present; title-screen has no active world shader pipeline.");
                if (GL.getCapabilities().OpenGL43) {
                    debugCallback = GLDebugMessageCallback.create((source, type, id, severity, length, message, user) -> {
                        if (type == GL43.GL_DEBUG_TYPE_ERROR) {
                            System.err.println("[CELESTIAL_GL_ERROR] scenario=" + currentScenario + " id=" + id + " "
                                    + GLDebugMessageCallback.getMessage(length, message));
                            new Exception("Synchronous GL error origin").printStackTrace();
                        }
                    });
                    GL43.glDebugMessageCallback(debugCallback, 0);
                    GL11.glEnable(GL43.GL_DEBUG_OUTPUT); GL11.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                }
                try { iris = Class.forName("net.irisshaders.iris.Iris"); }
                catch (ClassNotFoundException absent) { report.put("oculus", "not installed"); }
                if (iris != null) {
                    var config = iris.getMethod("getIrisConfig").invoke(null);
                    initialShaders = (boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
                }
            }
            if (phase == 4) {
                if (!reload.isDone()) return;
                reload.join();
            }
            String label = switch (phase) {
                case 0 -> "initial"; case 1 -> "oculus-off"; case 2 -> "oculus-on";
                case 3 -> "oculus-reload"; default -> "resources-reloaded";
            };
            if (iris != null && (phase == 1 || phase == 2))
                iris.getMethod("toggleShaders", Minecraft.class, boolean.class).invoke(null, mc, phase == 2);
            if (iris != null && phase == 3) iris.getMethod("reload").invoke(null);
            captureBatch(dir, label);
            if (++phase == 4) { reload = mc.reloadResourcePacks(); return; }
            if (phase == 5) finish(mc, dir, null);
        } catch (Throwable error) { finish(mc, dir, error); }
    }

    private void finish(Minecraft mc, Path dir, Throwable error) {
        done = true;
        try {
            if (error != null) { failures.add(error.toString()); error.printStackTrace(); }
            report.put("status", failures.isEmpty() ? "PASS" : "FAIL");
            report.put("failures", failures);
            report.put("scenarios", scenarios);
            report.put("worldOpened", false);
            report.put("oculusWorldPipelineVerified", false);
            if (iris != null) iris.getMethod("toggleShaders", Minecraft.class, boolean.class).invoke(null, mc, initialShaders);
            Files.writeString(dir.resolve("celestial-state.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Throwable failure) { failure.printStackTrace(); }
        mc.stop();
    }

    private void captureBatch(Path directory, String stage) throws Exception {
        var mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        int width = target.width, height = target.height;
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var mv = RenderSystem.getModelViewStack();
        mv.pushPose(); mv.setIdentity(); RenderSystem.applyModelViewMatrix();
        boolean smooth = ConfigHolder.INSTANCE.enableSmoothAnimations;
        ConfigHolder.INSTANCE.enableSmoothAnimations = false;
        try {
            // Keep the production attachment configuration: do not enable stencil here.
            target.resize(SIZE, SIZE, Minecraft.ON_OSX);
            for (boolean active : List.of(false, true)) for (boolean day : List.of(true, false))
                for (boolean inheritedBlend : List.of(false, true))
                    capture(directory, stage, active, day, inheritedBlend);
            if (stage.equals("initial")) for (boolean active : List.of(false, true))
                for (boolean day : List.of(true, false)) capture(directory, "eye", active, day, false);
        } finally {
            ConfigHolder.INSTANCE.enableSmoothAnimations = smooth;
            target.resize(width, height, Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
            mv.popPose(); RenderSystem.applyModelViewMatrix();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(true); RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend(); RenderSystem.enableCull();
        }
    }

    private void capture(Path directory, String stage, boolean active, boolean day, boolean inheritedBlend) throws Exception {
        var mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        String name = stage + "-" + (active ? "active" : "idle") + "-" + (day ? "day" : "night")
                + "-" + (inheritedBlend ? "blend" : "opaque");
        currentScenario = name;
        var row = new LinkedHashMap<String, Object>();
        scenarios.add(row); row.put("name", name);
        if (iris != null) {
            row.put("oculusPack", iris.getMethod("getCurrentPackName").invoke(null));
            var manager = iris.getMethod("getPipelineManager").invoke(null);
            var pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            row.put("oculusPipeline", pipeline == null ? "none-title-screen" : pipeline.getClass().getName());
            row.put("oculusStoredError", iris.getMethod("getStoredError").invoke(null).toString());
        }
        boolean eye = stage.equals("eye");
        row.put("machine", eye ? "gtceu:eye_of_harmony" : "gtladditions:arcanic_astrograph");
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(new ResourceLocation(
                eye ? "gtceu:eye_of_harmony" : "gtladditions:arcanic_astrograph"));
        var state = definition.defaultBlockState().setValue(((MetaMachineBlock) definition.getBlock()).rotationState.property, Direction.SOUTH);
        var entity = definition.getBlockEntityType().create(BlockPos.ZERO, state);
        var machine = (WorkableMultiblockMachine) ((IMachineBlockEntity) entity).getMetaMachine();
        var world = (AuditLevel) unsafe().allocateInstance(AuditLevel.class);
        world.state = state; world.entity = entity;
        ObfuscationReflectionHelper.findField(Level.class, "f_46443_").setBoolean(world, true);
        entity.setLevel(world);
        try {
            field(MultiblockControllerMachine.class, "isFormed").setBoolean(machine, true);
            if (!eye) field(machine.getClass(), "parallelAmount").setInt(machine, Integer.MAX_VALUE);
            var logic = machine.getRecipeLogic();
            field(RecipeLogic.class, "isActive").setBoolean(logic, active);
            field(RecipeLogic.class, "status").set(logic, active ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
            target.setClearColor(day ? .25F : .025F, day ? .55F : .04F, day ? .8F : .09F, 1);
            RenderSystem.colorMask(true, true, true, true); RenderSystem.depthMask(true);
            target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            RenderSystem.viewport(0, 0, SIZE, SIZE);
            RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(55), 1, .1F, 1500), VertexSorting.DISTANCE_TO_ORIGIN);
            RenderSystem.enableDepthTest(); RenderSystem.disableCull();
            RenderSystem.depthMask(!inheritedBlend);
            if (inheritedBlend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderColor(.82F, .91F, .73F, .88F);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.getShader().apply();
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);
            var before = snapshot(); row.put("before", before);
            var center = eye ? CelestialEffectAnchors.eye(machine) : CelestialEffectAnchors.astrograph(machine);
            var camera = center.add(day ? new Vec3(68, 37, 58) : new Vec3(12, 8, 12));
            var view = new Matrix4f().lookAt(camera.toVector3f(), center.toVector3f(), new Vector3f(0, 1, 0));
            var pose = new PoseStack(); pose.last().pose().set(view); pose.last().normal().set(view).invert().transpose();
            var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(entity);
            renderer.render(entity, .25F, pose, mc.renderBuffers().bufferSource(), 0xF000F0, 0);
            @SuppressWarnings("unchecked")
            var queue = (Long2ObjectLinkedOpenHashMap<DeferredRenderEntry>) field(DeferredMachineRenderer.class, "entries").get(null);
            var pending = queue.remove(BlockPos.ZERO.asLong());
            if (pending != null) {
                row.put("drawPath", "Real BER enqueues; manually invoking actual deferred entry render. Scheduling and world identity gate are not tested here.");
                row.put("deferredEntry", pending.getClass().getName());
                pending.render(new DeferredRenderContext(camera, new PoseStack(), null));
            } else row.put("drawPath", "Real BER immediate draw or idle");
            var after = snapshot(); row.put("after", after);
            var differences = new ArrayList<String>();
            for (var key : before.keySet()) if (!Objects.equals(before.get(key), after.get(key))) differences.add(key);
            row.put("stateDifferences", differences);
            if (!differences.isEmpty()) failures.add(name + " leaked " + differences);
            // No state repair here: only the shader/projection changes ordinary successor draws require.
            RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, SIZE, SIZE, 0, -10, 10), VertexSorting.ORTHOGRAPHIC_Z);
            // Exact GUI ordering immediately after BER, before any opaque shader can neutralize BlendMode.lastApplied.
            RenderSystem.disableDepthTest();
            drawCrosshair();
            row.put("immediateCrosshairState", snapshot());
            try (var image = Screenshot.takeScreenshot(target)) {
                image.writeToFile(directory.resolve(name + "-immediate-crosshair.png"));
                int black = 0;
                int[] patch = new int[225];
                int patchIndex = 0;
                for (int y = SIZE / 2 - 7; y < SIZE / 2 + 8; y++) for (int x = SIZE / 2 - 7; x < SIZE / 2 + 8; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    patch[patchIndex++] = pixel;
                    if ((pixel & 255) + (pixel >> 8 & 255) + (pixel >> 16 & 255) < 15) black++;
                }
                row.put("immediateCrosshairBlackPixels", black);
                String baselineKey = stage + "-" + day + "-" + inheritedBlend;
                if (!active) idleCrosshairs.put(baselineKey, patch);
                else {
                    int newBlack = 0;
                    int[] baseline = idleCrosshairs.get(baselineKey);
                    for (int i = 0; i < patch.length; i++) {
                        int current = patch[i], previous = baseline[i];
                        int currentLight = (current & 255) + (current >> 8 & 255) + (current >> 16 & 255);
                        int previousLight = (previous & 255) + (previous >> 8 & 255) + (previous >> 16 & 255);
                        if (currentLight < 15 && previousLight > 40) newBlack++;
                    }
                    row.put("crosshairNewBlackComparedToIdle", newBlack);
                    if (newBlack > 100) failures.add(name + " immediate vanilla crosshair became opaque black square relative to idle");
                }
            }
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            rect(8, 8, 112, 70, .2F, .7F, 1); // successor sky
            rect(8, SIZE - 70, 112, SIZE - 8, .3F, .85F, .25F); // successor ground
            rect(SIZE - 112, SIZE - 70, SIZE - 8, SIZE - 8, 1, .35F, .2F); // successor GUI
            RenderSystem.disableDepthTest(); // Ordinary GUI stage, after world successor probes.
            drawCrosshair();
            row.put("successorDrawGlError", GL11.glGetError());
            try (var image = Screenshot.takeScreenshot(target)) {
                image.writeToFile(directory.resolve(name + ".png"));
                var pixels = new LinkedHashMap<String, String>();
                for (var point : List.of(new int[]{30, 30}, new int[]{30, SIZE - 30}, new int[]{SIZE - 30, SIZE - 30})) {
                    int pixel = image.getPixelRGBA(point[0], point[1]);
                    pixels.put(point[0] + "," + point[1], Integer.toUnsignedString(pixel, 16));
                    if ((pixel & 255) < 20 && (pixel >> 8 & 255) < 20 && (pixel >> 16 & 255) < 20)
                        failures.add(name + " successor swatch black at " + Arrays.toString(point));
                }
                row.put("successorPixels", pixels);
                int crosshairLit = 0;
                for (int y = SIZE / 2 - 7; y <= SIZE / 2 + 7; y++) for (int x = SIZE / 2 - 7; x <= SIZE / 2 + 7; x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    if ((pixel & 255) + (pixel >> 8 & 255) + (pixel >> 16 & 255) > 150) crosshairLit++;
                }
                row.put("crosshairLitPixels", crosshairLit);
                if (crosshairLit < 4) failures.add(name + " crosshair has fewer than four visible pixels");
                int corner = image.getPixelRGBA(SIZE / 2 - 6, SIZE / 2 - 6);
                row.put("crosshairTransparentCorner", Integer.toUnsignedString(corner, 16));
            }
        } finally { entity.setLevel(null); }
    }

    private static Map<String, Object> snapshot() {
        var state = new LinkedHashMap<String, Object>();
        for (var item : Map.of("program", GL20.GL_CURRENT_PROGRAM, "activeTexture", GL13.GL_ACTIVE_TEXTURE,
                "drawFramebuffer", GL30.GL_DRAW_FRAMEBUFFER_BINDING, "readFramebuffer", GL30.GL_READ_FRAMEBUFFER_BINDING,
                "blendSrcRGB", GL14.GL_BLEND_SRC_RGB, "blendDstRGB", GL14.GL_BLEND_DST_RGB,
                "blendSrcAlpha", GL14.GL_BLEND_SRC_ALPHA, "blendDstAlpha", GL14.GL_BLEND_DST_ALPHA).entrySet())
            state.put(item.getKey(), GL11.glGetInteger(item.getValue()));
        state.put("depthTest", GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        state.put("depthFunc", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        state.put("depthMask", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        state.put("activeTextureBinding2D", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        state.put("blendEquationRGB", GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB));
        state.put("blendEquationAlpha", GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA));
        state.put("blend", GL11.glIsEnabled(GL11.GL_BLEND));
        state.put("cull", GL11.glIsEnabled(GL11.GL_CULL_FACE));
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        state.put("viewport", Arrays.toString(viewport));
        state.put("shaderColor", Arrays.toString(RenderSystem.getShaderColor().clone()));
        var shader = RenderSystem.getShader();
        state.put("shaderReference", shader == null ? "null" : shader.getName() + "@" + System.identityHashCode(shader));
        state.put("shaderProgram", shader == null ? -1 : shader.getId());
        int fbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (fbo != 0) for (int attachment : new int[]{GL30.GL_DEPTH_ATTACHMENT, GL30.GL_STENCIL_ATTACHMENT}) {
            String label = attachment == GL30.GL_DEPTH_ATTACHMENT ? "depthAttachment" : "stencilAttachment";
            state.put(label + "Type", GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE));
            state.put(label + "Name", GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME));
        }
        return state;
    }

    private static void rect(float x1, float y1, float x2, float y2, float r, float g, float b) {
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(x1, y2, 0).color(r, g, b, 1F).endVertex(); buffer.vertex(x2, y2, 0).color(r, g, b, 1F).endVertex();
        buffer.vertex(x2, y1, 0).color(r, g, b, 1F).endVertex(); buffer.vertex(x1, y1, 0).color(r, g, b, 1F).endVertex();
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void drawCrosshair() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        var mc = Minecraft.getInstance();
        new GuiGraphics(mc, mc.renderBuffers().bufferSource()).blit(new ResourceLocation("minecraft", "textures/gui/icons.png"),
                (SIZE - 15) / 2, (SIZE - 15) / 2, 0, 0, 15, 15);
    }

    private static Field field(Class<?> type, String name) throws Exception { var f = type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static sun.misc.Unsafe unsafe() throws Exception { return (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null); }
    private static final class AuditLevel extends DummyWorld {
        BlockState state; BlockEntity entity;
        private AuditLevel() { super(null); }
        @Override public long getGameTime() { return 10_000; }
        @Override public BlockState getBlockState(BlockPos pos) { return state; }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return entity; }
    }
}
