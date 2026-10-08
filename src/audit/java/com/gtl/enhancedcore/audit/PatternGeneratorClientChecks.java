package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.client.PatternGeneratorNameSearch;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.gui.PatternGeneratorGhostWidget;
import com.gtl.enhancedcore.common.gui.PatternGeneratorWidget;
import com.gtl.enhancedcore.common.item.PatternGeneratorBehavior;
import com.gtl.enhancedcore.common.item.PatternGeneratorSettings;
import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Actual OpenGL GUI with a disposable dummy player; never opens a player's world. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class PatternGeneratorClientChecks {
    private static int phase, wait, checks, packets;
    private static boolean done;
    private static Player player;
    private static ModularUI ui;
    private static PatternGeneratorWidget widget;
    private static CompletableFuture<Void> reload;
    private static Path fixture;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new IllegalStateException(reason);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("gtl.enhancedcore.patternGeneratorAudit") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        mc.getToasts().clear();
        try {
            if (phase == 0) {
                if (mc.level == null || mc.player == null || ++wait < 30) return;
                fixture = Path.of(System.getProperty("gtl.enhancedcore.patternGeneratorFixture"));
                player = new Player(new TrackedDummyWorld(mc.level), BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "PatternGuiAudit")) {
                    @Override public boolean isSpectator() { return false; }
                    @Override public boolean isCreative() { return true; }
                };
                check(player.level().isClientSide, "Dummy player runs the real client-side controls");
                open(false);
                phase++; wait = 0;
                return;
            }
            if (reload != null && !reload.isDone()) return;
            if (++wait < 30) return;
            wait = 0;
            if (phase == 1) {
                screenshot("01-drag-machine.png");
                open(true);
                check(ui.getWidth() == 310 && ui.getHeight() == 240, "Actual menu uses the intended GT layout dimensions");
                check(ui.getGuiTop() >= 0 && ui.getGuiLeft() >= 0 && ui.getGuiTop() + ui.getHeight() <= ui.getScreenHeight(), "Menu fits the actual scaled game window");
                check(ui.getSlotMap().size() == 36, "All real inventory slots are visible");
                var iron = PatternGeneratorNameSearch.find("铁锭");
                check(iron.items().get(BuiltInRegistries.ITEM.getId(Items.IRON_INGOT)), "Chinese material-name whitelist resolves iron ingot");
                var machine = (PatternGeneratorGhostWidget) ui.getFirstWidgetById("pattern_generator.machine");
                check(machine.getPhantomTargets(new ItemStack(Items.APPLE)).isEmpty(), "Machine slot rejects a non-machine JEI ingredient");
                var typed = mezz.jei.library.ingredients.TypedIngredient.createUnvalidated(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, player.getInventory().getItem(9));
                var handler = new com.lowdragmc.lowdraglib.jei.ModularUIJeiHandler();
                var targets = handler.getTargetsTyped((ModularUIGuiContainer) mc.screen, typed, true);
                check(targets.size() == 1, "Machine slot accepts a machine JEI ingredient");
                int before = packets;
                targets.getFirst().accept(player.getInventory().getItem(9));
                check(packets > before && player.getInventory().getItem(9).getCount() == 1, "JEI ghost drop sends an action without consuming the inventory item");
            } else if (phase == 2) {
                screenshot("02-main-menu.png");
                click("pattern_generator.filters");
                check(group("filters").isVisible(), "Filter button opens the optional filter page");
                var ghost = (PatternGeneratorGhostWidget) ui.getFirstWidgetById("pattern_generator.input_black.0");
                int before = packets;
                var typedFluid = mezz.jei.library.ingredients.TypedIngredient.createUnvalidated(mezz.jei.api.forge.ForgeTypes.FLUID_STACK, new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
                ghost.getPhantomTargets(typedFluid).getFirst().accept(typedFluid);
                check(packets > before, "Fluid ghost drop emits its server action");
                for (String side : java.util.List.of("input", "output")) {
                    var include = (PatternGeneratorGhostWidget) ui.getFirstWidgetById("pattern_generator." + side + "_include");
                    var typed = mezz.jei.library.ingredients.TypedIngredient.createUnvalidated(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, new ItemStack(Items.IRON_INGOT));
                    check(include.getPhantomTargets(typed).size() == 1 && include.getPhantomTargets(typedFluid).size() == 1, "Both inclusion slots accept JEI's actual typed item/fluid wrapper");
                }
            } else if (phase == 3) {
                screenshot("03-item-fluid-filters.png");
                var show = PatternGeneratorWidget.class.getDeclaredMethod("show", WidgetGroup.class);
                show.setAccessible(true); show.invoke(widget, group("main"));
                click("pattern_generator.circuit");
                check(group("circuits").isVisible(), "Circuit button opens the 1-32 grid");
            } else if (phase == 4) {
                screenshot("04-circuit-1-32.png");
                show("previews");
                var cells = group("grid").widgets;
                check(cells.size() == 45, "Default preview actually constructs 3 columns of 15 cells");
                check(!group("inventory").isVisible(), "Preview page does not overlap the player's inventory");
            } else if (phase == 5) {
                screenshot("05-preview-three-columns.png");
                var packet = new FriendlyByteBuf(Unpooled.wrappedBuffer(Files.readAllBytes(fixture.resolve("pattern-generator-state-ten-columns.bin"))));
                try { widget.readUpdateInfo(100, packet); check(packet.readableBytes() == 0, "Ten-column preview consumes the actual server packet"); }
                finally { packet.release(); }
                check(group("grid").widgets.size() == 150, "Ten columns render all 150 real recipe cells");
            } else if (phase == 6) {
                screenshot("06-preview-ten-columns.png");
                var grid = (com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup) group("grid");
                boolean scrolled = true;
                for (int step = 0; step < 16; step++) scrolled &= grid.mouseWheelMove(grid.getPosition().x + 5, grid.getPosition().y + 5, -1);
                check(scrolled, "Mouse wheel scrolls the real preview viewport");
                check(grid.widgets.get(14).isVisible() && !grid.widgets.getFirst().isVisible(), "The fifteenth row is reachable without covering the header/footer");
            } else if (phase == 7) {
                screenshot("07-preview-last-row.png");
                var requested = PatternGeneratorWidget.class.getDeclaredField("saveRequested"); requested.setAccessible(true); requested.set(widget, true);
                var packet = new FriendlyByteBuf(Unpooled.wrappedBuffer(Files.readAllBytes(fixture.resolve("pattern-generator-preset-file.bin"))));
                try { widget.readUpdateInfo(120, packet); check(packet.readableBytes() == 0, "Local save consumes the actual server export packet"); }
                finally { packet.release(); }
                check(!com.gtl.enhancedcore.client.PatternGeneratorPresets.list().presets().isEmpty(), "Server-exported preset is saved in the isolated client's local directory");
                show("main"); click("pattern_generator.presets");
                var rows = group("presetList").widgets;
                check(!rows.isEmpty(), "Preset submenu lists local/shared presets");
                var first = rows.getFirst();
                first.mouseClicked(first.getPosition().x + 4, first.getPosition().y + 4, 0);
                var preview = new FriendlyByteBuf(Unpooled.wrappedBuffer(Files.readAllBytes(fixture.resolve("pattern-generator-preset-preview.bin"))));
                var delivered = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    preview.readVarInt(); delivered.writeVarInt((int) value("presetRequest")); delivered.writeBytes(preview);
                    widget.readUpdateInfo(121, delivered);
                    check(delivered.readableBytes() == 0 && (int) value("inspectedAvailable") == 2, "Preset submenu renders an actual server response");
                } finally { preview.release(); delivered.release(); }
            } else if (phase == 8) {
                screenshot("08-shared-preset.png");
                show("main"); click("pattern_generator.save_preset");
            } else if (phase == 9) {
                screenshot("09-save-preset.png");
                mc.getLanguageManager().setSelected("en_us"); mc.options.languageCode = "en_us";
                reload = mc.reloadResourcePacks();
            } else if (phase == 10) {
                reload.join(); reload = null;
                open(true);
                var iron = PatternGeneratorNameSearch.find("iron ingot");
                check(iron.items().get(BuiltInRegistries.ITEM.getId(Items.IRON_INGOT)), "English material-name search rebuilds after language reload");
            } else if (phase == 11) {
                screenshot("10-english-main.png");
                show("previews");
            } else if (phase == 12) {
                screenshot("11-english-preview.png");
                GTLEnhancedcore.LOGGER.info("[PATTERN_GUI_CLIENT] COMPLETE checks={} emitted_actions={} screenshots=11 no_player_world=true typed_jei=true", checks, packets);
                done = true;
                mc.stop();
            }
            phase++;
        } catch (Throwable error) {
            done = true;
            GTLEnhancedcore.LOGGER.error("[PATTERN_GUI_CLIENT] FAIL phase=" + phase, error);
            mc.stop();
        }
    }

    private static WidgetGroup group(String name) throws Exception {
        return (WidgetGroup) value(name);
    }

    private static Object value(String name) throws Exception {
        Field field = PatternGeneratorWidget.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(widget);
    }

    private static void show(String name) throws Exception {
        var method = PatternGeneratorWidget.class.getDeclaredMethod("show", WidgetGroup.class); method.setAccessible(true);
        method.invoke(widget, group(name));
    }

    private static void open(boolean configured) throws Exception {
        var tool = GTLEnhancedcoreItems.PATTERN_GENERATOR.asStack();
        if (configured) tool.getOrCreateTag().put(PatternGeneratorSettings.TAG,
                NbtIo.read(fixture.resolve("pattern-generator-settings.nbt").toFile()));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        if (configured) player.getInventory().setItem(9, ItemStack.of(tool.getTag().getCompound(PatternGeneratorSettings.TAG).getCompound("machine")));
        var holder = new HeldItemUIFactory.HeldItemHolder(player, InteractionHand.MAIN_HAND);
        ui = PatternGeneratorBehavior.INSTANCE.createUI(holder, player);
        ui.initWidgets();
        widget = (PatternGeneratorWidget) ui.getFirstWidgetById("pattern_generator");
        if (configured) {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(Files.readAllBytes(fixture.resolve("pattern-generator-state-only.bin"))));
            try { widget.readUpdateInfo(100, buffer); check(buffer.readableBytes() == 0, "Client consumes the real server's complete state packet"); }
            finally { buffer.release(); }
        }
        Minecraft.getInstance().setScreen(new ModularUIGuiContainer(ui, 1));
        widget.setUiAccess(new WidgetUIAccess() {
            @Override public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
            @Override public void writeClientAction(Widget source, int id, Consumer<FriendlyByteBuf> writer) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try { writer.accept(buffer); check(buffer.readableBytes() > 0 || id >= 101, "Client action has a payload or a known root action"); packets++; }
                finally { buffer.release(); }
            }
            @Override public void writeUpdateInfo(Widget source, int id, Consumer<FriendlyByteBuf> writer) {}
        });
    }

    private static void click(String id) {
        var button = ui.getFirstWidgetById(id);
        check(button != null && button.isActive(), "Required GUI button is available: " + id);
        var pos = button.getPosition(); var size = button.getSize();
        button.mouseClicked(pos.x + size.width / 2.0, pos.y + size.height / 2.0, 0);
    }

    private static void screenshot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                message -> GTLEnhancedcore.LOGGER.info("[PATTERN_GUI_CLIENT] SCREENSHOT {} {}", name, message.getString()));
    }
}
