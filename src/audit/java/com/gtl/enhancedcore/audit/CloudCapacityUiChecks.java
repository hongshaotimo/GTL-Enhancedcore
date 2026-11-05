package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** Real production UI packets and framebuffer renders; fixture formation/readiness remain explicit seams. */
public final class CloudCapacityUiChecks {
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private CloudCapacityUiChecks() {}

    public static void run(Path directory, List<String> checks) throws Exception {
        Path images = directory.resolve("screenshots"); Files.createDirectories(images);
        var rows = new ArrayList<Map<String, Object>>();
        var failures = new ArrayList<String>();
        for (String id : List.of("gtladditions:space_scaling_instrument", "gtladditions:forge_of_the_antichrist",
                "gtladditions:heliofusion_exoticizer", "gtladditions:space_infinity_integrated_ore_processor")) {
            var row = new LinkedHashMap<String, Object>(); row.put("id", id); rows.add(row);
            try (var server = new CloudCapacityFixture(id, true); var client = new CloudCapacityFixture(id, true)) {
                server.machine.getRecipeLogic().setWorkingEnabled(true);
                ObfuscationReflectionHelper.setPrivateValue(Level.class, client.world, true, "f_46443_");
                ((IvNativeAccess)client.machine.getRecipeLogic()).iv$syncMode(true, true);
                var serverPlayer = player(server.world); var clientPlayer = player(client.world);
                var serverUi = ((IUIMachine)server.machine).createUI(serverPlayer);
                var clientUi = ((IUIMachine)client.machine).createUI(clientPlayer);
                serverUi.initWidgets(); clientUi.initWidgets();
                var serverTree = tree(serverUi.mainGroup); var clientTree = tree(clientUi.mainGroup);
                row.put("serverTree", serverTree); row.put("clientTree", clientTree);
                check(serverTree.equals(clientTree), id + " actual client/server native widget trees match", checks);
                var packet = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    serverUi.mainGroup.writeInitialData(packet); row.put("initialBytes", packet.readableBytes());
                    clientUi.mainGroup.readInitialData(packet); row.put("unreadInitialBytes", packet.readableBytes());
                    check(packet.readableBytes() == 0, id + " actual server initial UI packet is fully consumed by client tree", checks);
                } finally { packet.release(); }
                var serverText = text(serverUi.mainGroup); var clientText = text(clientUi.mainGroup);
                row.put("serverText", describe(serverText)); row.put("clientText", describe(clientText));
                check(serverText.stream().map(Component.Serializer::toJson).toList()
                                .equals(clientText.stream().map(Component.Serializer::toJson).toList()),
                        id + " synchronized client display components exactly match server", checks);
                String infinity = Component.translatable("gtladditions.multiblock.forge_of_the_antichrist.parallel").getString();
                row.put("localizedInfinity", infinity);
                var parallels = capacityRows(clientText, "gtceu.multiblock.parallel");
                check(parallels.size() == 1 && value(parallels.getFirst()).equals(infinity),
                        id + " rendered parallel row uses native localized infinity value " + infinity, checks);
                var threads = capacityRows(clientText, "gtladditions.multiblock.threads");
                boolean single = id.endsWith("heliofusion_exoticizer");
                row.put("parallel", IvMachineScope.parallel(server.machine, null));
                row.put("threads", IvMachineScope.threads(server.machine));
                check(single ? threads.isEmpty() && IvMachineScope.threads(server.machine) == 1
                                : threads.size() == 1 && value(threads.getFirst()).equals(infinity)
                                        && IvMachineScope.unlimitedThreads(server.machine),
                        id + (single ? " retains one locked recipe and no invented thread row" : " native thread row keeps infinity rather than 128 or int-max"), checks);
                sink(clientUi.mainGroup); clientPlayer.containerMenu = clientUi.getModularUIContainer();
                var renders = new ArrayList<Map<String, Object>>(); row.put("renders", renders);
                String name = "capacity-" + id.substring(id.indexOf(':') + 1);
                for (int[] size : SIZES) {
                    var image = images.resolve(name + "-" + size[0] + "x" + size[1] + "-gui" + size[2] + ".png");
                    var frame = render(clientUi, size[0], size[1], size[2], image); renders.add(frame);
                    checks.add(id + " actual native UI framebuffer " + size[0] + "x" + size[1] + "/GUI" + size[2]
                            + " windowScissorMatched=" + frame.get("windowScissorMatched"));
                }
                row.put("status", "PASS");
            } catch (Throwable failure) {
                failure.printStackTrace(); row.put("status", "FAIL"); row.put("error", failure.toString()); failures.add(id + ": " + failure);
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", failures.isEmpty() ? "PASS" : "FAIL"); report.put("machines", rows); report.put("failures", failures);
        report.put("scope", "Actual registered CloudCapacityFixture suppliers, production ModularUI server/client widget trees, real initial synchronization and native client framebuffer rendering. Synthetic formed membership and readiness, no real structure formation, live ME transfer or player world.");
        report.put("renderBoundary", "Each render attempts to match the real GLFW window to its requested framebuffer and GUI scale. windowScissorMatched=false marks possible clipping caused by real Window/scissor disagreement; screenshots retain actual result. Native small-window overflow is not redesigned.");
        Files.writeString(directory.resolve("cloud-capacity-ui.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
        if (!failures.isEmpty()) throw new AssertionError("Cloud capacity UI failed: " + failures);
    }

    private static List<Component> capacityRows(List<Component> lines, String key) {
        return lines.stream().filter(line -> line.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)).toList();
    }
    private static String value(Component line) {
        var contents = (TranslatableContents)line.getContents();
        if (contents.getArgs().length != 1) return "invalid capacity argument count";
        Object argument = contents.getArgs()[0]; return argument instanceof Component component ? component.getString() : String.valueOf(argument);
    }
    private static List<Map<String, Object>> describe(List<Component> lines) {
        return lines.stream().map(line -> {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("text", line.getString()); row.put("json", Component.Serializer.toJson(line)); return row;
        }).toList();
    }
    private static List<String> tree(Widget root) {
        var result = new ArrayList<String>(); collect(root, result); return result;
    }
    private static void collect(Widget widget, List<String> result) {
        result.add(widget.getClass().getName() + " id=" + widget.getId() + " pos=" + widget.getPositionX() + "," + widget.getPositionY()
                + " size=" + widget.getSizeWidth() + "x" + widget.getSizeHeight());
        if (widget instanceof WidgetGroup group) for (var child : group.widgets) collect(child, result);
    }
    private static List<Component> text(Widget root) throws Exception {
        var result = new ArrayList<Component>();
        if (root instanceof ComponentPanelWidget) {
            var field = ComponentPanelWidget.class.getDeclaredField("lastText"); field.setAccessible(true);
            for (Object line : (List<?>)field.get(root)) result.add((Component)line);
        }
        if (root instanceof WidgetGroup group) for (var child : group.widgets) result.addAll(text(child));
        return result;
    }
    private static void sink(Widget root) {
        root.setUiAccess(new WidgetUIAccess() {
            public boolean attemptMergeStack(ItemStack stack, boolean first, boolean second) { return false; }
            public void writeUpdateInfo(Widget widget, int id, Consumer<FriendlyByteBuf> writer) {}
            public void writeClientAction(Widget widget, int id, Consumer<FriendlyByteBuf> writer) {}
        });
        if (root instanceof WidgetGroup group) for (var child : group.widgets) sink(child);
    }
    private static AuditPlayer player(Level world) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe)field.get(null); var player = (AuditPlayer)unsafe.allocateInstance(AuditPlayer.class);
        player.inventory = new net.minecraft.world.entity.player.Inventory(player);
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.entity.Entity.class, player, world, "f_19853_"); return player;
    }
    private static Map<String, Object> render(ModularUI ui, int width, int height, int scale, Path output) throws Exception {
        var mc = Minecraft.getInstance(); var previousScreen = mc.screen;
        int previousWidth = mc.getWindow().getWidth(), previousHeight = mc.getWindow().getHeight(), previousScale = mc.options.guiScale().get();
        mc.options.guiScale().set(scale); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), width, height); GLFW.glfwPollEvents(); mc.resizeDisplay();
        var row = new LinkedHashMap<String, Object>(); row.put("image", output.toString()); row.put("requestedFramebuffer", List.of(width, height));
        row.put("requestedScale", scale); row.put("windowFramebuffer", List.of(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        row.put("windowGuiSize", List.of(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()));
        row.put("windowGuiScale", mc.getWindow().getGuiScale());
        row.put("windowScissorMatched", mc.getWindow().getWidth() == width && mc.getWindow().getHeight() == height
                && mc.getWindow().getGuiScale() == scale);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix()); var sorting = RenderSystem.getVertexSorting();
        var target = new TextureTarget(width, height, true, Minecraft.ON_OSX); var modelView = RenderSystem.getModelViewStack(); modelView.pushPose();
        try {
            target.setClearColor(.12f, .14f, .16f, 1f); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            int guiWidth = (width + scale - 1) / scale, guiHeight = (height + scale - 1) / scale;
            row.put("renderGuiSize", List.of(guiWidth, guiHeight)); row.put("nativeUiSize", List.of(ui.getWidth(), ui.getHeight()));
            row.put("nativeUiFits", ui.getWidth() <= guiWidth && ui.getHeight() <= guiHeight);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, guiWidth, guiHeight, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity(); modelView.translate(0, 0, -10000); RenderSystem.applyModelViewMatrix();
            var gui = new AuditGui(ui); gui.initialize(mc, guiWidth, guiHeight); mc.screen = gui;
            for (int frame = 0; frame < 3; frame++) {
                var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource()); gui.render(graphics, -100, -100, 0f); graphics.flush();
            }
            try (var image = Screenshot.takeScreenshot(target)) { image.writeToFile(output); }
        } finally {
            mc.screen = previousScreen; target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
            modelView.popPose(); RenderSystem.applyModelViewMatrix(); RenderSystem.setProjectionMatrix(projection, sorting);
            mc.options.guiScale().set(previousScale); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), previousWidth, previousHeight);
            GLFW.glfwPollEvents(); mc.resizeDisplay();
        }
        return row;
    }
    private static final class AuditGui extends ModularUIGuiContainer {
        AuditGui(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc, int guiWidth, int guiHeight) { minecraft = mc; font = mc.font; width = guiWidth; height = guiHeight; super.init(); }
        @Override protected void containerTick() {}
        @Override public void removed() {}
        @Override public void onClose() {}
    }
    private static final class AuditPlayer extends Player {
        net.minecraft.world.entity.player.Inventory inventory;
        private AuditPlayer(Level level) { super(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "CloudCapacityUiAudit")); }
        @Override public net.minecraft.world.entity.player.Inventory getInventory() { return inventory; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return true; }
    }
    private static void check(boolean value, String reason, List<String> checks) {
        if (!value) throw new AssertionError(reason); checks.add(reason);
    }
}
