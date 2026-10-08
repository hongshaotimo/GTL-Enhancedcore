package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

/** Each open panel owns its own paging state; counts are supplied and actions executed on the server. */
public final class IvBufferConfigurator implements IFancyConfigurator {
    private final MEPatternBufferPartMachineBase buffer;
    public IvBufferConfigurator(MEPatternBufferPartMachineBase buffer){this.buffer=buffer;}
    @Override public Component getTitle(){return Component.translatable("gtl_enhancedcore.gui.iv_buffer_title");}
    @Override public IGuiTexture getIcon(){return GuiTextures.BUTTON_POWER;}
    @Override public List<Component> getTooltips(){return List.of(getTitle(),Component.translatable("gtl_enhancedcore.gui.iv_cancel_help"));}
    @Override public Widget createConfigurator(){
        var group=new WidgetGroup(0,0,300,240);int[] page={0};
        var scroll=new DraggableScrollableWidgetGroup(0,0,300,198).setYScrollBarWidth(4).setUseScissor(true).setDraggable(false);
        group.addWidget(scroll);
        scroll.addWidget(new ComponentPanelWidget(6,5,lines->{
            var state=IvBuffers.state(buffer);if(state==null)return;
            lines.add(getTitle().copy().withStyle(ChatFormatting.AQUA,ChatFormatting.BOLD));
            lines.add(Component.translatable("gtl_enhancedcore.gui.iv_break_help").withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("gtl_enhancedcore.gui.iv_shared_catalysts").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(state.accepting?"gtl_enhancedcore.gui.iv_accepting":"gtl_enhancedcore.gui.iv_not_accepting").withStyle(state.accepting?ChatFormatting.GREEN:ChatFormatting.YELLOW));
            lines.add(Component.translatable("gtl_enhancedcore.gui.iv_buffer_count",state.jobs.size()).withStyle(ChatFormatting.GRAY));
            if(!state.message.isEmpty())lines.add(Component.translatable(state.message).withStyle(ChatFormatting.YELLOW));
            if(state.jobs.isEmpty())return;
            page[0]=Math.min(page[0],state.jobs.size()-1);var job=state.jobs.get(page[0]);
            lines.add(Component.translatable("gtl_enhancedcore.gui.iv_buffer_page",page[0]+1,state.jobs.size(),job.slot+1).withStyle(ChatFormatting.WHITE));
            lines.add(Component.translatable(job.recipe.recipeType.registryName.toString().replace(':','.')).withStyle(ChatFormatting.AQUA));
            if(!job.error.isEmpty())lines.add(Component.translatable(job.error).withStyle(job.halted?ChatFormatting.RED:ChatFormatting.YELLOW));
            lines.add(Component.translatable("gtl_enhancedcore.gui.iv_remaining",IvPresentation.number(job.remaining),IvPresentation.number(job.parallel)).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.translatable("gtl_enhancedcore.gui.iv_cancelled_count",IvPresentation.number(job.cancelledOperations)).withStyle(ChatFormatting.GRAY));
            stock(lines,"gtl_enhancedcore.gui.iv_stock_input",job.inventory);stock(lines,"gtl_enhancedcore.gui.iv_stock_refunds",job.refunds);stock(lines,"gtl_enhancedcore.gui.iv_stock_pending",job.pending);
            lines.add(Component.translatable("gtl_enhancedcore.gui.iv_cancel_help").withStyle(ChatFormatting.DARK_GRAY));
        }).setMaxWidthLimit(282).setSpace(1));
        group.addWidget(new ButtonWidget(6,204,42,16,GuiTextures.BUTTON_LEFT,click->{if(!click.isRemote)page[0]=Math.max(0,page[0]-1);}));
        group.addWidget(new ButtonWidget(52,204,42,16,GuiTextures.BUTTON_RIGHT,click->{if(!click.isRemote)page[0]++;}));
        group.addWidget(new IvActionButton(100,204,92,16,()->!IvBuffers.state(buffer).jobs.isEmpty(),()->false,
                "gtl_enhancedcore.gui.iv_cancel_slot","gtl_enhancedcore.gui.iv_cancel_slot","gtl_enhancedcore.gui.iv_cancel_help",false,()->{
            var state=IvBuffers.state(buffer);
            if(!state.jobs.isEmpty())IvCancellation.cancel(buffer,state.jobs.get(Math.min(page[0],state.jobs.size()-1)).slot);
        }));
        group.addWidget(new IvActionButton(196,204,98,16,()->!IvBuffers.state(buffer).jobs.isEmpty(),()->false,
                "gtl_enhancedcore.gui.iv_cancel_all","gtl_enhancedcore.gui.iv_cancel_all","gtl_enhancedcore.gui.iv_cancel_help",false,()->IvCancellation.cancel(buffer,-1)));
        group.addWidget(new IvActionButton(100,224,194,14,()->IvBuffers.isolated(buffer),()->IvBuffers.state(buffer).accepting,
                "gtl_enhancedcore.gui.iv_admission_on","gtl_enhancedcore.gui.iv_admission_off","gtl_enhancedcore.gui.iv_admission_help",true,
                ()->IvCancellation.setAccepting(buffer,!IvBuffers.state(buffer).accepting)));
        return group;
    }
    private static void stock(List<Component> text,String title,Map<appeng.api.stacks.AEKey,Long> values){
        text.add(Component.translatable(title).withStyle(ChatFormatting.GRAY));
        if(values.isEmpty())text.add(Component.translatable("gtl_enhancedcore.gui.iv_stock_empty").withStyle(ChatFormatting.DARK_GRAY));
        for(var entry:values.entrySet()){
            text.add(Component.literal("  "+IvPresentation.number(entry.getValue())+" × ").append(entry.getKey().getDisplayName()).withStyle(ChatFormatting.WHITE));
        }
    }
}
