package com.gtl.enhancedcore.audit;

import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingLogic;
import com.gtl.enhancedcore.client.cloud.CloudLibraryUploadButton;
import com.gtl.enhancedcore.common.cloud.CloudTerminalMenu;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Render the actual transformed AE screen. Only the client host and received GuiSync bit are seeded. */
final class CloudTerminalUiChecks {
    static Session open(Player player) throws Exception {
        var host = new Host(); host.setLevel(player.level());
        var pattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT), 1)},
                new appeng.api.stacks.GenericStack[]{new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.GOLD_INGOT), 1)});
        host.logic.getEncodedPatternInv().setItemDirect(0, pattern);
        var menu = new PatternEncodingTermMenu(73, player.getInventory(), host);
        menu.mode = appeng.parts.encoding.EncodingMode.PROCESSING;
        player.containerMenu = menu;
        var screen = new NativeScreen(menu, player.getInventory());
        var wrapper = new RenderScreen(screen);
        Minecraft.getInstance().setScreen(wrapper);
        // The title-page fixture has no server connection. Seed only GTLCore's unrelated
        // first-render precise-count handshake; the real AE render and cloud Mixin still run.
        var displayReady = appeng.client.gui.me.common.MEStorageScreen.class.getDeclaredField("gtlcore$displayReady");
        displayReady.setAccessible(true);
        displayReady.setBoolean(screen, true);
        return new Session(menu, screen, wrapper);
    }
    record Session(PatternEncodingTermMenu menu, NativeScreen screen, RenderScreen wrapper) {
        int frames() { return wrapper.frames; }
        void bound(boolean value) throws Exception { menu.getClass().getField("cloud$bound").setBoolean(menu, value); }
        void verifyRecovery(BiConsumer<Boolean,String> check) throws Exception {
            var field = PatternEncodingTermMenu.class.getDeclaredField("encodingLogic"); field.setAccessible(true);
            var logic = (PatternEncodingLogic)field.get(menu);
            var state = ((com.gtl.enhancedcore.common.cloud.transfer.CloudTerminalSource)logic).cloud$sourceState();
            var review = menu.getClass().getField("cloud$review");
            state.putBoolean("quarantined", true); review.set(menu, "");
            screen.layoutAt(((Screen)screen).width, ((Screen)screen).height);
            check.accept(((net.minecraft.client.gui.components.AbstractWidget)button()).active, "Server-cleared GuiSync unlocks upload despite stale quarantined wireless item NBT");
            review.set(menu, "source/25/operation/Cloud snapshot write failed");
            screen.layoutAt(((Screen)screen).width, ((Screen)screen).height);
            check.accept(((net.minecraft.client.gui.components.AbstractWidget)button()).active && button().getTooltipMessage().stream().anyMatch(c -> c.getString().contains("save") || c.getString().contains("保存")),
                    "Recoverable failed commit offers active retry with an explicit save tooltip");
            review.set(menu, "source/25/operation/generation_gap");
            screen.layoutAt(((Screen)screen).width, ((Screen)screen).height);
            check.accept(!((net.minecraft.client.gui.components.AbstractWidget)button()).active, "Unknown generation conflict remains disabled on the actual AE screen");
            review.set(menu, ""); state.remove("quarantined");
            screen.layoutAt(((Screen)screen).width, ((Screen)screen).height);
        }
        CloudLibraryUploadButton button() {
            if (wrapper.failure != null) throw new IllegalStateException("Actual AE terminal render failed", wrapper.failure);
            return screen.children().stream().filter(CloudLibraryUploadButton.class::isInstance).map(CloudLibraryUploadButton.class::cast).findFirst()
                    .orElseThrow(() -> new AssertionError("Real terminal Mixin did not add its upload button"));
        }
        void verify(boolean bound, BiConsumer<Boolean, String> check) throws Exception {
            net.minecraft.client.gui.components.AbstractWidget button = button();
            check.accept(button.visible == bound, "Actual AE encoding screen upload button is " + (bound ? "visible when bound" : "hidden without a library"));
            if (bound) {
                check.accept(((CloudTerminalMenu)menu).cloud$hasOriginal() && button.active, "Real encoded output makes the bound upload button active");
                var original = menu.getSlots(appeng.menu.SlotSemantics.ENCODED_PATTERN).get(0);
                check.accept(button.getX() + button.getWidth() == screen.getGuiLeft() + original.x - 4
                                && button.getY() == screen.getGuiTop() + original.y + 2,
                        "Rendered upload button aligns beside the actual encoded-original output slot");
                check.accept(button.getTooltip() == null,
                        "Cloud upload uses AE ITooltip only, with no second vanilla tooltip");
                var tooltip = button().getTooltipArea();
                check.accept(tooltip.getX() == button.getX() && tooltip.getY() == button.getY()
                                && tooltip.getWidth() == button.getWidth() && tooltip.getHeight() == button.getHeight(),
                        "Cloud upload tooltip and click rectangles match the native pixel raster");
                check.accept(button.isMouseOver(button.getX(), button.getY())
                                && button.isMouseOver(button.getX() + 11, button.getY() + 11)
                                && !button.isMouseOver(button.getX() - 1, button.getY())
                                && !button.isMouseOver(button.getX() + 12, button.getY()),
                        "Actual upload hover includes every painted pixel and excludes adjacent pixels");
                var nativeEncode = screen.encodeButton();
                check.accept(button.getWidth() == 12 && button.getHeight() == 12,
                        "Cloud upload hitbox matches Plus's compact 12px icon raster");
                check.accept(button.getX() + button.getWidth() <= nativeEncode.getX() - 4,
                        "Cloud upload button leaves a clear gap before the real native encoding button");
                check.accept(button.getX() + button.getWidth() <= nativeEncode.getX() - 3
                                || button.getX() >= nativeEncode.getX() + nativeEncode.getWidth() + 3
                                || button.getY() + button.getHeight() <= nativeEncode.getY() - 3
                                || button.getY() >= nativeEncode.getY() + nativeEncode.getHeight() + 3,
                        "Actual cloud and native encoding button rectangles do not overlap, including the enlarged border");
                check.accept(button.getX() >= 0 && button.getY() >= 0 && button.getX() + button.getWidth() <= ((Screen)screen).width
                        && button.getY() + button.getHeight() <= ((Screen)screen).height, "Rendered terminal upload button stays inside the actual screen");
                int inventoryTop = menu.slots.stream().filter(slot -> slot.container == menu.getPlayer().getInventory())
                        .mapToInt(slot -> slot.y).min().orElseThrow() + screen.screenRect().getY();
                check.accept(button.getY() + button.getHeight() < inventoryTop,
                        "Cloud upload button stays above the actual player inventory row");
                check.accept(menu.slots.stream().filter(net.minecraft.world.inventory.Slot::isActive).noneMatch(slot ->
                                overlaps(button, screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16)),
                        "Cloud upload does not cover any active recipe, original, blank-pattern or player slot");
                check.accept(screen.children().stream()
                                .filter(child -> child.getClass().getName().startsWith("com.extendedae_plus.mixin.ae2.client.gui.PatternEncodingTermScreenMixin$"))
                                .filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                                .noneMatch(other -> overlaps(button, other.getX(), other.getY(), other.getWidth(), other.getHeight())),
                        "Cloud upload keeps Plus's native provider-selection arrow unobstructed");
                check.accept(screen.children().stream()
                                .filter(child -> child.getClass().getName().equals("com.extendedae_plus.mixin.ae2.client.gui.PatternEncodingTermScreenMixin$1"))
                                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                                .allMatch(other -> other.getTooltip() == null
                                        && other.getMessage().equals(Component.translatable("extendedae_plus.button.choose_provider"))),
                        "Plus's provider selector also uses its existing translated text through AE ITooltip only");
                check.accept(screen.children().stream()
                                .filter(child -> child.getClass().getName().equals("com.extendedae_plus.mixin.ae2.client.gui.PatternEncodingTermScreenMixin$1"))
                                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast)
                                .allMatch(other -> other.getWidth() == button.getWidth() && other.getHeight() == button.getHeight()),
                        "Cloud upload and the rendered Plus provider selector share the same compact native pixel dimensions");
                verifyResponsiveLayout(check);
            }
            Class<?> wetScreen = Class.forName("de.mari_023.ae2wtlib.wet.WETScreen");
            Class<?> wetMenu = Class.forName("de.mari_023.ae2wtlib.wet.WETMenu");
            check.accept(PatternEncodingTermScreen.class.isAssignableFrom(wetScreen) && PatternEncodingTermMenu.class.isAssignableFrom(wetMenu)
                    && CloudTerminalMenu.class.isAssignableFrom(wetMenu), "Real wireless encoding screen and menu inherit the transformed AE upload entry");
        }
        private void verifyResponsiveLayout(BiConsumer<Boolean, String> check) {
            int oldWidth = ((Screen)screen).width, oldHeight = ((Screen)screen).height;
            var oldMode = menu.mode;
            try {
                // GUI scale changes the logical viewport; drive AE's real init and
                // mode visibility rather than testing a duplicate layout formula.
                for (int[] viewport : new int[][]{{427, 240}, {640, 360}, {960, 540}, {1280, 720}}) {
                    for (var mode : appeng.parts.encoding.EncodingMode.values()) {
                        menu.mode = mode;
                        screen.layoutAt(viewport[0], viewport[1]);
                        net.minecraft.client.gui.components.AbstractWidget upload = button();
                        var original = menu.getSlots(appeng.menu.SlotSemantics.ENCODED_PATTERN).get(0);
                        check.accept(upload.visible && upload.getX() + 16 == screen.getGuiLeft() + original.x
                                        && upload.getY() == screen.getGuiTop() + original.y + 2,
                                "Real AE layout anchors cloud upload at " + viewport[0] + "x" + viewport[1] + " / " + mode);
                        check.accept(menu.slots.stream().filter(net.minecraft.world.inventory.Slot::isActive).noneMatch(slot ->
                                        overlaps(upload, screen.getGuiLeft() + slot.x, screen.getGuiTop() + slot.y, 16, 16)),
                                "Real AE slots remain unobstructed at " + viewport[0] + "x" + viewport[1] + " / " + mode);
                    }
                }
            } finally {
                menu.mode = oldMode;
                screen.layoutAt(oldWidth, oldHeight);
            }
        }
    }
    private static boolean overlaps(net.minecraft.client.gui.components.AbstractWidget button,
                                    int x, int y, int width, int height) {
        return button.getX() < x + width && button.getX() + button.getWidth() > x
                && button.getY() < y + height && button.getY() + button.getHeight() > y;
    }
    private static final class Host extends BlockEntity implements appeng.helpers.IPatternTerminalMenuHost {
        final PatternEncodingLogic logic;
        final appeng.api.util.IConfigManager config = new appeng.util.ConfigManager((Runnable) () -> {});
        Host() {
            super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
            logic = new PatternEncodingLogic(new appeng.helpers.IPatternTerminalLogicHost() {
                @Override public PatternEncodingLogic getLogic() { return Host.this.logic; }
                @Override public net.minecraft.world.level.Level getLevel() { return Host.this.getLevel(); }
                @Override public void markForSave() {}
            });
        }
        @Override public PatternEncodingLogic getLogic() { return logic; }
        @Override public appeng.api.util.IConfigManager getConfigManager() { return config; }
        @Override public appeng.api.storage.MEStorage getInventory() { return () -> Component.literal("Synthetic terminal storage"); }
        @Override public void returnToMainMenu(Player player, appeng.menu.ISubMenu menu) {}
        @Override public ItemStack getMainMenuIcon() { return appeng.core.definitions.AEItems.PROCESSING_PATTERN.stack(); }
    }
    private static final class NativeScreen extends PatternEncodingTermScreen<PatternEncodingTermMenu> {
        NativeScreen(PatternEncodingTermMenu menu, net.minecraft.world.entity.player.Inventory inventory) {
            super(menu, inventory, Component.translatable("gui.ae2.PatternEncoding"), StyleManager.loadStyleDoc("/screens/terminals/pattern_encoding_terminal.json"));
        }
        void initialize(Minecraft mc) {
            minecraft = mc; font = mc.font; width = mc.getWindow().getGuiScaledWidth(); height = mc.getWindow().getGuiScaledHeight(); super.init();
        }
        void layoutAt(int width, int height) {
            // This title-host fixture deliberately has no LocalPlayer. Screen's
            // public init(mc,w,h) also posts Forge Init events; InvTweaks assumes
            // an in-world player for this container screen and dereferences null.
            // Re-run the same protected AE lifecycle used by initialize(), with
            // vanilla's widget reset, preserving all AE/Mixin layout execution.
            Screen viewport = this; viewport.width = width; viewport.height = height;
            clearWidgets();
            super.init();
            updateBeforeRender();
        }
        Rect2i screenRect() { return new Rect2i(getGuiLeft(), getGuiTop(), imageWidth, imageHeight); }
        net.minecraft.client.gui.components.AbstractWidget encodeButton() throws Exception {
            var field = appeng.client.gui.WidgetContainer.class.getDeclaredField("widgets");
            field.setAccessible(true);
            return (net.minecraft.client.gui.components.AbstractWidget) ((java.util.Map<?, ?>)field.get(widgets)).get("encodePattern");
        }
    }
    static final class RenderScreen extends Screen {
        private final NativeScreen screen;
        private int frames;
        private Throwable failure;
        RenderScreen(NativeScreen screen) { super(Component.literal("Native AE encoding screen audit")); this.screen = screen; }
        @Override protected void init() { screen.initialize(minecraft); }
        @Override public void tick() {}
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            try { screen.render(graphics, mouseX, mouseY, partialTick); }
            catch (Throwable error) { failure = error; }
            frames++;
        }
    }
}
