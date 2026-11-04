package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets.TargetNetworkEntry;

/** Real Fancy shell and GTLCore network-page renderer with title-screen-only network cache fixtures. */
@Mod("enhancedcore_audit")
public final class CloudNativeUiChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/ui-followup-20261027");
    private static final String HOOKS = "org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeScreenHooks";
    private final List<String> checks = new ArrayList<>();
    private final List<Map<String, Object>> layouts = new ArrayList<>();
    private Path directory;
    private Player player;
    private AuditGui gui;
    private ModularUI ui;
    private FancyMachineUIWidget fancy;
    private IFancyUIProvider nativeProvider;
    private Widget nativePage;
    private int phase, elapsed;
    private boolean done;
    private CloudTerminalUiChecks.Session terminal;
    private Widget refundPanel;
    private final List<CompoundTag> refundActions = new ArrayList<>();

    public CloudNativeUiChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.cloudNativeUiAudit")) return;
        var mc = Minecraft.getInstance();
        if (phase == 0) {
            directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || mc.level != null || mc.getSingleplayerServer() != null
                    || !(directory.startsWith(ROOT) && !directory.equals(ROOT)
                    || directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-instant-upload-20261028"))
                    || directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/cloud-refund-dissolution-20261029"))
                    || directory.getParent().equals(Path.of("C:/Users/Administrator/.codex/worktrees/pr4-cloud-integration/GTL-Enhancedcore/_audit/pr4-integration-20261028")))
                    || !Files.isRegularFile(directory.resolve("cloud-native-ui.marker"))) return;
        }
        try {
            require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null,
                    "Title-screen synthetic fixture has no world or network connection");
            if (Boolean.getBoolean("gtl.cloudRefundUiAudit")) { refundTick(); return; }
            if (phase >= 9) {
                if (++elapsed < 4 || terminal.frames() < 4) return;
                if (phase == 9) { terminal.verify(true, this::require); terminal.verifyRecovery(this::require); phase = 10; elapsed = 0; }
                else { screenshot("terminal-recovered.png"); finish(null); }
                return;
            }
            if (phase == 0) {
                player = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false).player();
                open(0); phase = 1;
            } else if (++elapsed >= 5 && gui.frames >= 4) {
                int sample = phase - 1;
                String machine = sample < 4 ? "library" : "buffer";
                String state = sample % 4 < 2 ? "offline" : "online";
                String page = sample % 2 == 0 ? "main" : "network";
                screenshot(machine + "-" + page + "-" + state + ".png");
                if (sample % 2 == 1) verifyNativeLayout(state.equals("online"));
                if (++phase > 8) { terminal = CloudTerminalUiChecks.open(player); terminal.bound(true); }
                else open(phase - 1);
                elapsed = 0;
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }

    private void refundTick() throws Exception {
        if (phase == 0) {
            String language = Minecraft.getInstance().getLanguageManager().getSelected();
            require(language.equals("zh_cn") || language.equals("en_us"), "Refund screenshots use declared supported language: " + language);
            refundTextWidths();
            player = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false).player();
            open(6); refundPanel = ui.getFirstWidgetById("cloud.buffer");
            refundSnapshot(false); captureRefundActions(); phase = 1; elapsed = 0; return;
        }
        if (++elapsed < 25 || gui.frames < 4) return;
        switch (phase) {
            case 1 -> { screenshot("refund-main.png"); clickRefund("open"); refundPage("orders", true); }
            case 2 -> {
                screenshot("refund-orders.png"); refundBounds("orders"); clickRefund("cancel");
                refundPage("cancellation", true); require(refundActions.isEmpty(), "Opening confirmation does not cancel orders");
            }
            case 3 -> {
                screenshot("refund-confirmation.png"); refundBounds("cancellation"); clickRefund("keep");
                refundPage("orders", true); require(refundActions.isEmpty(), "Keep orders returns without a server action");
            }
            case 4 -> {
                screenshot("refund-kept.png"); clickRefund("cancel"); clickRefund("confirm");
                require(refundActions.size() == 1 && refundActions.get(0).getString("action").equals("cancel")
                        && refundActions.get(0).getCompound("payload").getBoolean("confirm")
                        && refundActions.get(0).getCompound("payload").getLong("revision") == 42,
                        "Only explicit confirmation emits cancel with current revision and confirm=true");
                refundSnapshot(true, true); refundPage("orders", true);
            }
            case 5 -> {
                screenshot("refund-online-pending.png"); refundBounds("orders");
                refundSnapshot(true);
            }
            case 6 -> {
                screenshot("refund-offline-pending.png"); refundBounds("orders");
                require(!ui.getFirstWidgetById("cloud.orders.cancel").isActive(), "No queued jobs disables cancel");
                require(ui.getFirstWidgetById("cloud.orders.reconnect").isActive(), "Offline pending stock offers source reconnect");
                clickRefund("back");
                require(((Widget)field(refundPanel, "patterns").get(refundPanel)).isVisible(), "Back restores pattern grid");
            }
            case 7 -> { screenshot("refund-patterns-returned.png"); finish(null); return; }
            default -> throw new AssertionError("Unknown refund stage " + phase);
        }
        phase++; elapsed = 0; gui.frames = 0;
    }
    private void refundSnapshot(boolean pending) throws Exception { refundSnapshot(pending, !pending); }
    private void refundSnapshot(boolean pending, boolean online) throws Exception {
        var snapshot = ((CompoundTag)field(refundPanel, "snapshot").get(refundPanel)).copy();
        snapshot.putLong("revision", 42); snapshot.putInt("queued", pending ? 0 : 12); snapshot.putInt("active", pending ? 0 : 3);
        snapshot.putBoolean("accepting", !pending); snapshot.putBoolean("tasksHealthy", true);
        snapshot.putBoolean("hasOrigin", true); snapshot.putBoolean("returnNetworkReady", online);
        snapshot.putBoolean("networkConnected", online); snapshot.putBoolean("resumeAllowed", false);
        snapshot.putInt("refundTypes", pending ? 8 : 0); snapshot.putInt("completedOutputTypes", pending ? 2 : 0);
        snapshot.putInt("requestSerial", field(refundPanel, "requestSerial").getInt(refundPanel));
        var packet = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { packet.writeNbt(snapshot); refundPanel.readUpdateInfo(212, packet); } finally { packet.release(); }
    }
    private void refundTextWidths() {
        var font = Minecraft.getInstance().font;
        for (String key : List.of("unused_return", "finished_return", "running_loss", "energy_loss", "cpu_cancel")) {
            String text = net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.cloud.ui.orders_" + key).getString();
            int width = font.width(text);
            layouts.add(Map.of("translation", key, "text", text, "pixels", width, "available", 162));
            require(width <= 162, "Full confirmation warning fits without clipping: " + key + " (" + width + "/162)");
        }
        for (String key : List.of("returning", "return_waiting", "nothing_pending")) {
            String text = net.minecraft.network.chat.Component.translatable("gtl_enhancedcore.cloud.ui.orders_" + key).getString();
            int width = font.width(text);
            layouts.add(Map.of("translation", key, "text", text, "pixels", width, "available", 138));
            require(width <= 138, "Complete refund status fits without clipping: " + key + " (" + width + "/138)");
        }
    }
    private void captureRefundActions() {
        refundPanel.setUiAccess(new WidgetUIAccess() {
            public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
            public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
            public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                if (id != 211) return;
                var packet = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                try { writer.accept(packet); var action = new CompoundTag(); action.putString("action", packet.readUtf(32));
                    action.put("payload", Objects.requireNonNull(packet.readNbt())); refundActions.add(action);
                } finally { packet.release(); }
            }
        });
    }
    private void clickRefund(String name) {
        Widget widget = ui.getFirstWidgetById("cloud.orders." + name);
        require(widget != null && widget.isVisible() && widget.isActive(), "Clickable real refund widget: " + name);
        var position = widget.getPosition();
        require(widget.mouseClicked(position.x + widget.getSizeWidth() / 2.0, position.y + widget.getSizeHeight() / 2.0, 0),
                "Real button mouseClicked handles: " + name);
    }
    private void refundPage(String name, boolean visible) throws Exception {
        require(((Widget)field(refundPanel, name).get(refundPanel)).isVisible() == visible, "Refund page visibility: " + name);
        require(!((Widget)field(refundPanel, "patterns").get(refundPanel)).isVisible(), "Order pages hide pattern controls");
    }
    private void refundBounds(String name) throws Exception {
        var group = (WidgetGroup)field(refundPanel, name).get(refundPanel); var origin = group.getPosition();
        var rows = new ArrayList<Map<String, Object>>();
        for (Widget child : group.widgets) {
            var point = child.getPosition(); int x = point.x - origin.x, y = point.y - origin.y;
            require(x >= 0 && y >= 0 && x + child.getSizeWidth() <= group.getSizeWidth()
                    && y + child.getSizeHeight() <= group.getSizeHeight(), "Refund page child stays within " + name + " bounds");
            rows.add(Map.of("class", child.getClass().getSimpleName(), "x", x, "y", y,
                    "width", child.getSizeWidth(), "height", child.getSizeHeight()));
        }
        layouts.add(Map.of("page", name, "width", group.getSizeWidth(), "height", group.getSizeHeight(), "widgets", rows));
    }

    private void open(int sample) throws Exception {
        boolean library = sample < 4, online = sample % 4 >= 2, network = sample % 2 == 1;
        String id = library ? "cloud_pattern_library" : "cloud_pattern_buffer";
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", id));
        require(definition != null, "Real cloud machine definition exists: " + id);
        var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(new BlockPos(40, 80, sample), definition.defaultBlockState());
        holder.setLevel(player.level());
        Object machine = holder.getMetaMachine();
        field(machine, library ? "online" : "meNetworkActive").set(machine, online);
        ui = ((IUIMachine)machine).createUI(player);
        ui.initWidgets();
        fancy = findFancy(ui.mainGroup);
        require(fancy != null && fancy.getMainPage().getClass().getName().contains("CloudPatternUi$Page"),
                "Registered machine uses real Fancy root and per-menu cloud provider: " + id);
        require(ui.getFirstWidgetById("cloud.me.status") instanceof ImageWidget,
                "Actual main panel contains ME state image: " + id);
        boolean actual = (boolean)machine.getClass().getMethod("meNetworkConnected").invoke(machine);
        require(actual == online, "Client synced ME flag controls actual host: " + id + " / " + online);
        Widget panel = ui.getFirstWidgetById(library ? "cloud.library" : "cloud.buffer");
        var snapshot = new CompoundTag(); snapshot.putString("status", "gtl_enhancedcore.cloud.ui.available");
        snapshot.putInt("pages", 1); snapshot.putInt("page", 0); snapshot.putBoolean("networkConnected", online);
        var rows = new ListTag(); var row = new CompoundTag();
        row.putString("id", "00000000-0000-0000-0000-000000000001"); row.putString("name", "Gold Ingot");
        row.put("icon", new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT).save(new CompoundTag())); rows.add(row);
        snapshot.put("entries", rows); snapshot.putInt("total", 1); snapshot.putInt("matched", 1);
        snapshot.putInt("libraryTotal", 1);
        snapshot.putString("mode", "gtceu:chemical_reactor");
        var packet = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try { packet.writeNbt(snapshot); panel.readUpdateInfo(212, packet); } finally { packet.release(); }
        require(fancy.getMainPage().createMainPage(fancy) == panel,
                "Revisiting native main page reuses its populated panel without another poll: " + id);
        var wire = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ui.mainGroup.writeInitialData(wire);
            field(panel, "snapshot").set(panel, new CompoundTag());
            ui.mainGroup.readInitialData(wire);
            require(wire.readableBytes() == 0 && snapshot.equals(field(panel, "snapshot").get(panel)),
                    "Complete native initial packet restores products and connection before any client tick: " + id);
            var image = ((ImageWidget)ui.getFirstWidgetById("cloud.me.status")).getImage();
            require(image instanceof com.lowdragmc.lowdraglib.gui.texture.ResourceTexture texture
                    && texture.imageLocation.getPath().endsWith(online ? "status_online.png" : "status_offline.png"),
                    "First-frame connection icon matches received state before ImageWidget polling: " + id);
        } finally { wire.release(); }
        sink(ui.mainGroup);
        player.containerMenu = ui.getModularUIContainer();
        gui = new AuditGui(ui);
        if (network) stageNativePage(holder.getBlockPos(), online);
        Minecraft.getInstance().setScreen(new Wrapper(gui));
        // onScreenInit normally needs a live level. Reset only native embedded data after init.
        if (network) seedNetwork(holder.getBlockPos(), online);
        sink(ui.mainGroup);
    }

    private void stageNativePage(BlockPos pos, boolean online) throws Exception {
        Class<?> providerClass = Class.forName(HOOKS + "$WirelessAeFancyPageProvider");
        var constructor = providerClass.getDeclaredConstructor(AbstractContainerScreen.class, BlockPos.class, IFancyUIProvider.class);
        constructor.setAccessible(true);
        nativeProvider = (IFancyUIProvider)constructor.newInstance(gui, pos, fancy.getMainPage());
        Class<?> pageClass = Class.forName(HOOKS + "$WirelessAeFancyPageWidget");
        var widgetConstructor = pageClass.getDeclaredConstructor(AbstractContainerScreen.class, BlockPos.class);
        widgetConstructor.setAccessible(true);
        nativePage = (Widget)widgetConstructor.newInstance(gui, pos);
        require(nativeProvider.getClass() == providerClass && nativePage.getClass() == pageClass,
                "Provider and renderer are the real GTLCore private native classes");
        // Native provider.createMainPage unconditionally sends C2S. This adapter bypasses only
        // that opening request while keeping its exact page widget, title, tabs and Fancy layout.
        IFancyUIProvider fixtureProvider = (IFancyUIProvider)Proxy.newProxyInstance(IFancyUIProvider.class.getClassLoader(),
                new Class<?>[]{IFancyUIProvider.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createMainPage")) return nativePage;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    return method.invoke(nativeProvider, args);
                });
        invokeFancy("clearUI", new Class<?>[]{});
        field(fancy, "currentHomePage").set(fancy, nativeProvider);
        field(fancy, "currentPage").set(fancy, nativeProvider);
        var pages = new ArrayList<>(fancy.getAllPages()); pages.add(nativeProvider); field(fancy, "allPages").set(fancy, pages);
        invokeFancy("setupSideTabs", new Class<?>[]{IFancyUIProvider.class}, nativeProvider);
        invokeFancy("setupFancyUI", new Class<?>[]{IFancyUIProvider.class, boolean.class}, fixtureProvider, false);
        fancy.getSideTabsWidget().selectTab(nativeProvider);
        require(fancy.getCurrentPage() == nativeProvider && fancy.getPageContainer().widgets.contains(nativePage),
                "Actual Fancy page container renders the real GTLCore wireless widget");
        seedNetwork(pos, online);
    }

    private void seedNetwork(BlockPos pos, boolean online) throws Exception {
        var entries = List.of(new TargetNetworkEntry(UUID.fromString("00000000-0000-0000-0000-000000000101"), "GregTech ME", online, online),
                new TargetNetworkEntry(UUID.fromString("00000000-0000-0000-0000-000000000102"), "Workshop storage", false, false),
                new TargetNetworkEntry(UUID.fromString("00000000-0000-0000-0000-000000000103"), "Factory network", false, false));
        var current = Minecraft.getInstance().screen;
        setStatic("embeddedScreen", current); setStatic("embeddedTargetPos", pos); setStatic("embeddedEntries", entries);
        setStatic("embeddedConnectedEntry", online ? entries.get(0) : null);
        setStatic("embeddedDisconnectableEntry", online ? entries.get(0) : null);
        setStatic("embeddedHasData", true); setStatic("embeddedLoading", false);
        setStatic("embeddedScrollOffset", 0); setStatic("embeddedRefreshAfterMillis", 0L);
        setStatic("embeddedRequestAtMillis", System.currentTimeMillis());
        setStatic("fancyScreen", current); setStatic("fancyTargetPos", pos); setStatic("fancyProvider", nativeProvider);
    }

    private void verifyNativeLayout(boolean online) throws Exception {
        require(nativePage.getSizeWidth() == 168 && nativePage.getSizeHeight() == 158,
                "Original GTLCore wireless page retains its 168x158 layout");
        var connected = staticField("embeddedConnectedEntry").get(null);
        require((connected != null) == online, "Native selected-network cache matches rendered checkmark state");
        var entries = (List<?>)staticField("embeddedEntries").get(null);
        require(entries.size() == 3 && ((TargetNetworkEntry)entries.get(0)).connected() == online,
                "Native row draw receives exact selected/unselected synthetic entries");
        var position = nativePage.getPosition();
        var record = new LinkedHashMap<String, Object>(); record.put("nativeClass", nativePage.getClass().getName());
        record.put("providerClass", nativeProvider.getClass().getName()); record.put("origin", List.of(position.x, position.y));
        record.put("size", List.of(nativePage.getSizeWidth(), nativePage.getSizeHeight())); record.put("selected", online);
        record.put("rows", entries.stream().map(e -> ((TargetNetworkEntry)e).name()).toList()); layouts.add(record);
        require(gui.frames >= 4, "Real native drawWirelessPage rendered at least four frames before screenshot");
    }

    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static Field staticField(String name) throws Exception { var f = Class.forName(HOOKS).getDeclaredField(name); f.setAccessible(true); return f; }
    private static void setStatic(String name, Object value) throws Exception { staticField(name).set(null, value); }
    private void invokeFancy(String name, Class<?>[] types, Object... args) throws Exception {
        var method = FancyMachineUIWidget.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(fancy, args);
    }
    private static FancyMachineUIWidget findFancy(Widget widget) {
        if (widget instanceof FancyMachineUIWidget result) return result;
        if (widget instanceof WidgetGroup group) for (Widget child : group.widgets) { var found = findFancy(child); if (found != null) return found; }
        return null;
    }
    private static void sink(Widget widget) {
        widget.setUiAccess(new WidgetUIAccess() {
            public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
            public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
            public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
        });
        if (widget instanceof WidgetGroup group) for (Widget child : group.widgets) sink(child);
    }
    private void screenshot(String filename) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(directory.resolve(filename)); }
        checks.add("Framebuffer screenshot: " + filename);
    }
    private void require(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks.add(label); }
    private void finish(Throwable error) {
        done = true;
        var report = new LinkedHashMap<String, Object>(); report.put("status", error == null ? "PASS" : "FAIL");
        if (error != null) report.put("error", error.toString()); report.put("checks", checks); report.put("layouts", layouts);
        report.put("refundUi", Boolean.getBoolean("gtl.cloudRefundUiAudit"));
        report.put("scope", Boolean.getBoolean("gtl.cloudRefundUiAudit") ? "Real registered cloud buffer Fancy renderer and actual button mouseClicked callbacks; synthetic order/refund snapshots and captured cancel payload only. Font widths and framebuffer screenshots in the selected language. Title screen only: no world, server actions, live refund completion or persistence claim." : "Actual registered machine Fancy roots, ME flag nodes and GTLCore provider/widget/drawWirelessPage. Synthetic client-only entries; request opening replaced before native paint. No world, real C2S, binding mutation or automatic-tab-installation claim.");
        try { Files.writeString(directory.resolve("cloud-native-ui.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static final class AuditGui extends ModularUIGuiContainer {
        int frames;
        AuditGui(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc) {
            minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth();
            height = mc.getWindow().getGuiScaledHeight(); super.init();
        }
        @Override protected void containerTick() {}
        @Override public void removed() {}
        @Override public void onClose() {}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, -100, -100, partialTick); frames++;
        }
    }
    /** Title fixture has no LocalPlayer; unrelated container ScreenEvent hooks require one. */
    private static final class Wrapper extends net.minecraft.client.gui.screens.Screen {
        private final AuditGui gui;
        Wrapper(AuditGui gui) { super(net.minecraft.network.chat.Component.literal("Native cloud wireless UI audit")); this.gui = gui; }
        @Override protected void init() { gui.initialize(minecraft); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var mc = Minecraft.getInstance(); var previous = mc.screen;
            // The native widget explicitly guards screen identity before its real renderer.
            try { mc.screen = gui; gui.render(graphics, -100, -100, partialTick); }
            finally { mc.screen = previous; }
        }
    }
}
