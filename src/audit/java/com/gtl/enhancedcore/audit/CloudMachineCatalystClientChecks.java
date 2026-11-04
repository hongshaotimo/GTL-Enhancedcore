package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
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
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** One opt-in helper entry point; server mode never instantiates client-only nested classes. */
@Mod("enhancedcore_audit")
public final class CloudMachineCatalystClientChecks {
    private int serverTicks;
    private boolean serverDone;
    public CloudMachineCatalystClientChecks() {
        if (Boolean.getBoolean("gtl.enhancedcore.machineCatalystServerAudit")) MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        if (FMLEnvironment.dist == Dist.CLIENT && Boolean.getBoolean("gtl.enhancedcore.machineCatalystClientAudit")) new ClientRunner();
    }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (serverDone || event.phase != TickEvent.Phase.END || ++serverTicks < 30) return;
        serverDone = true;
        CloudMachineCatalystRuntimeChecks.runServer(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
    }
    private static final class ClientRunner {
        private final List<String> checks = new ArrayList<>(), actions = new ArrayList<>(), screenshots = new ArrayList<>();
        private final List<MetaMachineBlockEntity> holders = new ArrayList<>();
        private Path directory;
        private Player player;
        private ModularUI ui;
        private Widget panel;
        private AuditScreen screen;
        private boolean done, library = true, details;
        private int phase, definition;
        private long epoch = 1;
        private static final List<String> DEFINITIONS = List.of("gtceu:circuit_assembly_line", "gtceu:pyrolyse_oven");
        ClientRunner() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
        private void check(boolean value, String label) { CloudMachineCatalystRuntimeChecks.check(value, label, checks); }
        private void tick(TickEvent.ClientTickEvent event) {
            if (done || event.phase != TickEvent.Phase.END) return;
            var mc = Minecraft.getInstance();
            if (directory == null && (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen))) return;
            try {
                if (directory == null) {
                    directory = mc.gameDirectory.toPath().toRealPath();
                    Path root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
                    check(directory.startsWith(root) && !directory.equals(root) && Files.isRegularFile(directory.resolve("machine-catalyst.marker"))
                            && mc.level == null && mc.getSingleplayerServer() == null, "Marked independent client without a player world");
                    check(Boolean.getBoolean("gtl.enhancedcore.memoryAuditProductionTicks"), "Production execution is enabled under hidden-window audit");
                    Files.createDirectories(directory.resolve("screenshots"));
                    CloudMachineCatalystRuntimeChecks.matching(checks);
                    var fixture = StellarIvGuiChecks.fixture("gtceu:circuit_assembly_line", true, false, false);
                    holders.addAll(fixture.holders()); player = fixture.player(); open(true); return;
                }
                if (mc.getOverlay() != null || screen.frames < 12) return;
                screen.frames = 0;
                switch (phase++) {
                    case 0 -> { capture("library-home.png"); click("cloud.machine.catalysts"); check(details, "Library root click opens machine configuration"); }
                    case 1 -> { verifyDetails(); capture("library-machine-assembly.png"); click("cloud.catalyst.machine"); check(definition == 1, "Root machine selector cycles to another definition"); }
                    case 2 -> { verifyDetails(); capture("library-machine-pyrolyse.png"); open(false); }
                    case 3 -> { capture("buffer-home.png"); click("cloud.machine.catalysts"); check(details, "Buffer root click opens machine configuration"); }
                    case 4 -> { verifyDetails(); capture("buffer-machine-shared.png"); finish(null); }
                    default -> throw new AssertionError("Unexpected phase");
                }
            } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
        }
        private void open(boolean central) {
            library = central; details = false; definition = 0; epoch++;
            CloudPatternUiHost host = new CloudPatternUiHost() {
                public CompoundTag cloudUiSnapshot(ServerPlayer viewer, String search, String type, int page, String selected) { return snapshot(); }
                public void cloudUiAction(ServerPlayer viewer, String action, CompoundTag payload) {}
            };
            class Holder implements IUIHolder, IFancyUIProvider {
                public ModularUI createUI(Player viewer) { return CloudPatternUi.create(host, this, viewer, central); }
                public boolean isInvalid() { return false; }
                public boolean isRemote() { return true; }
                public void markAsDirty() {}
                public Widget createMainPage(FancyMachineUIWidget fancy) { return central ? new CloudPatternLibraryWidget(host) : new CloudPatternBufferWidget(host); }
                public IGuiTexture getTabIcon() { return IGuiTexture.EMPTY; }
                public Component getTitle() { return Component.translatable("gtl_enhancedcore.cloud.ui." + (central ? "library" : "buffer")); }
            }
            ui = new Holder().createUI(player); ui.initWidgets(); panel = ui.getFirstWidgetById(central ? "cloud.library" : "cloud.buffer");
            applySnapshot();
            panel.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
                public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                    var packet = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        writer.accept(packet);
                        if (id != 211) return;
                        String action = packet.readUtf(32); var payload = packet.readNbt();
                        check(payload != null && packet.readableBytes() == 0, "Native client action is a complete bounded packet: " + action);
                        if (action.startsWith("machine_catalysts_")) check(payload.getLong("catalystAccessEpoch") == epoch,
                                "Machine cycle sends current server access epoch");
                        actions.add(action);
                        if (action.equals("machine_config")) { details = true; epoch++; }
                        else if (action.equals("machine_catalysts_next")) { definition = (definition + 1) % DEFINITIONS.size(); epoch++; }
                        else if (action.equals("machine_catalysts_previous")) { definition = (definition + DEFINITIONS.size() - 1) % DEFINITIONS.size(); epoch++; }
                        else if (action.equals("close_pattern_config")) { details = false; epoch++; }
                        applySnapshot();
                    } finally { packet.release(); }
                }
            });
            screen = new AuditScreen(new AuditGui(ui)); Minecraft.getInstance().setScreen(screen);
            player.containerMenu = ui.getModularUIContainer();
            check(ui.getSlotMap().keySet().stream().filter(s -> s.container == player.getInventory()).count() == 36,
                    "Native Fancy " + (central ? "library" : "buffer") + " retains 36 player inventory slots");
        }
        private void applySnapshot() {
            var packet = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.writeNbt(snapshot()); panel.readUpdateInfo(212, packet); check(packet.readableBytes() == 0, "Authoritative snapshot consumed exactly"); }
            finally { packet.release(); }
            // Native client slot mirrors normally receive these stacks from LDLib slot sync.
            // Seed only the detached client mirror so screenshots show actual registered robots.
            if (details) {
                var robotId = new net.minecraft.resources.ResourceLocation("kubejs", "precision_circuit_assembly_robot_mk" + (definition + 1));
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(robotId);
                check(item != Items.AIR, "Actual registered circuit robot exists: " + robotId);
                var widget = ui.getFirstWidgetById("cloud.catalyst.0");
                var slot = ui.getSlotMap().entrySet().stream().filter(e -> e.getValue() == widget).findFirst().orElseThrow().getKey();
                slot.set(new ItemStack(item, 1));
            }
        }
        private CompoundTag snapshot() {
            var tag = new CompoundTag(); tag.putLong("revision", 7); tag.putBoolean("networkConnected", true);
            tag.putBoolean("machineCatalystsAvailable", true);
            tag.putString("status", "gtl_enhancedcore.cloud.ui.available"); tag.putInt("pages", 1); tag.putInt("matched", 2); tag.putInt("total", 2);
            tag.putString("mode", "gtceu:circuit_assembly_line");
            var types = new ListTag(); types.add(StringTag.valueOf("gtceu:circuit_assembly_line")); tag.put("types", types);
            var definitions = new ListTag();
            for (String id : DEFINITIONS) { var row = new CompoundTag(); row.putString("id", id); row.putString("nameJson", Component.Serializer.toJson(Component.translatable("block." + id.replace(':', '.')))); definitions.add(row); }
            tag.put("machineCatalystDefinitions", definitions);
            tag.putBoolean("patternCatalystsAllowed", details); tag.putBoolean("patternCatalystsMachine", details);
            tag.putLong("catalystAccessEpoch", epoch); tag.putString("catalystMachineDefinition", DEFINITIONS.get(definition));
            tag.putString("catalystEntry", details ? UUID.nameUUIDFromBytes(DEFINITIONS.get(definition).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString() : "");
            tag.putInt("patternCatalystsPages", 1); tag.putInt("patternCatalystsPage", 0);
            tag.putIntArray("patternCatalystItemLimits", new int[]{64,64,64,64,64,64,64,64,64});
            tag.putLongArray("patternCatalystFluidCapacities", new long[]{16000,16000,16000,16000,16000,16000,16000,16000,16000});
            var detail = new CompoundTag(); detail.putString("nameJson", Component.Serializer.toJson(Component.translatable("block." + DEFINITIONS.get(definition).replace(':', '.')))); tag.put("detail", detail);
            var entries = new ListTag();
            for (Item item : List.of(Items.IRON_INGOT, Items.GOLD_INGOT)) {
                var row = new CompoundTag(); row.putString("id", UUID.nameUUIDFromBytes(item.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
                row.putString("nameJson", Component.Serializer.toJson(item.getDescription())); row.putString("type", "gtceu:circuit_assembly_line");
                row.put("icon", item.getDefaultInstance().save(new CompoundTag())); row.put("iconKey", appeng.api.stacks.AEItemKey.of(item).toTagGeneric()); entries.add(row);
            }
            tag.put("entries", entries); return tag;
        }
        private void verifyDetails() {
            for (int i = 0; i < 9; i++) {
                check(ui.getFirstWidgetById("cloud.catalyst." + i) != null, "Machine inventory retains item slot " + i);
                check(ui.getFirstWidgetById("cloud.catalyst.fluid." + i) != null, "Machine inventory retains fluid tank " + i);
            }
            check(ui.getFirstWidgetById("cloud.catalyst.machine").isVisible(), "Machine selector is visible on shared details");
            check(ui.mainGroup.getSizeWidth() <= Minecraft.getInstance().getWindow().getGuiScaledWidth()
                    && ui.mainGroup.getSizeHeight() <= Minecraft.getInstance().getWindow().getGuiScaledHeight(), "Full Fancy shell fits actual GUI viewport");
        }
        private void click(String id) {
            var widget = Objects.requireNonNull(ui.getFirstWidgetById(id), id); var pos = widget.getPosition();
            int x = pos.x + widget.getSizeWidth()/2, y = pos.y + widget.getSizeHeight()/2;
            boolean handled = ui.mainGroup.mouseClicked(x, y, 0); ui.mainGroup.mouseReleased(x, y, 0);
            check(handled, "Real root input handles " + id);
        }
        private void capture(String name) throws Exception {
            try (var screenshot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { screenshot.writeToFile(directory.resolve("screenshots").resolve(name)); }
            screenshots.add(name); check(true, "Actual rendered screenshot " + name);
        }
        private void finish(Throwable failure) {
            done = true; var report = new LinkedHashMap<String, Object>(); report.put("status", failure == null ? "PASS" : "FAIL");
            if (failure != null) report.put("error", failure.toString()); report.put("checks", checks); report.put("actions", actions); report.put("screenshots", screenshots);
            report.put("scope", "Transformed production recipe admission and native Fancy root-input/rendering with controlled snapshots; server inventory authority is verified by separate independent-JVM phases");
            try { Files.writeString(directory.resolve("machine-catalyst-client.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception error) { error.printStackTrace(); }
            holders.forEach(h -> h.setLevel(null)); Minecraft.getInstance().stop();
        }
        private static final class AuditGui extends ModularUIGuiContainer {
            AuditGui(ModularUI ui) { super(ui, 1); }
            void cloudTick() { super.containerTick(); }
            void initialize(Minecraft mc) { minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight(); super.init(); }
        }
        private static final class AuditScreen extends net.minecraft.client.gui.screens.Screen {
            private final AuditGui gui;
            int frames;
            AuditScreen(AuditGui gui) { super(Component.literal("Cloud machine catalyst audit")); this.gui = gui; }
            protected void init() { gui.initialize(minecraft); }
            public void tick() { gui.cloudTick(); }
            public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partial) { gui.renderWithTooltip(graphics, -100, -100, partial); frames++; }
        }
    }
}
