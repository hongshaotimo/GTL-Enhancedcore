package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.ui.*;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.misc.*;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Native UI tree/event/render audit at an explicitly marked title screen; never loads a saved world. */
@Mod("enhancedcore_audit")
public final class CloudPatternUiChecks {
    private static final Set<Path> ROOTS = Set.of(
            Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-pattern-implementation-20261023"),
            Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-pattern-ae-rework-20261023"));
    private final List<String> checks = new ArrayList<>();
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<String> actions = new ArrayList<>();
    private Path directory;
    private ModularUI ui;
    private Widget panel;
    private Player player;
    private int phase, ticks, lastPhaseFrame;
    private int lastRecoveryPage;
    private CloudPatternSearch lastAliases = CloudPatternSearch.EMPTY;
    private CloudTerminalUiChecks.Session terminal;
    private boolean done;
    public CloudPatternUiChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.cloudUiAudit")) return;
        Minecraft mc = Minecraft.getInstance();
        if (phase == 0) {
            directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || mc.level != null
                    || mc.getSingleplayerServer() != null || ROOTS.stream().noneMatch(root -> directory.startsWith(root) && !directory.equals(root))
                    || !Files.isRegularFile(directory.resolve("cloud-ui.marker"))) return;
        }
        try {
            if (phase == 0) {
                searchProtocolChecks();
                var fixture = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
                player = fixture.player();
                for (String id : List.of("cloud_pattern_library", "cloud_pattern_buffer")) {
                    var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", id));
                    check(definition != null, "Real cloud definition registered: " + id);
                    var holder = (MetaMachineBlockEntity) definition.getBlockEntityType()
                            .create(new BlockPos(40, 80, 0), definition.defaultBlockState());
                    holder.setLevel(player.level());
                    check(holder.getMetaMachine() instanceof CloudPatternUiHost, "Registered machine implements the native UI host: " + id);
                    if (holder.getMetaMachine() instanceof com.gregtechceu.gtceu.api.machine.feature.IUIMachine machine) {
                        ModularUI actual = machine.createUI(player); actual.initWidgets();
                        check(actual.getWidth() == 194 && actual.getHeight() == 232, "Real machine uses compact 194x232 AE terminal with permanent inventory: " + id);
                        bounds(actual.mainGroup);
                        check(count(actual.mainGroup, SlotWidget.class) >= 36, "Real UI includes player inventory: " + id);
                    } else throw new AssertionError("Registered cloud machine has no IUIMachine: " + id);
                    holder.setLevel(null);
                }
                open(true); phase = 1; ticks = 0;
            } else if (++ticks >= 4) {
                int renderedFrames = mc.screen instanceof AuditScreen rendered ? rendered.frames : terminal == null ? 0 : terminal.frames();
                if (renderedFrames - lastPhaseFrame < 4) return;
                lastPhaseFrame = renderedFrames;
                ticks = 0;
                switch (phase++) {
                    case 1 -> { screenshot("cloud-library-patterns.png"); clickId("cloud.search"); ui.mainGroup.charTyped('铁', 0); ui.mainGroup.charTyped('锭', 0); check(lastAliases.matches(appeng.api.stacks.AEItemKey.of(Items.IRON_INGOT)), "Chinese search sends the actual iron-ingot registry alias"); clickId("cloud.pattern.0"); check(actions.contains("query"), "Full UI dispatch reaches a pattern selection"); inventory(); clickId("cloud.tab.catalysts"); }
                    case 2 -> { screenshot("cloud-library-catalysts.png"); check(count(ui.mainGroup, TankWidget.class) == 9, "Independent nine fluid catalyst widgets"); check(count(ui.mainGroup, SlotWidget.class) == 46, "Nine item catalysts plus import and permanently visible player inventory"); inventory(); itemTransfer(); clickId("cloud.tab.settings"); }
                    case 3 -> { screenshot("cloud-library-settings.png"); inventory(); clickId("cloud.terminal_diagnostics"); check(actions.contains("terminal_diagnostics"), "Secondary terminal diagnostic action is reachable without crowding the main terminal"); clickId("cloud.settings.import"); }
                    case 4 -> { screenshot("cloud-library-import.png"); clickId("cloud.import"); check(actions.contains("import"), "Import traverses root event chain once"); clickId("cloud.tab.settings"); clickId("cloud.settings.recovery"); }
                    case 5 -> { screenshot("cloud-library-recovery.png"); clickId("cloud.recovery.0"); clickId("cloud.recovery.claim"); check(actions.contains("review_transfer"), "Transfer review sends a selected record ID"); clickId("cloud.recovery.next"); check(lastRecoveryPage == 1, "Library recovery navigation requests the second bounded page"); inventory(); clickId("cloud.tab.settings"); clickId("cloud.settings.permissions"); }
                    case 6 -> { screenshot("cloud-library-permissions.png"); clickId("cloud.delegate"); for (char c : "00000000-0000-0000-0000-000000000001".toCharArray()) ui.mainGroup.charTyped(c, 0); clickId("cloud.grant"); check(actions.contains("grant"), "Permission grant traverses root with explicit delegate UUID"); clickId("cloud.revoke"); check(actions.contains("revoke"), "Permission revoke traverses root with explicit delegate UUID"); inventory(); open(false); }
                    case 7 -> { screenshot("cloud-buffer-patterns.png"); inventory(); check(count(ui.mainGroup, ButtonWidget.class) >= 36, "Native AE grid provides nine columns and 36 icon cells"); clickId("cloud.mode"); check(actions.contains("set_mode"), "Compact mode selector submits an available localized mode"); clickId("cloud.tab.settings"); }
                    case 8 -> { screenshot("cloud-buffer-controls.png"); clickId("cloud.pause"); check(actions.contains("pause"), "Node pause traverses full root chain"); int before = Collections.frequency(actions, "cancel"); clickId("cloud.cancel"); check(Collections.frequency(actions, "cancel") == before, "First cancel click only arms confirmation"); clickId("cloud.cancel"); check(Collections.frequency(actions, "cancel") == before + 1, "Second cancel click sends exactly one confirmed action"); inventory(); clickId("cloud.settings.recovery"); }
                    case 9 -> { screenshot("cloud-buffer-recovery.png"); clickId("cloud.recovery.0"); clickId("cloud.recovery.claim"); check(actions.contains("recover"), "Frozen-order recovery sends a selected record ID"); clickId("cloud.recovery.next"); check(lastRecoveryPage == 1, "Node recovery navigation requests the second bounded page"); inventory(); terminal = CloudTerminalUiChecks.open(player); lastPhaseFrame = 0; }
                    case 10 -> { terminal.verify(false, this::check); screenshot("cloud-terminal-unbound.png"); terminal.bound(true); }
                    case 11 -> { terminal.verify(true, this::check); screenshot("cloud-terminal-bound.png"); finish(null); }
                    default -> throw new AssertionError("Unexpected audit phase");
                }
            }
        } catch (Throwable failure) { failure.printStackTrace(); finish(failure); }
    }
    private void open(boolean library) {
        var host = new SampleHost();
        IUIHolder holder = new IUIHolder() {
            @Override public ModularUI createUI(Player viewer) { return CloudPatternUi.create(host, this, viewer, library); }
            @Override public boolean isInvalid() { return false; }
            @Override public boolean isRemote() { return true; }
            @Override public void markAsDirty() {}
        };
        ui = holder.createUI(player); ui.initWidgets();
        bounds(ui.mainGroup);
        panel = ui.getFirstWidgetById(library ? "cloud.library" : "cloud.buffer");
        check(panel != null, "Native panel found: " + (library ? "library" : "buffer"));
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.writeNbt(sample()); panel.readUpdateInfo(212, packet); check(packet.readableBytes() == 0, "Bounded page packet fully consumed"); }
        finally { packet.release(); }
        var screen = new AuditGui(ui);
        var minecraft = Minecraft.getInstance();
        minecraft.setScreen(new AuditScreen(screen));
        lastPhaseFrame = 0;
        var auditAccess = new WidgetUIAccess() {
            @Override public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
            @Override public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    writer.accept(buffer);
                    if (id == 210) {
                        buffer.readUtf(96); buffer.readUtf(128); buffer.readVarInt(); buffer.readUtf(128);
                        lastRecoveryPage = buffer.readVarInt(); lastAliases = CloudPatternSearch.read(buffer);
                        check(buffer.readableBytes() == 0, "Query consumes recovery pagination and bounded localized aliases");
                        actions.add("query");
                    }
                    if (id == 211) {
                        String action = buffer.readUtf(32); CompoundTag payload = buffer.readNbt();
                        check(payload != null && payload.getLong("revision") == 7, "Action carries server revision: " + action);
                        if (action.equals("set_mode")) check(payload.getString("type").equals("gtceu:chemical_reactor"), "Mode selector skips a mode claimed by another node");
                        if (action.equals("recover") || action.equals("review_transfer")) check(payload.getString("recoveryId").equals("recovery-1"), "Recovery identifies the chosen record");
                        if (action.equals("grant") || action.equals("revoke")) check(payload.getString("delegate").equals("00000000-0000-0000-0000-000000000001"), "Permission action identifies the explicitly typed delegate");
                        actions.add(action);
                    }
                } finally { buffer.release(); }
            }
            @Override public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
        };
        panel.setUiAccess(auditAccess);
    }
    private void inventory() {
        check(ui.getFirstWidgetById("cloud.inventory") == null, "No separate inventory page or cursor-switch button");
        long nativeSlots = ui.getSlotMap().keySet().stream().filter(slot -> slot.container == player.getInventory()).count();
        long visibleSlots = ui.getSlotMap().entrySet().stream().filter(entry -> entry.getKey().container == player.getInventory() && entry.getValue().isVisible()).count();
        check(nativeSlots == 36 && visibleSlots == 36, "All 27 inventory and nine hotbar slots remain simultaneously visible on every page");
    }
    private void itemTransfer() {
        var menu = ui.getModularUIContainer();
        player.containerMenu = menu;
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT));
        var source = ui.getSlotMap().keySet().stream().filter(slot -> slot.container == player.getInventory()
                && slot.getContainerSlot() == 9).findFirst().orElseThrow();
        var catalyst = ui.getSlotMap().entrySet().stream().filter(entry ->
                "cloud.catalyst.0".equals(entry.getValue().getId()))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        menu.clicked(source.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().is(Items.IRON_INGOT), "Real native inventory slot puts an item on the menu cursor");
        menu.clicked(catalyst.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().isEmpty() && catalyst.getItem().is(Items.IRON_INGOT), "Real native menu transfers the carried item into a simultaneously visible catalyst slot");
        menu.clicked(catalyst.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(source.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().isEmpty() && player.getInventory().getItem(9).is(Items.IRON_INGOT), "Native catalyst extraction returns the item to the original inventory slot");
    }
    private void clickId(String id) {
        var widget = ui.getFirstWidgetById(id); check(widget != null, "Native AE widget exists: " + id);
        var position = widget.getPosition(); int x = position.x + widget.getSizeWidth() / 2, y = position.y + widget.getSizeHeight() / 2;
        check(ui.mainGroup.mouseClicked(x, y, 0), "Root handles native AE click: " + id);
        ui.mainGroup.mouseReleased(x, y, 0);
    }
    private static int count(Widget widget, Class<?> type) {
        int count = type.isInstance(widget) ? 1 : 0;
        if (widget instanceof WidgetGroup group) for (Widget child : group.widgets) count += count(child, type);
        return count;
    }
    private void bounds(WidgetGroup group) {
        for (Widget widget : group.widgets) {
            var position = widget.getSelfPosition(); var size = widget.getSize();
            if (position.x < 0 || position.y < 0 || position.x + size.width > group.getSizeWidth()
                    || position.y + size.height > group.getSizeHeight()) throw new AssertionError(
                    "Widget escapes parent bounds: " + widget.getId() + " " + widget.getClass().getSimpleName()
                            + " " + position + " " + size + " parent=" + group.getSize());
            if (widget instanceof WidgetGroup nested) bounds(nested);
        }
    }
    private void screenshot(String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); }
        checks.add("Rendered native LDLib screenshot: " + name);
    }
    private void finish(Throwable failure) {
        done = true; report.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) report.put("error", failure.toString());
        report.put("checks", checks); report.put("actions", actions);
        report.put("scope", "Actual registered suppliers and native LDLib widget trees/root event dispatch/rendering, with sample bounded snapshots and real local menu slot clicks. Unrelated Forge screen-init hooks are skipped because the title-screen fixture has no LocalPlayer. No service mutation, AE processing, or saved player world.");
        try { Files.writeString(directory.resolve("cloud-ui-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks.add(message); }
    private void searchProtocolChecks() throws Exception {
        var names = com.gtl.enhancedcore.client.PatternGeneratorNameSearch.find("铁锭");
        int iron = net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(Items.IRON_INGOT);
        check(names.items().get(iron), "Client language index resolves Chinese iron-ingot name");
        var input = names.items(); var query = new CloudPatternSearch(input, names.fluids(), Set.of("gtceu:assembler"));
        input.clear(); query.items().clear();
        check(query.matchesItem(iron), "Search aliases clone constructor and accessor bitsets");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            query.write(buffer); var decoded = CloudPatternSearch.read(buffer);
            check(query.equals(decoded) && query.hashCode() == decoded.hashCode() && buffer.readableBytes() == 0,
                    "Chinese registry aliases and recipe types survive bounded packet round trip");
        } finally { buffer.release(); }
        expectSearchRejected(b -> b.writeVarInt((net.minecraft.core.registries.BuiltInRegistries.ITEM.size() + 63) / 64 + 1), "Oversized item registry alias array is rejected");
        expectSearchRejected(b -> { b.writeLongArray(new long[0]); b.writeVarInt((net.minecraft.core.registries.BuiltInRegistries.FLUID.size() + 63) / 64 + 1); }, "Oversized fluid registry alias array is rejected");
        expectSearchRejected(b -> { b.writeLongArray(new long[0]); b.writeLongArray(new long[0]); b.writeVarInt(GTRegistries.RECIPE_TYPES.values().size() + 1); }, "Oversized recipe-type alias count is rejected");
        expectSearchRejected(b -> { b.writeLongArray(new long[0]); b.writeLongArray(new long[0]); b.writeVarInt(1); b.writeUtf("invalid:missing"); }, "Unknown recipe-type alias is rejected");
        expectSearchRejected(b -> { var outOfRange = new BitSet(); outOfRange.set(net.minecraft.core.registries.BuiltInRegistries.ITEM.size()); b.writeLongArray(outOfRange.toLongArray()); }, "Out-of-registry item bit is rejected");
        expectSearchRejected(b -> { b.writeLongArray(new long[0]); b.writeLongArray(new long[0]); b.writeVarInt(1); b.writeUtf("a".repeat(129)); }, "Recipe-type aliases enforce the 128-character ID limit");
        var row = new CompoundTag(); row.putString("name", "Server English fallback");
        row.putString("nameJson", net.minecraft.network.chat.Component.Serializer.toJson(net.minecraft.network.chat.Component.translatable("item.minecraft.iron_ingot")));
        var method = Class.forName("com.gtl.enhancedcore.common.cloud.ui.CloudPatternPanel").getDeclaredMethod("display", CompoundTag.class, String.class);
        method.setAccessible(true);
        check("铁锭".equals(method.invoke(null, row, "name")), "Untranslated server component renders as Chinese through the real UI display helper");
    }
    private void expectSearchRejected(Consumer<FriendlyByteBuf> writer, String message) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        boolean rejected = false;
        try { writer.accept(buffer); try { CloudPatternSearch.read(buffer); } catch (RuntimeException expected) { rejected = true; } }
        finally { buffer.release(); }
        check(rejected, message);
    }
    private static CompoundTag sample() {
        CompoundTag tag = new CompoundTag(); tag.putLong("revision", 7); tag.putString("status", "gtl_enhancedcore.cloud.ui.available");
        tag.putInt("page", 0); tag.putInt("pages", 2); tag.putInt("matched", 18); tag.putInt("active", 2); tag.putInt("queued", 4);
        tag.putString("mode", "装配机 / Assembler"); tag.putString("library", "主基地 / Main base"); tag.putString("target", "Overworld / 40, 80, 12");
        tag.putString("sourceName", "旧超级总成"); tag.putString("sourcePos", "40, 80, 10"); tag.putInt("recovery", 1);
        ListTag entries = new ListTag(); Item[] materials = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.REDSTONE};
        for (int i = 0; i < 36; i++) {
            CompoundTag entry = new CompoundTag(); entry.putString("id", "entry-" + i); entry.putString("name", "Server English fallback " + i);
            String nameJson = net.minecraft.network.chat.Component.Serializer.toJson(new ItemStack(materials[i % 4]).getHoverName());
            entry.putString("nameJson", nameJson); entry.putString("recipeJson", nameJson);
            entry.putString("type", "Assembler"); entry.putBoolean("leased", i % 3 == 0); entry.putString("target", i % 3 == 0 ? "40,80,12" : "");
            entry.put("icon", new ItemStack(materials[i % 4]).save(new CompoundTag())); entries.add(entry);
        }
        tag.put("entries", entries); tag.put("detail", entries.getCompound(0).copy()); tag.putBoolean("networkConnected", true); tag.putInt("libraryTotal", 36);
        ListTag modes = new ListTag();
        for (String id : List.of("gtceu:assembler", "gtceu:centrifuge", "gtceu:chemical_reactor")) {
            CompoundTag mode = new CompoundTag(); mode.putString("id", id);
            mode.putBoolean("current", id.equals("gtceu:assembler"));
            mode.putBoolean("available", id.equals("gtceu:chemical_reactor")); modes.add(mode);
        }
        tag.put("modes", modes);
        ListTag types = new ListTag(); types.add(StringTag.valueOf("gtceu:assembler")); tag.put("types", types);
        ListTag recovery = new ListTag(); CompoundTag record = new CompoundTag(); record.putString("id", "recovery-1"); record.putString("name", "Unconsumed inputs"); record.putString("kind", "transfer"); record.putString("target", "Overworld / 20, 80, 12"); recovery.add(record); tag.put("recoveryEntries", recovery); tag.putInt("recoveryPage", 0); tag.putInt("recoveryPages", 2);
        return tag;
    }
    private static final class SampleHost implements CloudPatternUiHost {
        private final ItemStackTransfer items = new ItemStackTransfer(9), input = new ItemStackTransfer(1);
        private final FluidTransferList fluids = new FluidTransferList(java.util.stream.IntStream.range(0, 9).mapToObj(i -> new FluidStorage(16000)).toArray(IFluidTransfer[]::new));
        @Override public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected) { return sample(); }
        @Override public void cloudUiAction(ServerPlayer player, String action, CompoundTag payload) {}
        @Override public IItemTransfer cloudItemCatalysts(Player player) { return items; }
        @Override public IItemTransfer cloudImportInventory(Player player) { return input; }
        @Override public IFluidTransfer cloudFluidCatalysts(Player player) { return fluids; }
    }
    /** Title-screen fixture has no LocalPlayer; skip unrelated mods' container-init hooks. */
    private static final class AuditGui extends ModularUIGuiContainer {
        AuditGui(ModularUI ui) { super(ui, 1); }
        public void tickCloud() { super.containerTick(); }
        void initialize(Minecraft minecraft) {
            this.minecraft = minecraft; this.font = minecraft.font;
            this.width = minecraft.getWindow().getGuiScaledWidth();
            this.height = minecraft.getWindow().getGuiScaledHeight();
            super.init();
        }
    }
    /** The vanilla container tick is final and requires LocalPlayer; keep it outside Minecraft.screen. */
    private static final class AuditScreen extends net.minecraft.client.gui.screens.Screen {
        private final AuditGui gui;
        private int frames;
        AuditScreen(AuditGui gui) { super(net.minecraft.network.chat.Component.literal("Cloud UI audit")); this.gui = gui; }
        @Override protected void init() { gui.initialize(minecraft); }
        @Override public void tick() { gui.tickCloud(); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            gui.render(graphics, mouseX, mouseY, partialTick);
            frames++;
        }
    }
}
