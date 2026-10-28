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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Native compact AE terminal audit. Requires a fresh marked title-screen client, never a player world. */
@Mod("enhancedcore_audit")
public final class CloudPatternAeUiChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-network-actions-20261024");
    private final List<String> checks = new ArrayList<>(), actions = new ArrayList<>();
    private Path directory;
    private ModularUI ui;
    private Widget panel;
    private Player player;
    private SampleHost host;
    private AuditScreen screen;
    private int phase, ticks, lastFrames;
    private String lastSelected = "";
    private int lastPage;
    private CloudPatternSearch lastAliases = CloudPatternSearch.EMPTY;
    private CloudTerminalUiChecks.Session terminal;
    private boolean done;

    public CloudPatternAeUiChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.cloudAeUiAudit")) return;
        var mc = Minecraft.getInstance();
        if (phase == 0) {
            directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
            if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen) || mc.level != null
                    || mc.getSingleplayerServer() != null || !directory.startsWith(ROOT) || directory.equals(ROOT)
                    || !Files.isRegularFile(directory.resolve("cloud-ae-ui.marker"))) return;
        }
        try {
            if (phase == 0) {
                player = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false).player();
                for (String id : List.of("cloud_pattern_library", "cloud_pattern_buffer")) {
                    var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", id));
                    check(definition != null, "Real cloud machine registered: " + id);
                    var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(new BlockPos(40, 80, 0), definition.defaultBlockState());
                    holder.setLevel(player.level());
                    var actual = ((com.gregtechceu.gtceu.api.machine.feature.IUIMachine) holder.getMetaMachine()).createUI(player);
                    actual.initWidgets(); bounds(actual.mainGroup);
                    check(actual.getWidth() == (id.equals("cloud_pattern_library") ? 198 : 176) && actual.getHeight() == 232,
                            "Registered machine has the compact AE shell with a bounded library sidebar: " + id);
                    holder.setLevel(null);
                }
                open(true); phase = 1; ticks = 0;
            } else if (++ticks >= 4) {
                int frames = terminal == null ? screen.frames : terminal.frames();
                if (frames - lastFrames < 4) return;
                lastFrames = frames; ticks = 0;
                switch (phase++) {
                    case 1 -> {
                        screenshot("ae-library.png"); inventory(); removedControls();
                        verifyProductSearch("cloud.pattern.0");
                        verifyTypeCycle();
                        click("cloud.network.import", 0);
                        check(visible("cloud.network.confirm"), "Network import opens one native AE confirmation overlay");
                    }
                    case 2 -> {
                        screenshot("ae-library-network-confirm.png");
                        verifyNetworkConfirmation();
                        click("cloud.search", 0); ui.mainGroup.charTyped('铁', 0); ui.mainGroup.charTyped('锭', 0);
                        check(lastAliases.matches(appeng.api.stacks.AEItemKey.of(Items.IRON_INGOT)), "Native Chinese search sends iron-ingot registry aliases");
                        click("cloud.pattern.0", 2);
                        check(actions.contains("pattern_config") && lastSelected.equals("entry-0"), "Root middle click sends the selected pattern config action");
                        check(!visible("cloud.pattern.catalysts"), "Unconfirmed client click cannot expose catalyst inventories");
                        syncCatalyst(new ItemStack(Items.IRON_INGOT));
                        syncFluidCatalyst(com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(net.minecraft.world.level.material.Fluids.WATER, 1000));
                        confirm("entry-0");
                        check(catalyst().getItem().is(Items.IRON_INGOT), "Slot update arriving before panel confirmation survives the confirmed selection");
                        checkFluidCatalyst(net.minecraft.world.level.material.Fluids.WATER, 1000, "Fluid initial packet arriving before panel confirmation remains in the actual rendered tank");
                    }
                    case 3 -> {
                        screenshot("ae-library-catalysts.png"); inventory();
                        check(visible("cloud.pattern.catalysts"), "Catalyst page appears after the matching server confirmation");
                        check(count(ui.mainGroup, TankWidget.class) == 9 && count(ui.mainGroup, SlotWidget.class) == 46, "Native tree contains 9 fluid catalysts, 9 item catalysts, import slot and 36 player slots");
                        transferToCatalyst();
                        check(host.itemCalls.isEmpty() && host.fluidCalls.isEmpty(), "Client catalysts use fixed local mirrors without calling server authority getters");
                        click("cloud.pattern.back", 0); check(actions.contains("close_pattern_config"), "Back closes the confirmed server entry");
                        var mirrorSlot = catalyst();
                        click("cloud.pattern.1", 2); syncCatalyst(ItemStack.EMPTY); syncFluidCatalyst(com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty()); confirm("entry-1");
                        check(catalyst().getItem().isEmpty(), "Another pattern never exposes the first pattern's catalysts");
                        check(renderedFluidCatalyst().isEmpty(), "Another pattern's empty fluid packet clears the previous visible catalyst");
                        var mirrorTank = fluidCatalyst();
                        click("cloud.pattern.back", 0); click("cloud.pattern.0", 2); syncCatalyst(new ItemStack(Items.IRON_INGOT));
                        syncFluidCatalyst(com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(net.minecraft.world.level.material.Fluids.WATER, 1000)); confirm("entry-0");
                        check(catalyst() == mirrorSlot && catalyst().getItem().is(Items.IRON_INGOT), "Entry changes preserve the fixed native slot identity and authoritative synchronized item");
                        check(fluidCatalyst() == mirrorTank, "Entry changes preserve the fixed native TankWidget identity");
                        checkFluidCatalyst(net.minecraft.world.level.material.Fluids.WATER, 1000, "Returning to the original entry restores its synchronized rendered fluid catalyst");
                        click("cloud.pattern.back", 0); click("cloud.pattern.0", 2);
                        check(visible("cloud.pattern.catalysts"), "One middle click reopens an already confirmed entry without waiting for an identical snapshot");
                        var denied = sample(); denied.putString("catalystEntry", "entry-1"); denied.putBoolean("patternCatalystsAllowed", true); receive(denied);
                        check(!visible("cloud.pattern.catalysts"), "A mismatched server entry closes the visible catalyst page");
                        click("cloud.page.next", 0); check(lastPage == 1, "Next requests a bounded second page, separate from total capacity");
                        check(ui.getFirstWidgetById("cloud.import") instanceof ImageWidget
                                        && ui.getFirstWidgetById("cloud.import.slot") instanceof SlotWidget,
                                "Header exposes a compact upload marker and real import slot without a redundant action button");
                        click("cloud.pattern.0", 0);
                        check(Collections.frequency(actions, "take_pattern") == 1, "Ordinary product click requests the exact original onto the cursor");
                        var sparse = sample(); sparse.getList("entries", Tag.TAG_COMPOUND).remove(35); receive(sparse);
                        player.containerMenu.setCarried(appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                                new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.IRON_INGOT), 1)},
                                new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(Items.GOLD_INGOT), 1)}));
                        click("cloud.pattern.35", 0);
                        check(Collections.frequency(actions, "import_cursor") == 1, "Held encoded pattern uploads through an empty product-grid cell");
                        player.containerMenu.setCarried(ItemStack.EMPTY);
                        open(false);
                    }
                    case 4 -> {
                        screenshot("ae-buffer.png"); inventory(); removedControls();
                        verifyProductSearch("cloud.buffer.pattern.0");
                        click("cloud.mode", 0); check(actions.contains("set_mode"), "Compact mode selector submits a native action");
                        click("cloud.buffer.pattern.4", 2); confirm("entry-4");
                        syncCatalyst(new ItemStack(Items.IRON_INGOT));
                        syncFluidCatalyst(com.lowdragmc.lowdraglib.side.fluid.FluidStack.create(net.minecraft.world.level.material.Fluids.WATER, 1000));
                        checkFluidCatalyst(net.minecraft.world.level.material.Fluids.WATER, 1000, "Fluid initial packet arriving after buffer confirmation populates the actual rendered tank");
                    }
                    case 5 -> {
                        screenshot("ae-buffer-catalysts.png"); inventory();
                        check(visible("cloud.pattern.catalysts"), "Buffer opens only the selected server-confirmed pattern's catalyst page");
                        click("cloud.pattern.back", 0); click("cloud.buffer.pattern.2", 0);
                        screen.hover("cloud.buffer.pattern.2");
                    }
                    case 6 -> {
                        screenshot("ae-buffer-tooltip.png");
                        terminal = CloudTerminalUiChecks.open(player); lastFrames = 0;
                    }
                    case 7 -> { terminal.verify(false, this::check); screenshot("ae-terminal-unbound.png"); terminal.bound(true); }
                    case 8 -> { terminal.verify(true, this::check); screenshot("ae-terminal-bound.png"); finish(null); }
                    default -> throw new AssertionError("Unexpected audit phase");
                }
            }
        } catch (Throwable error) { error.printStackTrace(); finish(error); }
    }

    private void open(boolean library) throws Exception {
        host = new SampleHost(); lastSelected = ""; lastPage = 0;
        IUIHolder holder = new IUIHolder() {
            public ModularUI createUI(Player viewer) { return CloudPatternUi.create(host, this, viewer, library); }
            public boolean isInvalid() { return false; }
            public boolean isRemote() { return true; }
            public void markAsDirty() {}
        };
        ui = holder.createUI(player); ui.initWidgets(); bounds(ui.mainGroup);
        check(ui.getWidth() <= 427 && ui.getHeight() <= 240, "Whole menu fits the 427x240 high-scale logical viewport");
        panel = ui.getFirstWidgetById(library ? "cloud.library" : "cloud.buffer"); receive(sample());
        screen = new AuditScreen(new AuditGui(ui)); Minecraft.getInstance().setScreen(screen); lastFrames = 0;
        player.containerMenu = ui.getModularUIContainer();
        initialRootRoundTrip(library);
        panel.setUiAccess(new WidgetUIAccess() {
            public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
            public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
            public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    writer.accept(buffer);
                    if (id == 210) {
                        buffer.readUtf(96); buffer.readUtf(128); lastPage = buffer.readVarInt(); lastSelected = buffer.readUtf(128);
                        buffer.readVarInt(); lastAliases = CloudPatternSearch.read(buffer);
                        check(buffer.readableBytes() == 0, "Native query consumes its bounded search, selection and page fields"); actions.add("query");
                    } else if (id == 211) {
                        String action = buffer.readUtf(32); var payload = buffer.readNbt();
                        check(payload != null && payload.getLong("revision") == 7, "Action carries received server revision: " + action);
                        if (action.equals("set_mode")) check(payload.getString("type").equals("gtceu:chemical_reactor"), "Mode cycling skips a mode claimed by another node");
                        actions.add(action);
                    }
                } finally { buffer.release(); }
            }
        });
    }
    private void initialRootRoundTrip(boolean library) throws Exception {
        // TankWidget's initial reader is client-only. Exercise the actual complete
        // root here, rather than calling a stripped client method on a dedicated server.
        var snapshotField = panel.getClass().getSuperclass().getDeclaredField("snapshot");
        snapshotField.setAccessible(true);
        var expected = ((CompoundTag) snapshotField.get(panel)).copy();
        String name = library ? "library" : "buffer";
        check(expected.getBoolean("networkConnected") && !expected.getList("entries", Tag.TAG_COMPOUND).isEmpty()
                        && expected.getInt("matched") > 0,
                "Client root begins with connected populated snapshot: " + name);
        var previous = player.getInventory().getItem(9).copy();
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 3));
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ui.mainGroup.writeInitialData(packet);
            check(packet.readableBytes() > 0, "Real client root writes a nonempty native initial packet: " + name);
            receive(new CompoundTag());
            check(((CompoundTag) snapshotField.get(panel)).isEmpty(),
                    "Native update reader clears the snapshot before initial root read: " + name);
            ui.mainGroup.readInitialData(packet);
            check(packet.readableBytes() == 0, "Real client root initial reader consumes every byte: " + name);
            var received = (CompoundTag) snapshotField.get(panel);
            check(received.getBoolean("networkConnected") && !received.getList("entries", Tag.TAG_COMPOUND).isEmpty()
                            && received.getInt("matched") == expected.getInt("matched") && received.equals(expected),
                    "Root initial round trip restores connected nonzero entries and matched snapshot exactly: " + name);
            check(player.getInventory().getItem(9).is(Items.IRON_INGOT) && player.getInventory().getItem(9).getCount() == 3,
                    "Root initial reader preserves the populated native player-inventory slot: " + name);
        } finally { packet.release(); player.getInventory().setItem(9, previous); }
    }
    private void receive(CompoundTag snapshot) {
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.writeNbt(snapshot); panel.readUpdateInfo(212, packet); check(packet.readableBytes() == 0, "Snapshot packet is consumed exactly"); }
        finally { packet.release(); }
    }
    private void confirm(String entry) {
        var snapshot = sample(); snapshot.putString("catalystEntry", entry); snapshot.putBoolean("patternCatalystsAllowed", true);
        int index = Integer.parseInt(entry.substring(6)); snapshot.put("detail", snapshot.getList("entries", Tag.TAG_COMPOUND).getCompound(index).copy()); receive(snapshot);
    }
    private void inventory() {
        long nativeSlots = ui.getSlotMap().keySet().stream().filter(slot -> slot.container == player.getInventory()).count();
        long visibleSlots = ui.getSlotMap().entrySet().stream().filter(entry -> entry.getKey().container == player.getInventory() && entry.getValue().isVisible()).count();
        check(nativeSlots == 36 && visibleSlots == 36, "All 27 inventory and 9 hotbar slots remain visible and native");
    }
    private void removedControls() {
        for (String id : List.of("cloud.tab.catalysts", "cloud.tab.settings", "cloud.pause", "cloud.cancel", "cloud.delegate", "cloud.inventory", "cloud.terminal_diagnostics"))
            check(ui.getFirstWidgetById(id) == null, "Removed sidebar/management control stays absent: " + id);
    }
    private net.minecraft.world.inventory.Slot catalyst() { return ((SlotWidget) ui.getFirstWidgetById("cloud.catalyst.0")).getHandler(); }
    private void syncCatalyst(ItemStack stack) {
        var menu = ui.getModularUIContainer();
        menu.setItem(catalyst().index, menu.getStateId(), stack.copy());
    }
    private TankWidget fluidCatalyst() { return (TankWidget) ui.getFirstWidgetById("cloud.catalyst.fluid.0"); }
    private void syncFluidCatalyst(com.lowdragmc.lowdraglib.side.fluid.FluidStack stack) {
        // Serialize through LDLib's real initial writer and feed the actual menu
        // TankWidget's reader; changing SampleHost alone cannot populate this client render cache.
        var source = new FluidStorage(16000);
        source.setFluidInTank(0, stack.copy());
        var sourceWidget = new TankWidget(source, 0, 0, true, true);
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            sourceWidget.writeInitialData(packet);
            check(packet.readableBytes() > 0, "Native fluid catalyst initial writer emits a populated packet");
            fluidCatalyst().readInitialData(packet);
            check(packet.readableBytes() == 0, "Actual fluid catalyst TankWidget consumes the native initial packet exactly");
        } finally { packet.release(); }
    }
    private com.lowdragmc.lowdraglib.side.fluid.FluidStack renderedFluidCatalyst() {
        try {
            var field = TankWidget.class.getDeclaredField("lastFluidInTank"); field.setAccessible(true);
            var value = (com.lowdragmc.lowdraglib.side.fluid.FluidStack) field.get(fluidCatalyst());
            return value == null ? com.lowdragmc.lowdraglib.side.fluid.FluidStack.empty() : value.copy();
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private void checkFluidCatalyst(net.minecraft.world.level.material.Fluid fluid, long amount, String message) {
        var value = renderedFluidCatalyst();
        check(!value.isEmpty() && value.getFluid() == fluid && value.getAmount() == amount, message);
    }
    private void verifyProductSearch(String id) {
        var cloud = (CloudPatternPanel) panel;
        var widget = ui.getFirstWidgetById(id); var position = widget.getPosition();
        String name = cloud.hoveredSearchText(position.x + 8, position.y + 8);
        check(name.equals(new ItemStack(Items.IRON_INGOT).getHoverName().getString()), "Own product cell exposes the localized EAEP fill-search text: " + id);
        check(com.gtl.enhancedcore.client.cloud.CloudPatternSearchInput.tryFillSearch(cloud, 70, 0, position.x + 8, position.y + 8),
                "EAEP's actual configured F key fills this native product search: " + id);
        check(((TextFieldWidget) ui.getFirstWidgetById("cloud.search")).getCurrentString().equals(name), "Fill-search updates the actual native search widget: " + id);
        click("cloud.search", 0);
        check(cloud.isEditingSearch(), "Focused native search retains normal text editing: " + id);
        check(!com.gtl.enhancedcore.client.cloud.CloudPatternSearchInput.tryFillSearch(cloud, 70, 0, position.x + 8, position.y + 8),
                "F remains normal text input while the native search is focused: " + id);
        ui.getFirstWidgetById("cloud.search").setFocus(false); cloud.fillSearch("");
        try {
            var key = (net.minecraft.client.KeyMapping) Class.forName("com.extendedae_plus.client.ModKeybindings").getField("FILL_SEARCH_KEY").get(null);
            var previous = key.getKey();
            try {
                key.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(71));
                check(!com.gtl.enhancedcore.client.cloud.CloudPatternSearchInput.tryFillSearch(cloud, 70, 0, position.x + 8, position.y + 8)
                                && com.gtl.enhancedcore.client.cloud.CloudPatternSearchInput.tryFillSearch(cloud, 71, 0, position.x + 8, position.y + 8),
                        "Fill-search follows EAEP's actual remapped key instead of hardcoding F: " + id);
            } finally { key.setKey(previous); }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        cloud.fillSearch("");
    }
    private void verifyTypeCycle() {
        var field = CloudPatternPanel.class;
        try {
            var selectedType = field.getDeclaredField("type"); selectedType.setAccessible(true);
            click("cloud.type", 0); check(selectedType.get(panel).equals("gtceu:assembler"), "Type filter cycles into an actual recipe type");
            click("cloud.type", 0); check(selectedType.get(panel).equals(""), "Type filter cycles directly back to all types without archived state");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private void verifyNetworkConfirmation() {
        var cloud = (CloudPatternPanel) panel;
        var overlay = ui.getFirstWidgetById("cloud.network.confirm");
        int overlays = count(ui.mainGroup, DialogWidget.class);
        int actionsBefore = actions.size();
        click("cloud.network.import", 0); click("cloud.pattern.0", 0); click("cloud.import.slot", 0);
        var slot = ui.getSlotMap().entrySet().stream().filter(e -> e.getKey().container == player.getInventory()).findFirst().orElseThrow().getValue();
        var position = slot.getPosition();
        check(ui.mainGroup.mouseClicked(position.x + 8, position.y + 8, 0), "Confirmation swallows native player-inventory slot clicks");
        check(ui.mainGroup.mouseDragged(position.x + 8, position.y + 8, 0, 2, 2)
                        && ui.mainGroup.mouseWheelMove(position.x + 8, position.y + 8, 1)
                        && ui.mainGroup.keyPressed(69, 0, 0) && ui.mainGroup.charTyped('f', 0),
                "Confirmation swallows drag, wheel, inventory key and text before native menu controls");
        check(actions.size() == actionsBefore && count(ui.mainGroup, DialogWidget.class) == overlays
                        && ui.getFirstWidgetById("cloud.network.confirm") == overlay && !cloud.canFillSearch(),
                "Confirmation blocks underlying actions, repeated dialogs and fill-search");
        click("cloud.network.confirm.yes", 1);
        check(overlay.isVisible() && !actions.contains("network_import"), "Right click cannot confirm the destructive network import");
        click("cloud.network.confirm.no", 0);
        check(!overlay.isVisible() && !actions.contains("network_import") && cloud.canFillSearch(), "No closes confirmation without importing or disabling normal controls");
        click("cloud.network.import", 0);
        ui.mainGroup.keyPressed(256, 0, 0);
        check(!actions.contains("network_import") && cloud.canFillSearch(), "Escape cancels confirmation without importing");
        click("cloud.network.import", 0); click("cloud.network.confirm.yes", 0);
        check(Collections.frequency(actions, "network_import") == 1 && cloud.canFillSearch(), "Yes sends the host network_import action once and restores normal controls");
    }
    private void transferToCatalyst() {
        var menu = ui.getModularUIContainer(); player.containerMenu = menu;
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT));
        var source = ui.getSlotMap().keySet().stream().filter(slot -> slot.container == player.getInventory() && slot.getContainerSlot() == 9).findFirst().orElseThrow();
        menu.clicked(source.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().is(Items.IRON_INGOT), "Native menu puts the real inventory item onto the cursor");
        menu.clicked(catalyst().index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        check(menu.getCarried().isEmpty() && catalyst().getItem().is(Items.IRON_INGOT), "Native menu transfers an inventory item into the selected pattern's catalyst slot");
    }
    private boolean visible(String id) { return ui.getFirstWidgetById(id).isVisible(); }
    private void click(String id, int button) {
        var widget = ui.getFirstWidgetById(id); check(widget != null, "Native widget exists: " + id);
        var position = widget.getPosition(); int x = position.x + widget.getSizeWidth() / 2, y = position.y + widget.getSizeHeight() / 2;
        check(ui.mainGroup.mouseClicked(x, y, button), "Full root dispatch handles button " + button + ": " + id); ui.mainGroup.mouseReleased(x, y, button);
    }
    private static int count(Widget widget, Class<?> type) {
        int count = type.isInstance(widget) ? 1 : 0;
        if (widget instanceof WidgetGroup group) for (Widget child : group.widgets) count += count(child, type); return count;
    }
    private void bounds(WidgetGroup group) {
        for (Widget widget : group.widgets) {
            var position = widget.getSelfPosition(); var size = widget.getSize();
            check(position.x >= 0 && position.y >= 0 && position.x + size.width <= group.getSizeWidth() && position.y + size.height <= group.getSizeHeight(),
                    "Widget remains inside its parent: " + widget.getId());
            if (widget instanceof WidgetGroup nested) bounds(nested);
        }
    }
    private void screenshot(String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) { image.writeToFile(directory.resolve(name)); }
        checks.add("Real native render screenshot: " + name);
    }
    private void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks.add(message); }
    private void finish(Throwable error) {
        done = true;
        var report = new LinkedHashMap<String, Object>(); report.put("status", error == null ? "PASS" : "FAIL");
        if (error != null) report.put("error", error.toString());
        report.put("checks", checks); report.put("actions", actions);
        report.put("scope", "Real registered UI and native LDLib rendering/root input/menu inventory transfers. Server-confirmed snapshots are fixtures. No saved player world, service mutation or crafting processing.");
        try { Files.writeString(directory.resolve("cloud-ae-ui-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
        Minecraft.getInstance().stop();
    }
    private static CompoundTag sample() {
        var tag = new CompoundTag(); tag.putLong("revision", 7); tag.putInt("page", 0); tag.putInt("pages", 2000); tag.putInt("total", 72000);
        tag.putInt("matched", 72000); tag.putInt("libraryTotal", 72000); tag.putInt("active", 2); tag.putInt("queued", 4);
        tag.putString("status", "gtl_enhancedcore.cloud.ui.available"); tag.putBoolean("networkConnected", true);
        tag.putString("networkStatus", "gtl_enhancedcore.cloud.ui.available");
        var entries = new ListTag();
        Item[] materials = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.REDSTONE, Items.EMERALD, Items.COPPER_INGOT,
                Items.LAPIS_LAZULI, Items.QUARTZ, Items.COAL, Items.AMETHYST_SHARD, Items.NETHERITE_INGOT, Items.GLOWSTONE_DUST,
                Items.REPEATER, Items.COMPARATOR, Items.ENDER_PEARL, Items.BLAZE_ROD, Items.GLASS, Items.OBSIDIAN};
        for (int i = 0; i < 36; i++) {
            var entry = new CompoundTag(); var stack = new ItemStack(materials[i % materials.length]);
            entry.putString("id", "entry-" + i); entry.putLong("revision", 7); entry.putString("nameJson", Component.Serializer.toJson(stack.getHoverName()));
            entry.putString("type", "gtceu:assembler"); entry.putBoolean("leased", i % 5 == 2); entry.putString("author", "AE Terminal");
            entry.put("icon", stack.save(new CompoundTag()));
            entry.put("iconKey", (i == 8 ? appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER)
                    : appeng.api.stacks.AEItemKey.of(stack)).toTagGeneric());
            entries.add(entry);
        }
        tag.put("entries", entries); tag.put("detail", entries.getCompound(0).copy());
        var modes = new ListTag();
        for (String id : List.of("gtceu:assembler", "gtceu:centrifuge", "gtceu:chemical_reactor")) {
            var mode = new CompoundTag(); mode.putString("id", id); mode.putBoolean("current", id.equals("gtceu:assembler"));
            mode.putBoolean("available", id.equals("gtceu:chemical_reactor")); modes.add(mode);
        }
        tag.put("modes", modes); var types = new ListTag(); types.add(StringTag.valueOf("gtceu:assembler")); tag.put("types", types); return tag;
    }
    private static final class SampleHost implements CloudPatternUiHost {
        final Map<String, Integer> itemCalls = new HashMap<>(), fluidCalls = new HashMap<>();
        private final ItemStackTransfer input = new ItemStackTransfer(1);
        public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected) { return sample(); }
        public void cloudUiAction(ServerPlayer player, String action, CompoundTag payload) {}
        public IItemTransfer cloudImportInventory(Player player) { return input; }
        public IItemTransfer cloudPatternItemCatalysts(Player player, String entryId) { itemCalls.merge(entryId, 1, Integer::sum); return new ItemStackTransfer(9); }
        public IFluidTransfer cloudPatternFluidCatalysts(Player player, String entryId) {
            fluidCalls.merge(entryId, 1, Integer::sum);
            return new FluidTransferList(java.util.stream.IntStream.range(0, 9).mapToObj(i -> new FluidStorage(16000)).toArray(IFluidTransfer[]::new));
        }
    }
    private static final class AuditGui extends ModularUIGuiContainer {
        AuditGui(ModularUI ui) { super(ui, 1); }
        void tickCloud() { super.containerTick(); }
        void initialize(Minecraft mc) {
            minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight(); super.init();
        }
    }
    private final class AuditScreen extends net.minecraft.client.gui.screens.Screen {
        private final AuditGui gui;
        private int frames, mouseX = -100, mouseY = -100;
        AuditScreen(AuditGui gui) { super(Component.literal("Native AE cloud UI audit")); this.gui = gui; }
        void hover(String id) { var widget = ui.getFirstWidgetById(id); mouseX = widget.getPosition().x + 8; mouseY = widget.getPosition().y + 8; }
        @Override protected void init() { gui.initialize(minecraft); }
        @Override public void tick() { gui.tickCloud(); }
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int mx, int my, float partialTick) {
            gui.render(graphics, mouseX, mouseY, partialTick); frames++;
        }
    }
}
