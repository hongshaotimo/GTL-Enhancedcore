package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.ui.*;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

/** Real root input and completed rendering; synthetic snapshots isolate the UI/protocol boundary. */
public final class CloudOrderRecoveryClientChecks {
    private static final int[][] SIZES = {{960, 600, 2}, {1280, 720, 3}, {640, 480, 2}};
    private static final String PREFIX = "gtl_enhancedcore.cloud.ui.";
    private static final String[] IDS = {
            "3f248c6f-e6f9-4000-8000-000000000001", "3f248c6f-e6f9-4000-8000-000000000002",
            "3f248c6f-e6f9-4000-8000-000000000003", "3f248c6f-e6f9-4000-8000-000000000004",
            "3f248c6f-e6f9-4000-8000-000000000005"};
    private static Run active;
    private static boolean registered;

    private CloudOrderRecoveryClientChecks() {}

    /** Call once on the title-screen client thread, then call step from ClientTick.END. */
    public static void begin(Path directory, List<String> checks) throws Exception {
        if (active != null) throw new IllegalStateException("Order recovery UI audit already active");
        var run = new Run(directory, checks);
        active = run;
        if (!registered) {
            MinecraftForge.EVENT_BUS.addListener(CloudOrderRecoveryClientChecks::renderBoundary);
            registered = true;
        }
        try { run.begin(); }
        catch (Exception | Error failure) {
            try { run.report(failure); } finally { cleanup(); }
            throw failure;
        }
    }

    /** Returns true after all three viewports have completed, with the original screen restored. */
    public static boolean step() throws Exception {
        if (active == null) return true;
        var run = active;
        try {
            if (!run.step()) return false;
            run.report(null); cleanup(); return true;
        } catch (Exception | Error failure) {
            try { run.report(failure); } finally { cleanup(); }
            throw failure;
        }
    }

    public static void cleanup() {
        var run = active; active = null;
        if (run != null) run.cleanup();
    }

    private static void renderBoundary(TickEvent.RenderTickEvent event) {
        var run = active;
        if (run == null || event.phase != TickEvent.Phase.END) return;
        try { run.rendered(); }
        catch (Throwable failure) { run.renderFailure = failure; }
    }

    private static final class Run {
        private final Path directory;
        private final List<String> sharedChecks;
        private final List<String> checks = new ArrayList<>();
        private final List<Map<String, Object>> captures = new ArrayList<>(), actions = new ArrayList<>(), queries = new ArrayList<>();
        private final net.minecraft.client.gui.screens.Screen previousScreen;
        private final int previousWidth, previousHeight, previousScale;
        private Fixture fixture;
        private ModularUI ui;
        private Widget panel;
        private Screen screen;
        private CompoundTag snapshot;
        private Throwable renderFailure;
        private String pendingCapture;
        private boolean library;
        private int size, phase, ticks, frames, observedRender, querySerial, queryPage, viewportActionStart;

        Run(Path directory, List<String> sharedChecks) throws Exception {
            this.directory = directory.toRealPath(); this.sharedChecks = sharedChecks;
            var mc = Minecraft.getInstance(); previousScreen = mc.screen;
            previousWidth = mc.getWindow().getWidth(); previousHeight = mc.getWindow().getHeight();
            previousScale = mc.options.guiScale().get();
        }

        void begin() throws Exception {
            var mc = Minecraft.getInstance();
            check(directory.equals(mc.gameDirectory.toPath().toRealPath())
                            && Files.isRegularFile(directory.resolve("cloud-compat.marker")),
                    "Order recovery UI runs only in the marked independent compatibility instance");
            check(mc.level == null && mc.getSingleplayerServer() == null && mc.getConnection() == null
                            && mc.screen instanceof TitleScreen,
                    "Order recovery UI opens no world, server or network connection");
            fixture = fixture();
            Files.createDirectories(directory.resolve("screenshots")); startViewport();
        }

        private void startViewport() throws Exception {
            var mc = Minecraft.getInstance(); var target = SIZES[size];
            mc.options.guiScale().set(target[2]);
            GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), target[0], target[1]);
            GLFW.glfwPollEvents(); mc.resizeDisplay();
            phase = 0; viewportActionStart = actions.size(); open(true);
            apply(state(true, 0, 1, rows(0, 3), 3, revision())); scene("central-three-records");
        }

        boolean step() throws Exception {
            if (++ticks > 3600) throw new AssertionError("Order recovery UI audit timed out");
            if (renderFailure != null) throw new AssertionError("Order recovery UI rendering failed", renderFailure);
            if (Minecraft.getInstance().getOverlay() != null || frames < 3 || pendingCapture != null) return false;
            switch (phase++) {
                case 0 -> {
                    viewport();
                    check(snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND).size() == 3
                                    && !snapshot.getBoolean("recoveryOwner") && snapshot.getBoolean("recoveryBusy"),
                            "Central list contains the three frozen order records with non-owner/busy flags");
                    for (int i = 0; i < 3; i++) check(widget("cloud.recovery.review." + i).isActive(),
                            "Order detail button " + i + " remains active independently of transfer ownership/busy state");
                    openDetail(0); scene("central-detail-first");
                }
                case 1 -> { click("cloud.recovery.details.back"); openDetail(1); scene("central-detail-second"); }
                case 2 -> { click("cloud.recovery.details.back"); openDetail(2); hover("cloud.recovery.details.location"); scene("central-detail-third-hover"); }
                case 3 -> {
                    unhover(); apply(state(true, 0, 1, rows(0, 2), 2, revision() + 1));
                    check(!flag("recoveryDetailsShown") && flag("recoveryShown"),
                            "Authoritative disappearance of the selected frozen record returns details to the central list");
                    check(widget("cloud.recovery.review.0").isVisible() && widget("cloud.recovery.review.1").isVisible()
                                    && !widget("cloud.recovery.review.2").isVisible() && !widget("cloud.recovery.review.3").isVisible(),
                            "Restored central record disappears without a stale actionable row");
                    scene("central-record-disappeared");
                }
                case 4 -> { apply(state(true, 0, 2, rows(0, 4), 5, revision() + 2)); scene("central-page-first"); }
                case 5 -> {
                    queryClick("cloud.recovery.next", 1); apply(state(true, 1, 2, rows(4, 1), 5, revision() + 2));
                    check(widget("cloud.recovery.review.0").isVisible() && !widget("cloud.recovery.review.1").isVisible()
                                    && !widget("cloud.recovery.review.2").isVisible() && !widget("cloud.recovery.review.3").isVisible(),
                            "Central final recovery page exposes only its one authoritative row");
                    scene("central-page-final");
                }
                case 6 -> {
                    queryClick("cloud.recovery.previous", 0); apply(state(true, 0, 1, new ListTag(), 0, revision() + 3));
                    for (int i = 0; i < 4; i++) check(!widget("cloud.recovery.review." + i).isVisible(),
                            "Central empty page hides action " + i);
                    check(!widget("cloud.recovery.previous").isActive() && !widget("cloud.recovery.next").isActive(),
                            "Central empty recovery page disables pagination");
                    scene("central-page-empty");
                }
                case 7 -> {
                    click("cloud.recovery.back"); check(!flag("recoveryShown"), "Central root back returns to patterns");
                    click("cloud.recovery"); check(flag("recoveryShown"), "Central root recovery entry remains manually reachable after return");
                    click("cloud.recovery.back");
                    check(actions.size() == viewportActionStart, "Central details, pagination and return emit no mutation packet");
                    open(false); apply(state(false, 0, 1, rows(0, 3), 3, revision() + 10)); scene("buffer-patterns");
                }
                case 8 -> {
                    viewport(); click("cloud.orders.open");
                    check(flag("managing") && !flag("recovering") && !flag("confirmingCancel"),
                            "Buffer actual root order button opens the existing order/refund page");
                    scene("buffer-orders");
                }
                case 9 -> {
                    click("cloud.recovery.open"); check(flag("recovering"), "Buffer actual order page opens frozen-order recovery");
                    check(!widget("cloud.recovery.claim").isActive(), "Recovery claim starts disabled without a selection");
                    scene("buffer-recovery-list");
                }
                case 10 -> { disallowed(0); hover("cloud.recovery.reason"); scene("buffer-target-busy"); }
                case 11 -> { unhover(); disallowed(1); hover("cloud.recovery.reason"); scene("buffer-mode-mismatch"); }
                case 12 -> {
                    unhover(); int before = actions.size(); click("cloud.recovery.2");
                    check(actions.size() == before && IDS[2].equals(selection()) && widget("cloud.recovery.claim").isActive(),
                            "Selecting an allowed frozen row preserves its UUID and enables claim without mutation");
                    scene("buffer-selected-ready");
                }
                case 13 -> {
                    int before = actions.size(); click("cloud.recovery.claim");
                    check(actions.size() == before + 1, "Claim root click emits exactly one actual action 211");
                    var action = actions.get(actions.size() - 1);
                    check("recover".equals(action.get("action")) && IDS[2].equals(action.get("recoveryId"))
                                    && Long.valueOf(snapshot.getLong("revision")).equals(action.get("revision")),
                            "Actual recovery packet carries recover, the exact selected UUID and exact snapshot revision");
                    apply(state(false, 0, 1, rows(0, 2), 2, revision() + 11));
                    check(selection().isEmpty() && !widget("cloud.recovery.claim").isActive()
                                    && !widget("cloud.recovery.2").isVisible(),
                            "Restoration response removes the record, clears selection and disables repeat claim");
                    scene("buffer-restored-record-removed");
                }
                case 14 -> {
                    int before = actions.size(); clickOptional("cloud.recovery.claim");
                    check(actions.size() == before, "No repeat recovery action after the selected record disappears");
                    apply(state(false, 0, 2, rows(0, 4), 5, revision() + 12)); scene("buffer-page-first");
                }
                case 15 -> {
                    click("cloud.recovery.2"); check(widget("cloud.recovery.claim").isActive(), "Allowed selection is active before page change");
                    queryClick("cloud.recovery.next", 1);
                    check(selection().isEmpty(), "Actual buffer next-page input immediately clears the previous UUID");
                    apply(state(false, 1, 2, rows(4, 1), 5, revision() + 12));
                    check(widget("cloud.recovery.0").isVisible() && !widget("cloud.recovery.1").isVisible()
                                    && !widget("cloud.recovery.2").isVisible() && !widget("cloud.recovery.3").isVisible()
                                    && !widget("cloud.recovery.claim").isActive(),
                            "Buffer final page has one row and cannot claim a selection from the previous page");
                    scene("buffer-page-final");
                }
                case 16 -> {
                    queryClick("cloud.recovery.previous", 0); apply(state(false, 0, 1, new ListTag(), 0, revision() + 13));
                    for (int i = 0; i < 4; i++) check(!widget("cloud.recovery." + i).isVisible(), "Buffer empty page hides row " + i);
                    int before = actions.size(); clickOptional("cloud.recovery.claim");
                    check(selection().isEmpty() && !widget("cloud.recovery.claim").isActive() && actions.size() == before,
                            "Empty buffer page has no recoverable selection and emits no recovery action");
                    scene("buffer-page-empty");
                }
                case 17 -> {
                    click("cloud.recovery.back"); check(flag("managing") && !flag("recovering"), "Recovery back returns to the original orders page");
                    int before = actions.size(); click("cloud.orders.cancel");
                    check(flag("confirmingCancel") && actions.size() == before,
                            "Original cancellation entry still requires its visible material/energy confirmation");
                    check(snapshot.getInt("refundTypes") == 2 && snapshot.getInt("completedOutputTypes") == 1,
                            "Order/refund page retains authoritative pending refund and completed-output counters");
                    scene("buffer-original-cancel-confirmation");
                }
                case 18 -> {
                    int before = actions.size(); click("cloud.orders.keep");
                    check(!flag("confirmingCancel") && actions.size() == before, "Keep-orders root input leaves cancellation without mutation");
                    click("cloud.orders.cancel"); click("cloud.orders.confirm");
                    check(actions.size() == before + 1 && "cancel".equals(actions.get(before).get("action"))
                                    && Boolean.TRUE.equals(actions.get(before).get("confirm"))
                                    && Long.valueOf(snapshot.getLong("revision")).equals(actions.get(before).get("revision")),
                            "Original confirmed stop/refund sends cancel with confirm=true and the current revision");
                    assertAction("cloud.orders.resume", "resume"); assertAction("cloud.orders.reconnect", "reconnect");
                    scene("buffer-original-order-actions");
                }
                case 19 -> {
                    click("cloud.orders.back"); check(!flag("managing"), "Original orders back returns to the native pattern page");
                    var names = actions.subList(viewportActionStart, actions.size()).stream().map(a -> a.get("action")).toList();
                    check(names.equals(List.of("recover", "cancel", "resume", "reconnect")),
                            "Only the intended recovery, confirmed refund, resume and reconnect mutations were emitted");
                    scene("buffer-return-patterns");
                }
                case 20 -> { if (++size == SIZES.length) return true; startViewport(); }
                default -> throw new AssertionError("Unexpected recovery UI phase " + phase);
            }
            return false;
        }

        private long revision() { return 91_000L + size * 100L; }

        private void openDetail(int row) throws Exception {
            int before = actions.size(); click("cloud.recovery.review." + row);
            check(flag("recoveryDetailsShown") && IDS[row].equals(field(panel, "recoveryDetailId")),
                    "Central root detail input opens the exact frozen record " + row);
            check(actions.size() == before, "Opening frozen record " + row + " is local inspection with no mutation packet");
        }

        private void disallowed(int row) throws Exception {
            int before = actions.size(); click("cloud.recovery." + row);
            check(IDS[row].equals(selection()) && !widget("cloud.recovery.claim").isActive(),
                    "Disallowed frozen row " + row + " is inspectable while claim remains disabled");
            clickOptional("cloud.recovery.claim"); check(actions.size() == before,
                    "Actual disabled-claim input emits no action for disallowed row " + row);
        }

        private void assertAction(String id, String name) {
            int before = actions.size(); click(id);
            check(actions.size() == before + 1 && name.equals(actions.get(before).get("action"))
                            && Long.valueOf(snapshot.getLong("revision")).equals(actions.get(before).get("revision")),
                    "Original " + name + " root input retains its action and current revision");
        }

        private void queryClick(String id, int page) {
            int before = queries.size(); click(id);
            check(queries.size() == before + 1 && queryPage == page,
                    "Actual " + id + " sends exactly one complete query for recovery page " + page);
        }

        private void open(boolean library) {
            this.library = library; snapshot = null; querySerial = 0; queryPage = 0;
            var player = fixture.player();
            CloudPatternUiHost host = new CloudPatternUiHost() {
                public CompoundTag cloudUiSnapshot(ServerPlayer viewer, String search, String type, int page, String selected) {
                    return snapshot == null ? new CompoundTag() : snapshot.copy();
                }
                public void cloudUiAction(ServerPlayer viewer, String action, CompoundTag payload) {}
            };
            class Holder implements IUIHolder, IFancyUIProvider {
                public ModularUI createUI(Player viewer) { return CloudPatternUi.create(host, this, viewer, library); }
                public boolean isInvalid() { return false; }
                public boolean isRemote() { return true; }
                public void markAsDirty() {}
                public Widget createMainPage(FancyMachineUIWidget fancy) {
                    return library ? new CloudPatternLibraryWidget(host) : new CloudPatternBufferWidget(host);
                }
                public IGuiTexture getTabIcon() { return IGuiTexture.EMPTY; }
                public Component getTitle() { return Component.translatable(PREFIX + (library ? "library" : "buffer")); }
            }
            ui = new Holder().createUI(player); ui.initWidgets(); panel = widget(library ? "cloud.library" : "cloud.buffer");
            panel.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack, boolean first, boolean second) { return false; }
                public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                    var packet = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        writer.accept(packet);
                        if (id == 210) {
                            String search = packet.readUtf(96), type = packet.readUtf(128); int page = packet.readVarInt();
                            String selected = packet.readUtf(128); queryPage = packet.readVarInt(); CloudPatternSearch.read(packet);
                            querySerial = packet.readVarInt();
                            check(packet.readableBytes() == 0, "Actual 210 query fully decodes including recovery page and request serial");
                            queries.add(Map.of("viewport", size, "library", library, "search", search, "type", type,
                                    "page", page, "selected", selected, "recoveryPage", queryPage, "requestSerial", querySerial));
                        } else if (id == 211) {
                            String name = packet.readUtf(32); var payload = packet.readNbt();
                            check(payload != null && packet.readableBytes() == 0, "Actual 211 action fully decodes with its NBT payload");
                            var action = new LinkedHashMap<String, Object>(); action.put("packet", 211); action.put("viewport", size);
                            action.put("library", library); action.put("action", name); action.put("recoveryId", payload.getString("recoveryId"));
                            action.put("revision", payload.getLong("revision")); action.put("confirm", payload.getBoolean("confirm"));
                            action.put("payload", payload.toString()); actions.add(action);
                        }
                    } finally { packet.release(); }
                }
            });
            screen = new Screen(new Gui(ui)); Minecraft.getInstance().setScreen(screen); player.containerMenu = ui.getModularUIContainer();
            check(ui.getSlotMap().keySet().stream().filter(slot -> slot.container == player.getInventory()).count() == 36,
                    (library ? "Central" : "Buffer") + " recovery UI retains all 36 native player inventory slots");
        }

        private void apply(CompoundTag next) throws Exception {
            snapshot = next.copy(); snapshot.putInt("requestSerial", querySerial);
            var packet = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.writeNbt(snapshot); panel.readUpdateInfo(212, packet);
                check(packet.readableBytes() == 0 && snapshot.equals(field(panel, "snapshot")),
                        "Native panel consumes and accepts the exact authoritative 212 snapshot with the current serial");
            } finally { packet.release(); }
        }

        private static CompoundTag state(boolean central, int page, int pages, ListTag rows, int count, long revision) {
            var tag = new CompoundTag(); tag.putLong("revision", revision); tag.putInt("page", 0); tag.putInt("pages", 1);
            tag.put("entries", new ListTag()); tag.put("types", new ListTag()); tag.putInt("recovery", count);
            tag.putInt("recoveryPage", page); tag.putInt("recoveryPages", pages); tag.put("recoveryEntries", rows);
            tag.putBoolean("recoveryOwner", false); tag.putBoolean("recoveryBusy", true);
            tag.putBoolean("networkConnected", true); tag.putString("status", PREFIX + "orders_paused");
            tag.putString("networkStatus", PREFIX + "me_connected"); tag.putBoolean("tasksHealthy", true);
            tag.putBoolean("accepting", false); tag.putBoolean("resumeAllowed", true); tag.putBoolean("hasOrigin", true);
            tag.putBoolean("returnNetworkReady", false); tag.putInt("queued", 8); tag.putInt("active", 3);
            tag.putInt("refundTypes", 2); tag.putInt("completedOutputTypes", 1);
            tag.putString("mode", "gtceu:assembler");
            var modes = new ListTag(); var mode = new CompoundTag(); mode.putString("id", "gtceu:assembler");
            mode.putBoolean("current", true); mode.putBoolean("available", true); modes.add(mode); tag.put("modes", modes);
            return tag;
        }

        private static ListTag rows(int from, int count) {
            var rows = new ListTag();
            for (int i = from; i < from + count; i++) {
                var row = new CompoundTag(); row.putString("id", IDS[i]); row.putString("operation", IDS[i]);
                row.putString("kind", "order"); row.putString("status", i == 1 ? "NEEDS_REVIEW" : "FROZEN");
                row.putString("mode", i == 1 ? "gtceu:chemical_reactor" : "gtceu:assembler");
                row.putString("machineDefinition", "gtceu:circuit_assembly_line"); row.putString("sourceDimension", "minecraft:overworld");
                row.putString("sourcePos", "(" + (120 + i * 16) + ", 64, -48)"); row.putInt("jobCount", 17 + i * 12);
                row.putBoolean("multipleMachines", false); row.putBoolean("recoverAllowed", i >= 2);
                row.putString("recoverReason", i == 0 ? PREFIX + "recovery_target_busy"
                        : i == 1 ? PREFIX + "recovery_mode_mismatch" : ""); rows.add(row);
            }
            return rows;
        }

        private void viewport() {
            var window = Minecraft.getInstance().getWindow(); int[] target = SIZES[size];
            check(window.getWidth() == target[0] && window.getHeight() == target[1]
                            && window.getGuiScale() == target[2],
                    "Actual framebuffer and GUI scale match " + target[0] + "x" + target[1] + "/GUI" + target[2]);
            check(ui.mainGroup.getSizeWidth() <= window.getGuiScaledWidth()
                            && ui.mainGroup.getSizeHeight() <= window.getGuiScaledHeight(),
                    "Entire native Fancy shell fits viewport " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight());
            if (size == 2) check(window.getGuiScaledWidth() == 320 && window.getGuiScaledHeight() == 240,
                    "Smallest rendered scene is the required actual 320x240 logical viewport");
        }

        private void scene(String name) { frames = 0; observedRender = screen.renders; pendingCapture = "order-recovery-" + size + "-" + name + ".png"; }

        void rendered() throws Exception {
            var mc = Minecraft.getInstance();
            if (screen == null || mc.screen != screen || mc.getOverlay() != null || screen.renders == observedRender) return;
            observedRender = screen.renders; frames++;
            if (frames < 3 || pendingCapture == null) return;
            String name = pendingCapture;
            try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                shot.writeToFile(directory.resolve("screenshots").resolve(name));
            }
            var window = mc.getWindow(); var capture = new LinkedHashMap<String, Object>();
            capture.put("image", "screenshots/" + name); capture.put("viewport", size); capture.put("library", library);
            capture.put("framebuffer", List.of(window.getWidth(), window.getHeight()));
            capture.put("logical", List.of(window.getGuiScaledWidth(), window.getGuiScaledHeight()));
            capture.put("guiScale", window.getGuiScale()); capture.put("completedSceneFrames", frames);
            capture.put("boundary", "RenderTick.END after actual Screen.render"); captures.add(capture); pendingCapture = null;
        }

        private Widget widget(String id) {
            var widget = Objects.requireNonNull(ui.getFirstWidgetById("^" + java.util.regex.Pattern.quote(id) + "$"), id);
            if (!id.equals(widget.getId())) throw new AssertionError("Exact native widget identity: " + id); return widget;
        }

        private boolean clickOptional(String id) {
            var widget = widget(id); var position = widget.getPosition();
            int x = position.x + widget.getSizeWidth() / 2, y = position.y + widget.getSizeHeight() / 2;
            boolean handled = ui.mainGroup.mouseClicked(x, y, 0); ui.mainGroup.mouseReleased(x, y, 0); return handled;
        }

        private void click(String id) { check(clickOptional(id), "Actual native root dispatches " + id); }
        private void hover(String id) { var widget = widget(id); var position = widget.getPosition(); screen.mouseX = position.x + 8; screen.mouseY = position.y + 5; }
        private void unhover() { screen.mouseX = screen.mouseY = -100; }
        private boolean flag(String name) throws Exception { return (boolean)field(panel, name); }
        private String selection() throws Exception { return (String)field(panel, "recoveryId"); }

        private static Object field(Object target, String name) throws Exception {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                try { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(target); }
                catch (NoSuchFieldException ignored) { }
            }
            throw new NoSuchFieldException(name);
        }

        private void check(boolean value, String label) {
            if (!value) throw new AssertionError(label + " [viewport=" + size + ", phase=" + phase + "]");
            checks.add(label); sharedChecks.add("order recovery UI: " + label);
        }

        void report(Throwable failure) throws Exception {
            var report = new LinkedHashMap<String, Object>(); report.put("status", failure == null ? "PASS" : "FAIL");
            if (failure != null) report.put("error", failure.toString()); report.put("checks", checks);
            report.put("screenshots", captures); report.put("actions", actions); report.put("queries", queries);
            report.put("language", Minecraft.getInstance().getLanguageManager().getSelected());
            report.put("scope", "Actual production Fancy UI root mouse input, actual 210/211 packet encoding and completed native framebuffer rendering in three viewports. Synthetic authoritative snapshots test the client UI/protocol boundary. No server restoration or refund execution is claimed by this client audit; actual restoration belongs to CloudPatternFlowRuntimeChecks.");
            Files.writeString(directory.resolve("order-recovery-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }

        void cleanup() {
            var mc = Minecraft.getInstance(); mc.setScreen(previousScreen);
            if (fixture != null) fixture.holders().forEach(holder -> holder.setLevel(null));
            mc.options.guiScale().set(previousScale); GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), previousWidth, previousHeight);
            GLFW.glfwPollEvents(); mc.resizeDisplay();
        }
    }

    /** Same client fixture subset as the native GUI audit, without any bootstrap dependency. */
    private static Fixture fixture() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe)unsafeField.get(null);
        var world = (AuditWorld)unsafe.allocateInstance(AuditWorld.class);
        set(world, "blockEntities", new HashMap<>()); set(world, "renderedBlocks", new HashMap<>());
        set(world, "proxyWorld", new java.lang.ref.WeakReference<Level>(null));
        ObfuscationReflectionHelper.setPrivateValue(Level.class, world, true, "f_46443_");
        ObfuscationReflectionHelper.setPrivateValue(Level.class, world, Level.OVERWORLD, "f_46421_");
        var holders = new ArrayList<MetaMachineBlockEntity>();
        var controller = (WorkableElectricMultiblockMachine)place("gtceu:circuit_assembly_line", new BlockPos(0, 80, 0), world, holders).getMetaMachine();
        set(controller, "isFormed", true); controller.getMultiblockState().error = null;
        var parts = new ArrayList<IMultiPart>();
        var buffer = place("gtladditions:me_super_pattern_buffer", new BlockPos(1, 80, 0), world, holders).getMetaMachine();
        parts.add((IMultiPart)buffer); IvBuffers.state(buffer).owner = "fixture/gtceu:circuit_assembly_line";
        ((IvBufferAccess)buffer).iv$syncDedicated(true);
        parts.add((IMultiPart)place("gtl_enhancedcore:galaxy_connection_target_hatch", new BlockPos(2, 80, 0), world, holders).getMetaMachine());
        set(controller, "parts", parts); set(controller, "partPositions", parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
        ((IvNativeAccess)controller.getRecipeLogic()).iv$syncMode(IvMachineScope.nativeTarget(controller), false);
        var player = (AuditPlayer)unsafe.allocateInstance(AuditPlayer.class);
        player.inventory = new net.minecraft.world.entity.player.Inventory(player);
        ObfuscationReflectionHelper.setPrivateValue(net.minecraft.world.entity.Entity.class, player, world, "f_19853_");
        return new Fixture(player, holders);
    }

    private static MetaMachineBlockEntity place(String id, BlockPos position, TrackedDummyWorld world, List<MetaMachineBlockEntity> holders) {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        if (definition == null) throw new AssertionError("Missing real definition: " + id);
        var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(position, definition.defaultBlockState());
        holder.setLevel(world); world.blockEntities.put(position, holder);
        world.renderedBlocks.put(position, new com.lowdragmc.lowdraglib.utils.BlockInfo(definition.defaultBlockState()));
        holders.add(holder); return holder;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private record Fixture(Player player, List<MetaMachineBlockEntity> holders) {}

    private static final class AuditWorld extends TrackedDummyWorld {
        private AuditWorld() { super(null); }
        @Override public Level getLevel() { return this; }
        @Override public net.minecraft.core.RegistryAccess registryAccess() { return net.minecraft.core.RegistryAccess.EMPTY; }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() { return new net.minecraft.world.item.crafting.RecipeManager(); }
        @Override public long getGameTime() { return 1000; }
        @Override public BlockPos getSharedSpawnPos() { return BlockPos.ZERO; }
        @Override public void blockEntityChanged(BlockPos position) {}
        @Override public void setBlocksDirty(BlockPos position, net.minecraft.world.level.block.state.BlockState before,
                net.minecraft.world.level.block.state.BlockState after) {}
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos position) {
            var block = renderedBlocks.get(position);
            return block == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : block.getBlockState();
        }
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position) { return blockEntities.get(position); }
    }

    private static final class AuditPlayer extends Player {
        net.minecraft.world.entity.player.Inventory inventory;
        private AuditPlayer(Level world) { super(world, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "CloudOrderUiAudit")); }
        @Override public net.minecraft.world.entity.player.Inventory getInventory() { return inventory; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return true; }
    }

    private static final class Gui extends ModularUIGuiContainer {
        Gui(ModularUI ui) { super(ui, 1); }
        void cloudTick() { super.containerTick(); }
        void initialize(Minecraft mc) {
            minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight(); super.init();
        }
    }

    private static final class Screen extends net.minecraft.client.gui.screens.Screen {
        final Gui gui; int renders, mouseX = -100, mouseY = -100;
        Screen(Gui gui) { super(Component.literal("Frozen cloud order recovery audit")); this.gui = gui; }
        @Override protected void init() { gui.initialize(minecraft); }
        @Override public void tick() { gui.cloudTick(); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) {
            gui.renderWithTooltip(graphics, mouseX, mouseY, partial); renders++;
        }
    }
}
