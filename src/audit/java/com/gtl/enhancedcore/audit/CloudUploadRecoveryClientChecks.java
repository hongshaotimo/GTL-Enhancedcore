package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gtl.enhancedcore.common.cloud.ui.*;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Isolated Forge bootstrap. Client scenes replay this task's real server snapshot evidence. */
@Mod("enhancedcore_audit")
public final class CloudUploadRecoveryClientChecks {
    private int ticks;
    private CloudUploadRecoveryRuntimeChecks server;
    public CloudUploadRecoveryClientChecks() {
        if (Boolean.getBoolean("gtl.enhancedcore.uploadRecoveryServerAudit")) MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        if (FMLEnvironment.dist == Dist.CLIENT && Boolean.getBoolean("gtl.enhancedcore.uploadRecoveryClientAudit")) new Client();
    }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 30) return;
        try {
            if (server == null) server = new CloudUploadRecoveryRuntimeChecks(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
            server.tick();
        } catch (Throwable failure) { failure.printStackTrace(); net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().halt(false); }
    }
    private static final class Client {
        private final List<String> checks = new ArrayList<>(), captures = new ArrayList<>(), actions = new ArrayList<>();
        private StellarIvGuiChecks.Fixture fixture;
        private Path directory;
        private ModularUI ui;
        private Widget panel;
        private Screen screen;
        private CompoundTag snapshot;
        private ListTag evidence;
        private int phase, queries, querySerial;
        private boolean done;
        Client() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
        private void check(boolean ok, String label) { CloudUploadRecoveryRuntimeChecks.check(ok, label, checks); }
        private void tick(TickEvent.ClientTickEvent event) {
            if (done || event.phase != TickEvent.Phase.END) return;
            var mc = Minecraft.getInstance();
            if (directory == null && (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen))) return;
            try {
                if (directory == null) {
                    directory = mc.gameDirectory.toPath().toRealPath(); Path root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
                    check(directory.startsWith(root) && !directory.equals(root) && Files.isRegularFile(directory.resolve("upload-recovery.marker"))
                            && mc.level == null && mc.getSingleplayerServer() == null, "Marked independent title-screen client with no player world");
                    Path source = Path.of(System.getProperty("gtl.enhancedcore.uploadRecoverySnapshot")).toRealPath();
                    check(source.startsWith(root.getParent()) && Files.isRegularFile(source), "Recovery snapshots come from this task's completed server fixture");
                    evidence = NbtIo.readCompressed(source.toFile()).getList("snapshots", Tag.TAG_COMPOUND);
                    check(evidence.size() == 4, "Replay actual pre-ACK, COMMITTED+ACK and uncertain-source production snapshots");
                    fixture = StellarIvGuiChecks.fixture("gtceu:circuit_assembly_line", true, false, false);
                    Files.createDirectories(directory.resolve("screenshots")); open(); apply(evidence.getCompound(0)); return;
                }
                if (mc.getOverlay() != null || screen.frames < 12) return;
                screen.frames = 0;
                switch (phase++) {
                    case 0 -> {
                        visibleRecovery(); capture("preack-owner-auto-open.png"); hover("cloud.recovery.row.0");
                    }
                    case 1 -> {
                        capture("preack-owner-evidence-hover.png"); screen.mouseX = screen.mouseY = -100;
                        String operation = snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND).getCompound(0).getString("operation");
                        click("cloud.recovery.review.0"); check(actions.contains("review_transfer:" + operation), "Real root click sends selected exact operation with review_transfer");
                        apply(evidence.getCompound(1));
                    }
                    case 2 -> { visibleRecovery(); capture("postack-committed-visible.png"); var nonowner = snapshot.copy(); nonowner.putBoolean("recoveryOwner", false); apply(nonowner); }
                    case 3 -> {
                        check(!widget("cloud.recovery.review.0").isActive(), "Non-owner recovery button is visibly disabled");
                        int before = actions.size(); clickOptional("cloud.recovery.review.0"); check(actions.size() == before, "Disabled non-owner root click emits no recovery action");
                        capture("nonowner-disabled.png"); var busy = evidence.getCompound(1).copy(); busy.putBoolean("recoveryBusy", true); apply(busy);
                    }
                    case 4 -> {
                        check(!widget("cloud.recovery.review.0").isActive(), "In-progress recovery disables repeat submission");
                        capture("busy-disabled.png"); apply(evidence.getCompound(2));
                    }
                    case 5 -> { visibleRecovery();
                        check(snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND).size() == 4, "Four actual same-library unresolved rows are simultaneously visible");
                        capture("uncertain-source-isolated.png"); hover("cloud.recovery.row.0"); }
                    case 6 -> { capture("uncertain-source-evidence-hover.png"); screen.mouseX = screen.mouseY = -100;
                        int before = queries; click("cloud.recovery.next"); check(queries == before + 1, "Native next-page click emits exactly one bounded server query");
                        apply(evidence.getCompound(3)); }
                    case 7 -> {
                        check(snapshot.getInt("recoveryPage") == 1 && snapshot.getList("recoveryEntries", Tag.TAG_COMPOUND).size() == 1,
                                "Final page keeps exact server one-row page identity");
                        check(widget("cloud.recovery.row.0").isVisible() && !widget("cloud.recovery.row.1").isVisible()
                                && !widget("cloud.recovery.row.2").isVisible() && !widget("cloud.recovery.row.3").isVisible(),
                                "Native final page shows its one authoritative row and hides all three empty rows");
                        capture("uncertain-final-page.png"); click("cloud.recovery.back");
                        check(!recoveryShown(), "Native back click returns to patterns"); apply(evidence.getCompound(3));
                        check(!recoveryShown(), "Authoritative refresh never steals the page after an explicit manual return");
                    }
                    case 8 -> { capture("manual-return-refresh.png"); click("cloud.recovery"); check(recoveryShown(), "Recovery remains directly reachable after returning to patterns"); }
                    case 9 -> { capture("manual-reopen-recovery.png"); finish(null); }
                    default -> throw new AssertionError("Unexpected client phase");
                }
            } catch (Throwable failure) { finish(failure); }
        }
        private void open() {
            var player = fixture.player();
            CloudPatternUiHost host = new CloudPatternUiHost() {
                public CompoundTag cloudUiSnapshot(ServerPlayer viewer, String s, String t, int p, String selected) { return snapshot == null ? new CompoundTag() : snapshot.copy(); }
                public void cloudUiAction(ServerPlayer viewer, String action, CompoundTag payload) {}
            };
            class Holder implements IUIHolder, IFancyUIProvider {
                public ModularUI createUI(Player viewer) { return CloudPatternUi.create(host, this, viewer, true); }
                public boolean isInvalid() { return false; }
                public boolean isRemote() { return true; }
                public void markAsDirty() {}
                public Widget createMainPage(FancyMachineUIWidget fancy) { return new CloudPatternLibraryWidget(host); }
                public IGuiTexture getTabIcon() { return IGuiTexture.EMPTY; }
                public Component getTitle() { return Component.translatable("gtl_enhancedcore.cloud.ui.library"); }
            }
            ui = new Holder().createUI(player); ui.initWidgets(); panel = widget("cloud.library");
            panel.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
                public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                    var packet = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        writer.accept(packet);
                        if (id == 210) {
                            queries++; packet.readUtf(96); packet.readUtf(128); packet.readVarInt(); packet.readUtf(128); packet.readVarInt();
                            CloudPatternSearch.read(packet); querySerial = packet.readVarInt();
                            check(packet.readableBytes() == 0, "Native pagination query payload is complete and its response serial is preserved");
                            return;
                        }
                        if (id != 211) return;
                        String action = packet.readUtf(32); var payload = packet.readNbt();
                        check(payload != null && packet.readableBytes() == 0, "Production recovery action packet is complete and bounded");
                        actions.add(action + ":" + payload.getString("recoveryId"));
                    } finally { packet.release(); }
                }
            });
            screen = new Screen(new Gui(ui)); Minecraft.getInstance().setScreen(screen); player.containerMenu = ui.getModularUIContainer();
            check(ui.getSlotMap().keySet().stream().filter(s -> s.container == player.getInventory()).count() == 36, "Recovery view retains all 36 native player inventory slots");
        }
        private void apply(CompoundTag state) throws ReflectiveOperationException {
            snapshot = state.copy(); snapshot.putInt("requestSerial", querySerial); var packet = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.writeNbt(snapshot); panel.readUpdateInfo(212, packet); check(packet.readableBytes() == 0, "Native panel consumes full authoritative server snapshot"); }
            finally { packet.release(); }
            var field = CloudPatternPanel.class.getDeclaredField("snapshot"); field.setAccessible(true);
            check(snapshot.equals(field.get(panel)), "Native panel accepts this exact server snapshot with the current query serial");
        }
        private void visibleRecovery() {
            check(snapshot.getBoolean("importQuarantined") && widget("cloud.recovery").isVisible()
                    && widget("cloud.recovery.row.0").isVisible(), "Isolated core directly exposes its pending recovery row");
            check(ui.mainGroup.getSizeWidth() <= Minecraft.getInstance().getWindow().getGuiScaledWidth()
                    && ui.mainGroup.getSizeHeight() <= Minecraft.getInstance().getWindow().getGuiScaledHeight(), "Entire native Fancy shell fits actual GUI viewport");
        }
        private boolean recoveryShown() throws Exception { var field = panel.getClass().getDeclaredField("recoveryShown"); field.setAccessible(true); return field.getBoolean(panel); }
        private Widget widget(String id) {
            // LDLib uses regex Matcher.find(), so unanchored IDs can resolve a nested prefix match.
            var widget = Objects.requireNonNull(ui.getFirstWidgetById("^" + java.util.regex.Pattern.quote(id) + "$"));
            if (!id.equals(widget.getId())) throw new AssertionError("Exact native widget identity: " + id);
            return widget;
        }
        private boolean clickOptional(String id) {
            var widget = widget(id); var pos = widget.getPosition();
            int x = pos.x + widget.getSizeWidth()/2, y = pos.y + widget.getSizeHeight()/2;
            boolean handled = ui.mainGroup.mouseClicked(x, y, 0); ui.mainGroup.mouseReleased(x, y, 0); return handled;
        }
        private void click(String id) { check(clickOptional(id), "Actual native root dispatches " + id); }
        private void hover(String id) { var w = widget(id); var p = w.getPosition(); screen.mouseX = p.x + 20; screen.mouseY = p.y + 8; }
        private void capture(String name) throws Exception {
            try (var shot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { shot.writeToFile(directory.resolve("screenshots").resolve(name)); }
            captures.add(name);
        }
        private void finish(Throwable failure) {
            done = true; if (failure != null) failure.printStackTrace();
            var report = new LinkedHashMap<String,Object>(); report.put("status", failure == null ? "PASS" : "FAIL");
            if (failure != null) report.put("error", failure.toString()); report.put("checks", checks); report.put("screenshots", captures); report.put("actions", actions);
            var mc = Minecraft.getInstance(); report.put("viewport", Map.of("width", mc.getWindow().getWidth(), "height", mc.getWindow().getHeight(),
                    "logicalWidth", mc.getWindow().getGuiScaledWidth(), "logicalHeight", mc.getWindow().getGuiScaledHeight(), "guiScale", mc.options.guiScale().get()));
            report.put("language", mc.getLanguageManager().getSelected());
            report.put("scope", "Actual production Fancy UI root input/rendering replaying same-candidate server snapshots; no player world opened, authority verified by separate Forge server JVM");
            try { Files.writeString(directory.resolve("upload-recovery-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception error) { error.printStackTrace(); }
            if (fixture != null) fixture.holders().forEach(h -> h.setLevel(null)); mc.stop();
        }
        private static final class Gui extends ModularUIGuiContainer {
            Gui(ModularUI ui) { super(ui, 1); }
            void cloudTick() { super.containerTick(); }
            void initialize(Minecraft mc) { minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight(); super.init(); }
        }
        private static final class Screen extends net.minecraft.client.gui.screens.Screen {
            final Gui gui; int frames, mouseX = -100, mouseY = -100;
            Screen(Gui gui) { super(Component.literal("Cloud upload recovery audit")); this.gui = gui; }
            protected void init() { gui.initialize(minecraft); }
            public void tick() { gui.cloudTick(); }
            public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) { gui.renderWithTooltip(graphics, mouseX, mouseY, partial); frames++; }
        }
    }
}
