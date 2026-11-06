package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.gui.PagedInventoryWidget;
import com.gtl.enhancedcore.common.gui.SuperBufferNameWidget;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import com.lowdragmc.lowdraglib.gui.modular.WidgetUIAccess;
import com.lowdragmc.lowdraglib.gui.widget.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Real registered hatch UI trees in disposable dummy levels; never loads a player save. */
public final class ChamberStyleChecks {
    private ChamberStyleChecks() {}
    public static List<GuiStyleClientChecks.Scene> scenes() {
        return List.of(
                scene("chamber-quantum-first", "gtl_enhancedcore:quantum_data_access_hatch", 0),
                scene("chamber-quantum-last", "gtl_enhancedcore:quantum_data_access_hatch", 9),
                scene("chamber-me-drive", "gtl_enhancedcore:me_drive", 0),
                scene("chamber-circuit", "gtl_enhancedcore:circuit_encoder_hatch", 0),
                scene("chamber-super-buffer", "gtladditions:me_super_pattern_buffer", 0),
                scene("chamber-super-buffer-edit", "gtladditions:me_super_pattern_buffer", -1),
                scene("chamber-ordinary-buffer", "gtceu:me_final_pattern_buffer", 0));
    }
    private static GuiStyleClientChecks.Scene scene(String name, String id, int page) {
        return new GuiStyleClientChecks.Scene(name, () -> open(id, page), screen -> ((HatchScreen)screen).verify());
    }
    private static Screen open(String id, int page) throws Exception {
        return build(id, page, true);
    }
    private static HatchScreen build(String id, int page, boolean client) throws Exception {
        var base = StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line", client, false, false);
        var holders = new ArrayList<>(base.holders());
        var definition = Objects.requireNonNull(GTRegistries.MACHINES.get(new ResourceLocation(id)), id);
        var position = new BlockPos(70, 80, 0);
        var holder = (MetaMachineBlockEntity)definition.getBlockEntityType().create(position, definition.defaultBlockState());
        holders.add(holder); holder.setLevel(base.player().level());
        if (holder.getMetaMachine() instanceof org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine buffer)
            buffer.setCustomName("Long assembly buffer name 100% / \u957f\u540d\u79f0\u6837\u677f\u603b\u6210");
        if (holder.getMetaMachine() instanceof com.gtl.enhancedcore.common.machine.hatch.CircuitEncoderHatchMachine circuit) {
            circuit.toggleCircuit(0); circuit.toggleCircuit(3); circuit.toggleCircuit(19); circuit.toggleCircuit(32);
        }
        var ui = ((IUIMachine)holder.getMetaMachine()).createUI(base.player());
        ui.initWidgets();
        base.player().containerMenu = ui.getModularUIContainer();
        return new HatchScreen(ui, holders, id, page);
    }
    private static void require(boolean value, String message) { GuiStyleClientChecks.require(value, message); }
    private static final class HatchGui extends ModularUIGuiContainer {
        HatchGui(ModularUI ui) { super(ui, 1); }
        void initialize(Minecraft mc) { minecraft=mc; font=mc.font; width=mc.getWindow().getGuiScaledWidth(); height=mc.getWindow().getGuiScaledHeight(); super.init(); }
        void hatchTick() { super.containerTick(); }
    }
    private static final class HatchScreen extends Screen {
        final ModularUI ui;
        final HatchGui gui;
        final List<MetaMachineBlockEntity> holders;
        final String id;
        final int requestedPage;
        HatchScreen(ModularUI ui, List<MetaMachineBlockEntity> holders, String id, int requestedPage) {
            super(Component.literal("Hatch style audit")); this.ui=ui; gui=new HatchGui(ui);
            this.holders=holders; this.id=id; this.requestedPage=requestedPage;
        }
        @Override protected void init() {
            gui.initialize(minecraft);
            ui.mainGroup.setUiAccess(new WidgetUIAccess() {
                public boolean attemptMergeStack(ItemStack stack, boolean a, boolean b) { return false; }
                public void writeUpdateInfo(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
                public void writeClientAction(Widget source, int action, Consumer<FriendlyByteBuf> writer) {}
            });
            var pages=ui.mainGroup.getWidgetsByType(PagedInventoryWidget.class);
            if (!pages.isEmpty()) {
                var panel=pages.getFirst();
                for (int step=0; step<requestedPage; step++) {
                    var next=panel.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                            .max(Comparator.comparingInt(Widget::getSelfPositionX)).orElseThrow();
                    var p=next.getPosition();
                    require(ui.mainGroup.mouseClicked(p.x+9,p.y+9,0), id+" pagination receives root click "+step);
                    ui.mainGroup.mouseReleased(p.x+9,p.y+9,0);
                }
            }
            if (requestedPage == -1) {
                var name=ui.mainGroup.getWidgetsByType(SuperBufferNameWidget.class).getFirst();
                var edit=name.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                        .max(Comparator.comparingInt(Widget::getSelfPositionX)).orElseThrow();
                var p=edit.getPosition();
                require(ui.mainGroup.mouseClicked(p.x+9,p.y+9,0),id+" rename receives a real root click");
                ui.mainGroup.mouseReleased(p.x+9,p.y+9,0);
            }
        }
        @Override public void tick() { gui.hatchTick(); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) { gui.renderWithTooltip(graphics,-100,-100,partial); }
        @Override public void removed() { holders.forEach(holder -> holder.setLevel(null)); }
        void verify() throws Exception {
            require(ui.getWidth() > 0 && ui.getHeight() > 0, id+" actual native Fancy shell has positive dimensions");
            require(!ui.mainGroup.getWidgetsByType(SlotWidget.class).isEmpty(), id+" retains native real inventory slots");
            var pages=ui.mainGroup.getWidgetsByType(PagedInventoryWidget.class);
            if (!pages.isEmpty()) {
                var panel=pages.getFirst();
                var slots=panel.getWidgetsByType(SlotWidget.class);
                int pageSize = id.endsWith("quantum_data_access_hatch") ? 81 : 63;
                require(slots.size()==pageSize,id+" preserves the native page capacity");
                require(slots.getFirst().getHandler().getSlotIndex()==requestedPage*pageSize,id+" page maps to exact original inventory index");
                require(slots.getLast().getHandler().getSlotIndex()==requestedPage*pageSize+pageSize-1,id+" last displayed slot retains original index");
                bounds(panel);
            } else if (id.endsWith("circuit_encoder_hatch")) {
                var buttons=ui.mainGroup.getWidgetsByType(ButtonWidget.class).stream()
                        .filter(button -> button.getSelfPositionY()==104).toList();
                require(buttons.size()==2,id+" both bulk actions occupy a separate bottom row");
            } else {
                if (requestedPage==0) {
                    var server=build(id,0,false);
                    try {
                        require(tree(ui.mainGroup).equals(tree(server.ui.mainGroup)),id+" server/client initial widget topology matches");
                        var wire=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
                        try {
                            server.ui.mainGroup.writeInitialData(wire); ui.mainGroup.readInitialData(wire);
                            require(wire.readableBytes()==0,id+" initial GUI synchronization consumes all bytes");
                        } finally { wire.release(); }
                    } finally { server.holders.forEach(holder->holder.setLevel(null)); }
                }
                var names=ui.mainGroup.getWidgetsByType(SuperBufferNameWidget.class);
                if (id.equals("gtceu:me_final_pattern_buffer")) {
                    require(names.isEmpty(),id+" ordinary buffer keeps original rename action widget");
                    return;
                }
                require(names.size()==1,id+" contains exactly one shared name row");
                require(names.getFirst().getSizeHeight()==18,id+" name row uses full 18px controls");
                if (requestedPage == -1) require(names.getFirst().getWidgetsByType(TextFieldWidget.class).getFirst().isVisible(),
                        id+" rename editor opens through root input");
                bounds(names.getFirst());
            }
        }
        private List<String> tree(Widget widget) {
            var result=new ArrayList<String>(); result.add(widget.getClass().getName());
            if (widget instanceof WidgetGroup group) for (var child:group.widgets) result.addAll(tree(child));
            return result;
        }
        private void bounds(WidgetGroup group) {
            for (var child:group.widgets) {
                var p=child.getSelfPosition();
                require(p.x>=0 && p.y>=0 && p.x+child.getSizeWidth()<=group.getSizeWidth()
                        && p.y+child.getSizeHeight()<=group.getSizeHeight(), id+" child remains inside panel: "+child.getClass().getSimpleName());
                if (child instanceof WidgetGroup nested) bounds(nested);
            }
        }
    }
}
