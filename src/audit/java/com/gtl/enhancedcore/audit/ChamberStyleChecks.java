package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.gui.PagedInventoryWidget;
import com.gtl.enhancedcore.common.gui.SuperBufferNameWidget;
import com.gtl.enhancedcore.common.gui.BufferViewportWidget;
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
                scene("chamber-quantum-last", "gtl_enhancedcore:quantum_data_access_hatch", 17),
                scene("chamber-me-drive", "gtl_enhancedcore:me_drive", 0),
                scene("chamber-circuit", "gtl_enhancedcore:circuit_encoder_hatch", 0),
                scene("chamber-super-buffer", "gtladditions:me_super_pattern_buffer", 0),
                scene("chamber-super-buffer-edit", "gtladditions:me_super_pattern_buffer", -1),
                scene("chamber-super-buffer-scroll", "gtladditions:me_super_pattern_buffer", -2),
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
        void beginQuickCraftProbe() {
            quickCraftSlots.clear();
            getMenu().setCarried(new ItemStack(net.minecraft.world.item.Items.STONE,64));
            isQuickCrafting=true;
        }
        void endQuickCraftProbe() {
            isQuickCrafting=false;
            quickCraftSlots.clear();
            getMenu().setCarried(ItemStack.EMPTY);
        }
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
            if (requestedPage == -2) {
                var viewport=ui.mainGroup.getWidgetsByType(BufferViewportWidget.class).getFirst();
                var p=viewport.getPosition();
                for (int step=0;step<30;step++) ui.mainGroup.mouseWheelMove(p.x+30,p.y+30,-1);
                require(viewport.getScrollYOffset()>0,id+" native tree scrolls vertically through root wheel");
                int bx=p.x+20, by=p.y+viewport.getSizeHeight()-3;
                require(ui.mainGroup.mouseClicked(bx,by,0),id+" horizontal bar receives root press");
                ui.mainGroup.mouseDragged(bx+200,by,0,200,0);
                ui.mainGroup.mouseReleased(bx+200,by,0);
                require(viewport.getScrollXOffset()>0,id+" horizontal bar drags through root input");
                var next=viewport.getWidgetsByType(ButtonWidget.class).stream()
                        .max(Comparator.comparingInt(Widget::getSelfPositionX)).orElseThrow();
                var n=next.getPosition();
                require(n.x>=p.x && n.y>=p.y && n.x+next.getSizeWidth()<=p.x+viewport.getSizeWidth()-6
                        && n.y+next.getSizeHeight()<=p.y+viewport.getSizeHeight()-6,
                        id+" native next-page button is fully reachable after scrolling: button="+n
                                +" size="+next.getSize()+" viewport="+p+" offset="+viewport.getScrollXOffset()+","+viewport.getScrollYOffset()
                                +" maximum="+viewport.maximumX()+","+viewport.maximumY());
                int first=viewport.getWidgetsByType(SlotWidget.class).getFirst().getHandler().getSlotIndex();
                require(ui.mainGroup.mouseClicked(n.x+9,n.y+6,0),id+" native next-page action receives root click");
                ui.mainGroup.mouseReleased(n.x+9,n.y+6,0);
                require(viewport.getWidgetsByType(SlotWidget.class).getFirst().getHandler().getSlotIndex()>first,
                        id+" original pagination advances real inventory slot indices");
            }
        }
        @Override public void tick() { gui.hatchTick(); }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) { gui.renderWithTooltip(graphics,-100,-100,partial); }
        @Override public void removed() { holders.forEach(holder -> holder.setLevel(null)); }
        void verify() throws Exception {
            require(ui.getWidth() <= width && ui.getHeight() <= height, id+" actual Fancy shell fits viewport: "+ui.getWidth()+"x"+ui.getHeight());
            visibleScreenBounds(ui.mainGroup);
            var pages=ui.mainGroup.getWidgetsByType(PagedInventoryWidget.class);
            if (!pages.isEmpty()) {
                var panel=pages.getFirst();
                var slots=panel.getWidgetsByType(SlotWidget.class);
                require(slots.size()==45,id+" displays exactly five rows of nine slots");
                require(slots.getFirst().getHandler().getSlotIndex()==requestedPage*45,id+" page maps to exact original inventory index");
                require(slots.getLast().getHandler().getSlotIndex()==requestedPage*45+44,id+" last displayed slot retains original index");
                bounds(panel);
            } else if (id.endsWith("circuit_encoder_hatch")) {
                var buttons=ui.mainGroup.getWidgetsByType(ButtonWidget.class).stream()
                        .filter(button -> button.getSelfPositionY()==104).toList();
                require(buttons.size()==2,id+" both bulk actions occupy a separate bottom row");
            } else {
                var viewport=ui.mainGroup.getWidgetsByType(BufferViewportWidget.class).getFirst();
                require(viewport.getSizeWidth()==208 && viewport.getSizeHeight()==86,id+" fixed common-side content viewport");
                require(ui.getWidth()<=310 && ui.getHeight()<=240,id+" complete Fancy shell fits 320x240 logical minimum");
                var manager=viewport.getWidgetsByType(org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager.class);
                require(manager.size()==1 && !manager.getFirst().isVisible(),id+" native catalyst panel is attached and initially hidden");
                final int[] hiddenActions={0,0,0};
                final String[] hiddenDispatch={""};
                var probe=new ButtonWidget(8,viewport.getSizeHeight()+20,18,18,
                        com.lowdragmc.lowdraglib.gui.texture.IGuiTexture.EMPTY, click->hiddenActions[0]++) {
                    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
                        if (!isMouseOverElement(x,y)) return false;
                        hiddenDispatch[0]=Arrays.toString(Thread.currentThread().getStackTrace());
                        hiddenActions[1]++; return true;
                    }
                    @Override public boolean mouseReleased(double x,double y,int button) {
                        if (!isMouseOverElement(x,y)) return false;
                        hiddenDispatch[0]=Arrays.toString(Thread.currentThread().getStackTrace());
                        hiddenActions[2]++; return true;
                    }
                };
                probe.setClientSideWidget(); viewport.addWidget(probe); probe.setVisible(true);
                var hidden=probe.getPosition();
                require(!viewport.isMouseOverElement(hidden.x+9,hidden.y+9),
                        id+" hidden probe lies outside viewport: probe="+hidden+" viewport="+viewport.getPosition()
                                +" offset="+viewport.getScrollYOffset());
                ui.mainGroup.mouseClicked(hidden.x+9,hidden.y+9,0);
                ui.mainGroup.mouseDragged(hidden.x+9,hidden.y+9,0,2,2);
                ui.mainGroup.mouseReleased(hidden.x+9,hidden.y+9,0);
                require(hiddenActions[0]==0 && hiddenActions[1]==0 && hiddenActions[2]==0,
                        id+" root dispatch rejects clipped click/drag/release coordinates: "+Arrays.toString(hiddenActions)
                                +" probe="+hidden+" viewport="+viewport.getPosition()+" offset="+viewport.getScrollYOffset()
                                +" dispatch="+hiddenDispatch[0]);
                require(viewport.getHoverElement(hidden.x+9,hidden.y+9)==null,
                        id+" clipped native subtree cannot become a hover ingredient");
                viewport.removeWidget(probe);
                var vp=viewport.getPosition();
                int savedX=viewport.getScrollXOffset(), savedY=viewport.getScrollYOffset();
                viewport.setScrollXOffset(0);
                ui.mainGroup.mouseClicked(vp.x+20,vp.y+viewport.getSizeHeight()-3,0);
                ui.mainGroup.mouseDragged(vp.x+viewport.getSizeWidth()+20,vp.y+viewport.getSizeHeight()+20,0,12,0);
                require(viewport.maximumX()==0 || viewport.getScrollXOffset()>0,
                        id+" scrollbar drag continues outside viewport");
                ui.mainGroup.mouseReleased(vp.x+viewport.getSizeWidth()+20,vp.y+viewport.getSizeHeight()+20,0);
                int releasedX=viewport.getScrollXOffset();
                viewport.mouseDragged(vp.x+viewport.getSizeWidth()+20,vp.y+viewport.getSizeHeight()+20,0,12,0);
                require(viewport.getScrollXOffset()==releasedX,id+" outside release ends scrollbar drag");
                viewport.setScrollXOffset(savedX); viewport.setScrollYOffset(savedY);
                verifyQuickCraft(viewport);
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
        private void verifyQuickCraft(BufferViewportWidget viewport) throws Exception {
            int savedX=viewport.getScrollXOffset(), savedY=viewport.getScrollYOffset();
            viewport.setScrollXOffset(0); viewport.setScrollYOffset(0);
            var nativeSlots=viewport.getWidgetsByType(SlotWidget.class);
            SlotWidget playerTarget=null, clipped=null;
            for (var candidate:ui.mainGroup.getWidgetsByType(SlotWidget.class)) {
                if (!candidate.isPlayerContainer) continue;
                var p=candidate.getPosition();
                for (var nativeSlot:nativeSlots) {
                    if (nativeSlot.isMouseOverElement(p.x+9,p.y+9)) {
                        playerTarget=candidate; clipped=nativeSlot; break;
                    }
                }
                if (playerTarget!=null) break;
            }
            require(playerTarget!=null,id+" real clipped native slots overlap player inventory geometry");
            var target=playerTarget.getPosition();
            var hoverField=SlotWidget.class.getDeclaredField("HOVER_SLOT");
            hoverField.setAccessible(true);
            var priorHover=hoverField.get(null);
            gui.beginQuickCraftProbe();
            try {
                require(ui.mainGroup.mouseDragged(target.x+9,target.y+9,0,1,1),
                        id+" player inventory receives root quickcraft drag");
                require(gui.getQuickCraftSlots().size()==1 && gui.getQuickCraftSlots().contains(playerTarget.getHandler()),
                        id+" native quickcraft selects only the visible player slot");
                require(!gui.getQuickCraftSlots().contains(clipped.getHandler()),
                        id+" native quickcraft excludes the overlapping clipped buffer slot");
                require(hoverField.get(null)==priorHover,id+" temporary hover slot scope is restored after native drag");
            } finally {
                gui.endQuickCraftProbe();
                viewport.setScrollXOffset(savedX); viewport.setScrollYOffset(savedY);
            }
        }
        private List<String> tree(Widget widget) {
            var result=new ArrayList<String>(); result.add(widget.getClass().getName());
            if (widget instanceof WidgetGroup group) for (var child:group.widgets) result.addAll(tree(child));
            return result;
        }
        private void visibleScreenBounds(Widget widget) {
            if (!widget.isVisible()) return;
            var p=widget.getPosition();
            if (!(widget instanceof com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel))
                require(p.x>=0 && p.y>=0 && p.x+widget.getSizeWidth()<=width && p.y+widget.getSizeHeight()<=height,
                    id+" visible Fancy control fits screen: "+widget.getClass().getSimpleName()+" "+p+" "+widget.getSize()
                            +" visibleTree="+visibleTree(ui.mainGroup));
            if (widget instanceof WidgetGroup group && !(widget instanceof BufferViewportWidget))
                for (var child:group.widgets) visibleScreenBounds(child);
        }
        private List<String> visibleTree(Widget widget) {
            var list=new ArrayList<String>();
            if (!widget.isVisible()) return list;
            list.add(widget.getClass().getSimpleName()+" "+widget.getPosition()+" "+widget.getSize());
            if (widget instanceof WidgetGroup group && !(widget instanceof BufferViewportWidget))
                for (var child:group.widgets) list.addAll(visibleTree(child));
            return list;
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
