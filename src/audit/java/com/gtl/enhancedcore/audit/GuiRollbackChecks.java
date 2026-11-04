package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Fresh real registered menus after the GUI rollback; records upstream sizes without the withdrawn fit contract. */
public final class GuiRollbackChecks {
    private GuiRollbackChecks() {}

    public static List<GuiStyleClientChecks.Scene> scenes() {
        var scenes = new ArrayList<GuiStyleClientChecks.Scene>();
        for (var target : List.of(
                new String[]{"ordinary-buffer", "gtceu:me_final_pattern_buffer"},
                new String[]{"super-buffer", "gtladditions:me_super_pattern_buffer"},
                new String[]{"quantum-hatch", "gtl_enhancedcore:quantum_data_access_hatch"},
                new String[]{"me-drive", "gtl_enhancedcore:me_drive"},
                new String[]{"circuit-hatch", "gtl_enhancedcore:circuit_encoder_hatch"})) {
            scenes.add(new GuiStyleClientChecks.Scene("restored-" + target[0], () -> open(target[1]),
                    screen -> ((MenuScreen) screen).verify()));
        }
        scenes.add(new GuiStyleClientChecks.Scene("restored-config-home", () -> GuiStyleClientChecks.construct(
                "EnhancedConfigScreen", new Class<?>[]{Screen.class}, (Object) null)));
        scenes.add(new GuiStyleClientChecks.Scene("restored-config-files", () -> GuiStyleClientChecks.construct(
                "ConfigFilesScreen", new Class<?>[]{Screen.class}, (Object) null)));
        return scenes;
    }

    private static MenuScreen open(String id) throws Exception {
        var base = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", true, false, false);
        var holders = new ArrayList<>(base.holders());
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(id));
        GuiStyleClientChecks.require(definition != null, "Restored menu uses registered machine " + id);
        var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(new BlockPos(70, 80, 0), definition.defaultBlockState());
        holders.add(holder);
        holder.setLevel(base.player().level());
        var machine = holder.getMetaMachine();
        if (machine instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine buffer) {
            buffer.setCustomName("Restored pattern assembly / 原版样板总成");
        }
        var ui = ((IUIMachine) machine).createUI(base.player());
        ui.initWidgets();
        base.player().containerMenu = ui.getModularUIContainer();
        return new MenuScreen(ui, holders, id);
    }

    private static final class MenuGui extends ModularUIGuiContainer {
        MenuGui(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft minecraft) {
            this.minecraft = minecraft; font = minecraft.font;
            width = minecraft.getWindow().getGuiScaledWidth(); height = minecraft.getWindow().getGuiScaledHeight();
            super.init();
        }
        void menuTick() { super.containerTick(); }
    }

    private static final class MenuScreen extends Screen {
        final ModularUI ui;
        final MenuGui gui;
        final List<MetaMachineBlockEntity> holders;
        final String id;
        MenuScreen(ModularUI ui, List<MetaMachineBlockEntity> holders, String id) {
            super(Component.literal(id)); this.ui = ui; gui = new MenuGui(ui); this.holders = holders; this.id = id;
        }
        @Override protected void init() {
            gui.initialize(minecraft);
            ui.mainGroup.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
                public void writeUpdateInfo(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
            });
        }
        @Override public void tick() { gui.menuTick(); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            gui.renderWithTooltip(graphics, -100, -100, partial);
        }
        @Override public void removed() { holders.forEach(holder -> holder.setLevel(null)); }
        void verify() {
            var all = new ArrayList<Widget>();
            collect(ui.mainGroup, all);
            var withdrawn = all.stream().filter(widget -> List.of("BufferViewportWidget")
                    .contains(widget.getClass().getSimpleName())).map(widget -> widget.getClass().getSimpleName()).toList();
            GuiStyleClientChecks.require(withdrawn.isEmpty(), id + " has no withdrawn unified GUI components: " + withdrawn);
            GuiStyleClientChecks.require(!ui.mainGroup.getWidgetsByType(SlotWidget.class).isEmpty(), id + " retains native real inventory slots");
            GuiStyleClientChecks.require(ui.getWidth() > 0 && ui.getHeight() > 0,
                    id + " restored shell " + ui.getWidth() + "x" + ui.getHeight() + " at logical " + width + "x" + height
                            + "; native slot count=" + ui.mainGroup.getWidgetsByType(SlotWidget.class).size());
        }
    }

    private static void collect(Widget widget, List<Widget> list) {
        list.add(widget);
        if (widget instanceof WidgetGroup group) for (var child : group.widgets) collect(child, list);
    }
}
