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

    public CloudNativeUiChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.cloudNativeUiAudit")) return;
        var mc = Minecraft.getInstance();
        if (phase == 0) {
            directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || mc.level != null || mc.getSingleplayerServer() != null
                    || !directory.startsWith(ROOT) || directory.equals(ROOT)
                    || !Files.isRegularFile(directory.resolve("cloud-native-ui.marker"))) return;
        }
        try {
            require(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null,
                    "Title-screen synthetic fixture has no world or network connection");
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
                if (++phase > 8) finish(null);
                else open(phase - 1);
                elapsed = 0;
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
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
        field(panel, "snapshot").set(panel, snapshot);
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
        report.put("scope", "Actual registered machine Fancy roots, ME flag nodes and GTLCore provider/widget/drawWirelessPage. Synthetic client-only entries; request opening replaced before native paint. No world, real C2S, binding mutation or automatic-tab-installation claim.");
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
