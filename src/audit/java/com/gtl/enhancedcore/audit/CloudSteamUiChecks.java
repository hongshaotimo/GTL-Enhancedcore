package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;

/** Actual registered native UI renderers and initial synchronization; no world or network. */
final class CloudSteamUiChecks {
    private CloudSteamUiChecks() {}

    static void run(Path directory, List<String> checks) throws Exception {
        Path images = directory.resolve("screenshots"); Files.createDirectories(images);
        for (String id : List.of("gtl_enhancedcore:industrial_steam_platform", "gtceu:large_chemical_reactor",
                "gtceu:primitive_blast_furnace")) {
            try (var server = new CloudSteamScenarios.Fixture(id, !id.endsWith("steam_platform") && !id.endsWith("blast_furnace"));
                 var client = new CloudSteamScenarios.Fixture(id, !id.endsWith("steam_platform") && !id.endsWith("blast_furnace"))) {
                server.machine.getRecipeLogic().setWorkingEnabled(true);
                if (id.endsWith("steam_platform")) { server.steam(5000); client.steam(5000); }
                ObfuscationReflectionHelper.setPrivateValue(Level.class, client.world, true, "f_46443_");
                ((com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess) client.machine.getRecipeLogic()).iv$syncMode(true, true);
                var serverPlayer = player(server.world); var clientPlayer = player(client.world);
                ModularUI serverUi = ((IUIMachine) server.machine).createUI(serverPlayer);
                ModularUI clientUi = ((IUIMachine) client.machine).createUI(clientPlayer);
                serverUi.initWidgets(); clientUi.initWidgets();
                if (!tree(serverUi.mainGroup).equals(tree(clientUi.mainGroup)))
                    throw new AssertionError(id + " native UI has different client/server topology");
                checks.add(id + " native GUI retains matching real server/client widget trees");
                var packet = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    serverUi.mainGroup.writeInitialData(packet); clientUi.mainGroup.readInitialData(packet);
                    if (packet.readableBytes() != 0) throw new AssertionError(id + " UI initial data left bytes unread");
                } finally { packet.release(); }
                checks.add(id + " native GUI receives the complete actual initial synchronization packet");
                sink(clientUi.mainGroup);
                clientPlayer.containerMenu = clientUi.getModularUIContainer();
                String name = id.substring(id.indexOf(':') + 1);
                Files.writeString(images.resolve(name + "-server-text.json"), new com.google.gson.GsonBuilder().setPrettyPrinting()
                        .create().toJson(text(serverUi.mainGroup)));
                for (int[] size : List.of(new int[]{960, 600, 2}, new int[]{1280, 720, 3}, new int[]{320, 240, 1})) {
                    render(clientUi, size[0], size[1], size[2], images.resolve(name + "-" + size[0] + "x" + size[1] + "-gui" + size[2] + ".png"));
                    checks.add(name + " real native GUI framebuffer " + size[0] + "x" + size[1] + "/GUI" + size[2]);
                }
            }
        }
    }

    private static void render(ModularUI ui, int width, int height, int scale, Path output) throws Exception {
        var mc = Minecraft.getInstance();
        var previousScreen = mc.screen;
        int previousWidth = mc.getWindow().getWidth(), previousHeight = mc.getWindow().getHeight();
        int previousScale = mc.options.guiScale().get();
        // GuiGraphics scissor conversion reads the real Window. Keep it identical
        // to the offscreen target, including the selected native GUI scale.
        mc.options.guiScale().set(scale);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height);
        org.lwjgl.glfw.GLFW.glfwPollEvents();
        mc.resizeDisplay();
        // Hidden GLFW may defer resize until the next event pump; the framebuffer
        // target remains the authoritative requested viewport for this audit.
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            target.setClearColor(.12f, .14f, .16f, 1f); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            int guiWidth = (width + scale - 1) / scale, guiHeight = (height + scale - 1) / scale;
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, guiWidth, guiHeight, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity(); modelView.translate(0, 0, -10000); RenderSystem.applyModelViewMatrix();
            var gui = new AuditGui(ui); gui.initialize(mc, guiWidth, guiHeight);
            mc.screen = gui;
            for (int frame = 0; frame < 3; frame++) {
                var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                gui.render(graphics, -100, -100, 0f); graphics.flush();
            }
            try (var image = Screenshot.takeScreenshot(target)) { image.writeToFile(output); }
        } finally {
            mc.screen = previousScreen;
            target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
            modelView.popPose(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.options.guiScale().set(previousScale);
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), previousWidth, previousHeight);
            org.lwjgl.glfw.GLFW.glfwPollEvents();
            mc.resizeDisplay();
        }
    }

    private static void sink(Widget widget) {
        widget.setUiAccess(new WidgetUIAccess() {
            public boolean attemptMergeStack(ItemStack stack, boolean first, boolean second) { return false; }
            public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
            public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
        });
        if (widget instanceof WidgetGroup group) for (var child : group.widgets) sink(child);
    }

    private static List<String> tree(Widget widget) {
        var tree = new ArrayList<String>(); collect(widget, tree); return tree;
    }
    private static void collect(Widget widget, List<String> tree) {
        tree.add(widget.getClass().getName());
        if (widget instanceof WidgetGroup group) for (var child : group.widgets) collect(child, tree);
    }
    private static List<String> text(Widget widget) throws Exception {
        var result = new ArrayList<String>();
        if (widget instanceof com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget) {
            Field lastText = com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget.class.getDeclaredField("lastText");
            lastText.setAccessible(true);
            for (Object line : (List<?>) lastText.get(widget))
                result.add(net.minecraft.network.chat.Component.Serializer.toJson((net.minecraft.network.chat.Component) line));
        }
        if (widget instanceof WidgetGroup group) for (var child : group.widgets) result.addAll(text(child));
        return result;
    }
    private static AuditPlayer player(Level world) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        var player = (AuditPlayer) unsafe.allocateInstance(AuditPlayer.class);
        player.inventory = new net.minecraft.world.entity.player.Inventory(player);
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.entity.Entity.class, player, world, "f_19853_");
        return player;
    }
    private static final class AuditGui extends ModularUIGuiContainer {
        AuditGui(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc, int guiWidth, int guiHeight) {
            minecraft = mc; font = mc.font; width = guiWidth; height = guiHeight; super.init();
        }
        @Override protected void containerTick() {}
        @Override public void removed() {}
        @Override public void onClose() {}
    }
    private static final class AuditPlayer extends Player {
        net.minecraft.world.entity.player.Inventory inventory;
        private AuditPlayer(Level world) { super(world, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "CloudSteamUiAudit")); }
        @Override public net.minecraft.world.entity.player.Inventory getInventory() { return inventory; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return true; }
    }
}
