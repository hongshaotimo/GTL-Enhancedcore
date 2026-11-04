package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Focused real root-input/render checks for current Fancy panels; fixture snapshots have no service authority. */
@Mod("enhancedcore_audit")
public final class CloudCircuitClientChecks {
    private final List<String> checks = new ArrayList<>(), actions = new ArrayList<>();
    private final List<MetaMachineBlockEntity> holders = new ArrayList<>();
    private Path directory;
    private Player player;
    private ModularUI ui;
    private Widget panel;
    private AuditScreen screen;
    private int phase, ticks, previousFrames;
    private boolean done;

    public CloudCircuitClientChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.cloudCircuitClientAudit")) return;
        var mc = Minecraft.getInstance();
        if (phase == 0 && (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen))) return;
        try {
            if (phase == 0) {
                directory = mc.gameDirectory.toPath().toRealPath();
                var auditRoot = Path.of(Objects.requireNonNull(System.getProperty("gtl.enhancedcore.auditRoot"),
                        "Explicit independent-worktree _audit root is required")).toRealPath();
                check(auditRoot.getFileName().toString().equals("_audit")
                                && Files.isRegularFile(auditRoot.getParent().resolve(".git"))
                                && directory.startsWith(auditRoot) && !directory.equals(auditRoot)
                                && Files.isRegularFile(directory.resolve("cloud-ae-ui.marker"))
                                && mc.level == null && mc.getSingleplayerServer() == null,
                        "Marked isolated client; no player world opened");
                check(Boolean.getBoolean("gtl.enhancedcore.memoryAuditProductionTicks"),
                        "Production recipe ticks stay enabled under the hidden-window audit agent");
                CloudCircuitMatchingRuntimeChecks.run(checks);
                checks.addAll(CloudLifecycleBoundaryRuntimeChecks.run());
                CloudNativeInputRuntimeChecks.run(checks);
                if (!Boolean.getBoolean("gtl.enhancedcore.cloudCircuitAudit")) { finish(null); return; }
                var fixture = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
                holders.addAll(fixture.holders()); player = fixture.player();
                for (String name : List.of("cloud_pattern_library", "cloud_pattern_buffer")) {
                    var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", name));
                    var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(new BlockPos(40,80,0), definition.defaultBlockState());
                    holders.add(holder); holder.setLevel(player.level());
                    var actual = ((com.gregtechceu.gtceu.api.machine.feature.IUIMachine)holder.getMetaMachine()).createUI(player);
                    actual.initWidgets();
                    var actualPanel = (WidgetGroup)actual.getFirstWidgetById(name.endsWith("library") ? "cloud.library" : "cloud.buffer");
                    bounds(actualPanel);
                    check(actual.mainGroup.widgets.stream().anyMatch(FancyMachineUIWidget.class::isInstance), name + " retains actual Fancy shell");
                }
                open(true); phase = 1;
            } else if (++ticks >= 4 && screen.frames - previousFrames >= 4) {
                ticks = 0; previousFrames = screen.frames;
                switch (phase++) {
                    case 1 -> { screenshot("library.png"); verifyTypes(); screen.hover("cloud.type"); }
                    case 2 -> { screenshot("library-type-tooltip.png"); open(false); }
                    case 3 -> { screenshot("buffer.png"); verifyModes(); screen.hover("cloud.mode"); }
                    case 4 -> { screenshot("buffer-mode-tooltip.png"); screen.hover("cloud.state"); }
                    case 5 -> { screenshot("buffer-input-tooltip.png"); finish(null); }
                }
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }
    private void open(boolean library) {
        CloudPatternUiHost host = new CloudPatternUiHost() {
            public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected) { return snapshot(); }
            public void cloudUiAction(ServerPlayer player, String action, CompoundTag payload) {}
        };
        class Holder implements IUIHolder, IFancyUIProvider {
            public ModularUI createUI(Player viewer) { return CloudPatternUi.create(host, this, viewer, library); }
            public boolean isInvalid() { return false; }
            public boolean isRemote() { return true; }
            public void markAsDirty() {}
            public Widget createMainPage(FancyMachineUIWidget fancy) { return library ? new CloudPatternLibraryWidget(host) : new CloudPatternBufferWidget(host); }
            public IGuiTexture getTabIcon() { return IGuiTexture.EMPTY; }
            public Component getTitle() { return Component.literal("Cloud circuit audit"); }
        }
        ui = new Holder().createUI(player); ui.initWidgets();
        panel = ui.getFirstWidgetById(library ? "cloud.library" : "cloud.buffer");
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.writeNbt(snapshot()); panel.readUpdateInfo(212, packet); check(packet.readableBytes() == 0, "Current snapshot packet consumed exactly"); }
        finally { packet.release(); }
        screen = new AuditScreen(new AuditGui(ui)); Minecraft.getInstance().setScreen(screen); previousFrames = 0;
        player.containerMenu = ui.getModularUIContainer();
        panel.setUiAccess(new WidgetUIAccess() {
            public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
            public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
            public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    writer.accept(buffer);
                    if (id == 210) {
                        buffer.readUtf(96); buffer.readUtf(128); buffer.readVarInt(); buffer.readUtf(128);
                        buffer.readVarInt(); CloudPatternSearch.read(buffer); buffer.readVarInt();
                        check(buffer.readableBytes() == 0, "Current query includes request serial and consumes all bytes");
                    } else if (id == 211) {
                        var action = buffer.readUtf(32); var payload = buffer.readNbt();
                        check(payload != null && payload.getLong("revision") == 7 && buffer.readableBytes() == 0,
                                "Mode action carries exact authoritative snapshot revision");
                        actions.add(action + ":" + payload.getString("type"));
                    }
                } finally { buffer.release(); }
            }
        });
        long inventory = ui.getSlotMap().keySet().stream().filter(slot -> slot.container == player.getInventory()).count();
        check(inventory == 36, "Fancy UI retains all 36 native inventory slots");
    }
    private void verifyTypes() throws Exception {
        for (int button : new int[]{0,0,1,1,1,0}) {
            click("cloud.type", button);
            String expected = switch (typeStep++) { case 0,2 -> "gtceu:chemical_reactor"; case 1 -> "gtceu:centrifuge"; case 4 -> "gtceu:assembler"; default -> ""; };
            check(selectedType().equals(expected), "Type button " + button + " selects " + expected);
        }
        int count = actions.size(); var before = selectedType(); click("cloud.type", 2);
        check(actions.size() == count && selectedType().equals(before), "Middle click leaves type selection unchanged");
    }
    private int typeStep;
    private void verifyModes() throws Exception {
        String[] expected = {"gtceu:pyrolyse_oven","gtceu:chemical_reactor","gtceu:assembler","gtceu:chemical_reactor","gtceu:pyrolyse_oven","gtceu:assembler"};
        for (int i = 0; i < expected.length; i++) {
            int before = actions.size(); int button = i < 3 ? 0 : 1; click("cloud.mode", button);
            check(selectedType().equals(expected[i]) && actions.size() == before + 1
                            && actions.getLast().equals("set_mode:" + expected[i]),
                    "Mode button " + button + " selects " + expected[i] + ", skips claimed mode and sends one action");
        }
        int count = actions.size(); click("cloud.mode", 2);
        check(count == actions.size(), "Middle click sends no mode action");
    }
    private String selectedType() throws Exception { var field = CloudPatternPanel.class.getDeclaredField("type"); field.setAccessible(true); return (String)field.get(panel); }
    private void click(String id, int button) {
        var widget = ui.getFirstWidgetById(id); var pos = widget.getPosition();
        int x = pos.x + widget.getSizeWidth()/2, y = pos.y + widget.getSizeHeight()/2;
        boolean handled = ui.mainGroup.mouseClicked(x,y,button); ui.mainGroup.mouseReleased(x,y,button);
        if (button < 2) check(handled, "Root dispatch handles " + id + " button " + button);
    }
    private void bounds(WidgetGroup group) {
        for (var widget : group.widgets) {
            var pos = widget.getSelfPosition(); var size = widget.getSize();
            check(pos.x >= 0 && pos.y >= 0 && pos.x + size.width <= group.getSizeWidth()
                            && pos.y + size.height <= group.getSizeHeight(), "AE panel child stays in bounds: " + widget.getId());
            if (widget instanceof WidgetGroup child) bounds(child);
        }
    }
    private static CompoundTag snapshot() {
        var tag = new CompoundTag(); tag.putLong("revision",7); tag.putBoolean("networkConnected",true);
        tag.putString("status","gtl_enhancedcore.cloud.ui.available"); tag.putInt("pages",1); tag.putInt("matched",3); tag.putInt("total",3);
        var types = new ListTag(); for (String id : List.of("gtceu:assembler","gtceu:centrifuge","gtceu:chemical_reactor")) types.add(StringTag.valueOf(id)); tag.put("types",types);
        var modes = new ListTag();
        for (String id : List.of("gtceu:assembler","gtceu:centrifuge","gtceu:chemical_reactor","gtceu:pyrolyse_oven")) {
            var mode = new CompoundTag(); mode.putString("id",id); mode.putBoolean("current",id.equals("gtceu:assembler")); mode.putBoolean("available",!id.equals("gtceu:centrifuge")); modes.add(mode);
        }
        tag.put("modes",modes); var entries = new ListTag();
        for (Item item : List.of(Items.IRON_INGOT,Items.GOLD_INGOT,Items.DIAMOND)) {
            var entry = new CompoundTag(); entry.putString("id",item.toString()); entry.putString("nameJson",Component.Serializer.toJson(item.getDescription()));
            entry.putString("type","gtceu:assembler"); entry.put("icon",item.getDefaultInstance().save(new CompoundTag()));
            entry.put("iconKey",appeng.api.stacks.AEItemKey.of(item).toTagGeneric()); entries.add(entry);
        }
        tag.put("entries",entries); return tag;
    }
    private void screenshot(String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); }
        check(true,"Actual native render: " + name);
    }
    private void check(boolean valid,String label) { if (!valid) throw new AssertionError(label); checks.add(label); }
    private void finish(Throwable failure) {
        done=true; var report = new LinkedHashMap<String,Object>(); report.put("status",failure==null?"PASS":"FAIL");
        if (failure!=null) report.put("error",failure.toString()); report.put("checks",checks); report.put("actions",actions);
        report.put("scope","Actual transformed pattern helper, recipe matching, registered suppliers and native Fancy panel root input/rendering; controlled snapshots, no player save or real AE crafting job.");
        try { Files.writeString(directory.resolve("cloud-ae-ui-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        holders.forEach(holder->holder.setLevel(null)); Minecraft.getInstance().stop();
    }
    private static final class AuditGui extends ModularUIGuiContainer {
        AuditGui(ModularUI ui) { super(ui,1); }
        void cloudTick() { super.containerTick(); }
        void initialize(Minecraft mc) { minecraft=mc; font=mc.font; width=mc.getWindow().getGuiScaledWidth(); height=mc.getWindow().getGuiScaledHeight(); super.init(); }
    }
    private final class AuditScreen extends net.minecraft.client.gui.screens.Screen {
        private final AuditGui gui; int frames; int mouseX=-100,mouseY=-100;
        AuditScreen(AuditGui gui) { super(Component.literal("Cloud circuit audit")); this.gui=gui; }
        void hover(String id) { var widget=ui.getFirstWidgetById(id); var pos=widget.getPosition(); mouseX=pos.x+widget.getSizeWidth()/2; mouseY=pos.y+widget.getSizeHeight()/2; }
        protected void init() { gui.initialize(minecraft); }
        public void tick() { gui.cloudTick(); }
        public void render(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float partial) { gui.renderWithTooltip(graphics,mouseX,mouseY,partial); frames++; }
    }
}
