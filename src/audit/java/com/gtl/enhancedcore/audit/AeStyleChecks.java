package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.gui.fancy.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.ui.*;
import com.gtl.enhancedcore.common.gui.PatternGeneratorWidget;
import com.gtl.enhancedcore.common.item.PatternGeneratorBehavior;
import com.gtl.enhancedcore.common.item.PatternGeneratorSettings;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.*;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Detached snapshots in production AE panels and held-tool screens, with root input and real rendering. */
public final class AeStyleChecks {
    private AeStyleChecks() {}
    public static List<GuiStyleClientChecks.Scene> scenes() {
        return List.of(scene("ae-cloud-library", true, ""), scene("ae-cloud-buffer", false, ""),
                scene("ae-cloud-orders", false, "orders"), scene("ae-cloud-cancel-warning", false, "cancel"),
                generator("ae-generator-main", "main"), generator("ae-generator-filters", "filters"),
                generator("ae-generator-preview", "previews"), generator("ae-generator-save", "savePreset"),
                scene("ae-cloud-orders-hint", false, "orders_hint"));
    }
    private static GuiStyleClientChecks.Scene scene(String id, boolean library, String page) {
        return new GuiStyleClientChecks.Scene(id, () -> cloud(library, page), screen -> ((AeScreen)screen).verify());
    }
    private static GuiStyleClientChecks.Scene generator(String id, String page) {
        return new GuiStyleClientChecks.Scene(id, () -> tool(page), screen -> ((AeScreen)screen).verify());
    }
    private static void require(boolean valid, String message) { GuiStyleClientChecks.require(valid, message); }

    private static AeScreen cloud(boolean library, String page) throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
        CloudPatternUiHost host = new CloudPatternUiHost() {
            public CompoundTag cloudUiSnapshot(ServerPlayer player, String search, String type, int page, String selected) { return snapshot(); }
            public void cloudUiAction(ServerPlayer player, String action, CompoundTag payload) {}
        };
        class Holder implements IUIHolder, IFancyUIProvider {
            public ModularUI createUI(Player player) { return CloudPatternUi.create(host, this, player, library); }
            public boolean isInvalid() { return false; }
            public boolean isRemote() { return true; }
            public void markAsDirty() {}
            public Widget createMainPage(FancyMachineUIWidget fancy) { return library ? new CloudPatternLibraryWidget(host) : new CloudPatternBufferWidget(host); }
            public IGuiTexture getTabIcon() { return IGuiTexture.EMPTY; }
            public Component getTitle() { return Component.translatable("gtl_enhancedcore.cloud.ui." + (library ? "library" : "buffer")); }
        }
        var ui = new Holder().createUI(fixture.player()); ui.initWidgets();
        var panel = ui.getFirstWidgetById(library ? "cloud.library" : "cloud.buffer");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { buffer.writeNbt(snapshot()); panel.readUpdateInfo(212, buffer); require(buffer.readableBytes() == 0, "Cloud detached snapshot consumed exactly"); }
        finally { buffer.release(); }
        fixture.player().containerMenu = ui.getModularUIContainer();
        return new AeScreen(ui, fixture.holders(), panel, page);
    }

    private static AeScreen tool(String page) throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
        Player player = fixture.player();
        // The fixture overrides getInventory; vanilla held-item access also needs that same inventory field.
        ObfuscationReflectionHelper.setPrivateValue(Player.class, player, player.getInventory(), "f_36093_");
        var tool = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation("gtl_enhancedcore", "pattern_generator")));
        require(!tool.isEmpty(), "Real registered pattern generator item exists");
        var settings = new PatternGeneratorSettings();
        settings.machine = Objects.requireNonNull(GTRegistries.MACHINES.get(new ResourceLocation("gtceu:lv_assembler"))).asStack();
        settings.recipeType = com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES.registryName.toString();
        settings.machine.setHoverName(Component.literal("装配机 / Assembler · 100% 原料保留"));
        for (int i = 0; i < 7; i++) { settings.inputInclude[i] = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1); settings.outputBlack[i] = new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1); }
        tool.getOrCreateTag().put(PatternGeneratorSettings.TAG, settings.write()); player.getInventory().setItem(0, tool);
        var holder = new HeldItemUIFactory.HeldItemHolder(player, InteractionHand.MAIN_HAND);
        var ui = PatternGeneratorBehavior.INSTANCE.createUI(holder, player); ui.initWidgets();
        var panel = (PatternGeneratorWidget)ui.getFirstWidgetById("pattern_generator");
        seedPreview(panel, settings);
        // Seed this detached menu's supported-type presentation with the actual registered type.
        // Machine definitions and the production discovery path remain untouched.
        GuiStyleClientChecks.field(PatternGeneratorWidget.class, "types").set(panel,
                List.of(com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES));
        player.containerMenu = ui.getModularUIContainer();
        return new AeScreen(ui, fixture.holders(), panel, page);
    }
    private static void seedPreview(PatternGeneratorWidget panel, PatternGeneratorSettings settings) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeNbt(settings.write()); buffer.writeVarInt(7); buffer.writeVarInt(0); buffer.writeVarInt(45); buffer.writeVarInt(30);
            buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeBoolean(false); buffer.writeBoolean(true); buffer.writeVarInt(0);
            buffer.writeComponent(Component.empty()); buffer.writeVarInt(1); buffer.writeUtf(settings.recipeType); buffer.writeVarInt(45); buffer.writeVarInt(45);
            Item[] icons = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.REDSTONE};
            for (int i = 0; i < 45; i++) {
                buffer.writeUtf("gtl_enhancedcore:audit/recipe_" + i); buffer.writeVarInt(i % 33); buffer.writeLong(128); buffer.writeUtf("MV"); buffer.writeBoolean(i % 3 != 0);
                buffer.writeVarInt(1); buffer.writeNbt(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4)));
                buffer.writeVarInt(1); buffer.writeNbt(GenericStack.writeTag(new GenericStack(AEItemKey.of(icons[i % icons.length]), 1)));
            }
            panel.readUpdateInfo(100, buffer); require(buffer.readableBytes() == 0, "Pattern generator real state packet consumed exactly");
        } finally { buffer.release(); }
    }
    private static CompoundTag snapshot() {
        var tag = new CompoundTag(); tag.putLong("revision", 7); tag.putBoolean("networkConnected", true); tag.putString("status", "gtl_enhancedcore.cloud.ui.available");
        tag.putInt("pages", 3); tag.putInt("total", 108); tag.putInt("matched", 108); tag.putInt("libraryTotal", 108);
        tag.putInt("active", 3); tag.putInt("queued", 8); tag.putInt("refundTypes", 14); tag.putInt("completedOutputTypes", 5);
        tag.putBoolean("tasksHealthy", true); tag.putBoolean("accepting", true); tag.putBoolean("returnNetworkReady", true);
        var types = new ListTag(); for (String id : List.of("gtceu:assembler", "gtceu:chemical_reactor")) types.add(StringTag.valueOf(id)); tag.put("types", types);
        var mode = new CompoundTag(); mode.putString("id", "gtceu:assembler"); mode.putBoolean("current", true); mode.putBoolean("available", true);
        var modes = new ListTag(); modes.add(mode); tag.put("modes", modes);
        var entries = new ListTag(); Item[] icons = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.REDSTONE};
        for (int i = 0; i < 36; i++) {
            Item item = icons[i % icons.length]; var entry = new CompoundTag(); entry.putString("id", new UUID(0, i + 1).toString());
            entry.putString("nameJson", Component.Serializer.toJson(item.getDescription())); entry.putString("type", "gtceu:assembler");
            entry.put("icon", item.getDefaultInstance().save(new CompoundTag())); entry.put("iconKey", AEItemKey.of(item).toTagGeneric()); entries.add(entry);
        }
        tag.put("entries", entries); return tag;
    }
    private static final class AeGui extends ModularUIGuiContainer {
        AeGui(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc) { minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight(); super.init(); }
        void guiTick() { super.containerTick(); }
    }
    private static final class AeScreen extends Screen {
        final ModularUI ui; final AeGui gui; final List<MetaMachineBlockEntity> holders; final Widget panel; final String page;
        int frames;
        AeScreen(ModularUI ui, List<MetaMachineBlockEntity> holders, Widget panel, String page) {
            super(Component.literal("AE style audit")); this.ui = ui; this.holders = holders; this.panel = panel; this.page = page; gui = new AeGui(ui);
        }
        @Override protected void init() {
            gui.initialize(minecraft);
            ui.mainGroup.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
                public void writeUpdateInfo(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
            });
            if (panel instanceof PatternGeneratorWidget) {
                try {
                    var view = (WidgetGroup)GuiStyleClientChecks.field(panel.getClass(), page).get(panel);
                    var show = panel.getClass().getDeclaredMethod("show", WidgetGroup.class); show.setAccessible(true); show.invoke(panel, view);
                } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
            } else if (page.equals("orders") || page.equals("cancel")) {
                click("cloud.orders.open"); if (page.equals("cancel")) click("cloud.orders.cancel");
            }
        }
        private void click(String id) {
            var control = Objects.requireNonNull(ui.getFirstWidgetById(id), id); var p = control.getPosition();
            require(ui.mainGroup.mouseClicked(p.x + control.getSizeWidth() / 2d, p.y + control.getSizeHeight() / 2d, 0), "Root input opens " + id);
            ui.mainGroup.mouseReleased(p.x + control.getSizeWidth() / 2d, p.y + control.getSizeHeight() / 2d, 0);
        }
        @Override public void tick() { gui.guiTick(); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            if (page.equals("orders_hint")) {
                var control = ui.getFirstWidgetById("cloud.orders.open"); var p = control.getPosition();
                gui.renderWithTooltip(graphics, p.x + control.getSizeWidth() / 2, p.y + control.getSizeHeight() / 2, partial);
            } else gui.renderWithTooltip(graphics, -100, -100, partial);
            frames++;
        }
        @Override public void removed() { holders.forEach(holder -> holder.setLevel(null)); }
        void verify() {
            require(frames > 0, "Actual production AE screen rendered");
            require(ui.getWidth() <= width && ui.getHeight() <= height, "AE shell fits current GUI viewport: " + ui.getWidth() + "x" + ui.getHeight());
            bounds((WidgetGroup)panel);
            screenBounds(ui.mainGroup);
            if (panel instanceof PatternGeneratorWidget) {
                require(ui.getSlotMap().size() == 36, "Pattern generator retains all 36 player inventory slots");
                require(ui.mainGroup.getWidgetsByType(com.gtl.enhancedcore.common.gui.PatternGeneratorGhostWidget.class).size() == 29, "Pattern generator retains one machine plus four groups of seven ghost slots");
                var selector = (SelectorWidget)ui.getFirstWidgetById("pattern_generator.type");
                require(selector.getValue().contains("45"), "Pattern generator displays the registered assembler snapshot and recipe count");
            } else {
                require(ui.getHeight() == 220, "Cloud shell removes spare margins while retaining full native inventory");
                var inventory = ui.mainGroup.getWidgetsByType(com.lowdragmc.lowdraglib.gui.widget.custom.PlayerInventoryWidget.class);
                require(inventory.size() == 1 && inventory.getFirst().getWidgetsByType(SlotWidget.class).size() == 36,
                        "Cloud shell retains all 36 native player inventory slots alongside catalyst slots");
                require(ui.mainGroup.widgets.stream().anyMatch(FancyMachineUIWidget.class::isInstance), "Cloud machine retains native Fancy wireless-compatible shell");
                require(ui.mainGroup.getWidgetsByType(ButtonWidget.class).stream().filter(button -> button.getId().matches("cloud\\.(buffer\\.)?pattern\\.[0-9]+" )).count() == 36, "Cloud terminal keeps the complete nine by four product grid");
                var orders = ui.getFirstWidgetById("cloud.orders.open");
                if (orders != null) {
                    require(orders.getSizeWidth() == 18 && orders.getSizeHeight() == 18, "Orders action is a full AE pixel-icon button");
                    var next = ui.getFirstWidgetById("cloud.page.next");
                    require(orders.getPositionY() + orders.getSizeHeight() <= next.getPositionY(), "Orders header action cannot overlap product pagination");
                    require(orders.getTooltipTexts().size() == 3, "Orders action exposes its title, cancellation warning and return-network explanation");
                }
            }
        }
        private void bounds(WidgetGroup group) {
            for (Widget child : group.widgets) {
                if (!child.isVisible()) continue;
                var p = child.getSelfPosition();
                require(p.x >= 0 && p.y >= 0 && p.x + child.getSizeWidth() <= group.getSizeWidth()
                        && p.y + child.getSizeHeight() <= group.getSizeHeight(), "AE child stays inside its visible panel: " + child.getId());
                if (child instanceof WidgetGroup nested && !(child instanceof DraggableScrollableWidgetGroup)) bounds(nested);
            }
        }
        private void screenBounds(WidgetGroup group) {
            for (Widget child : group.widgets) {
                if (!child.isVisible()) continue;
                var p = child.getPosition();
                // ConfiguratorPanel draws only its tabs; its declared single-column height is stale after wrapping.
                if (!(child instanceof ConfiguratorPanel) && child.getSizeWidth() > 0 && child.getSizeHeight() > 0) {
                    require(p.x >= 0 && p.y >= 0 && p.x + child.getSizeWidth() <= width
                            && p.y + child.getSizeHeight() <= height,
                            "AE visible shell, host tab or control stays on screen: " + child.getClass().getSimpleName() + ":" + child.getId()
                                    + " at " + p + " size " + child.getSize());
                }
                if (child instanceof WidgetGroup nested && !(child instanceof DraggableScrollableWidgetGroup)) screenBounds(nested);
            }
        }
    }
}
