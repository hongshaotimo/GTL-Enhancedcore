package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import com.lowdragmc.lowdraglib.gui.widget.*;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Called from an already guarded title-screen audit. No world load, AE node creation or live packet send. */
public final class CloudIntegrationReviewChecks {
    private CloudIntegrationReviewChecks() {}
    public static Map<String,Object> run(BiConsumer<Boolean,String> check)throws Exception{
        var result=new LinkedHashMap<String,Object>();
        var fixture=StellarIvGuiChecks.fixture("gtceu:suprachronal_assembly_line",true,false,false);
        try{
            var originalField=MachineDefinition.class.getDeclaredField("tooltipBuilder");originalField.setAccessible(true);
            for(String id:List.of("gtceu:assembly_line","gtceu:dimensional_focus_engraving_array",
                    "gtceu:star_ultimate_material_forge_factory","gtceu:qft")){
                var definition=GTRegistries.MACHINES.get(new ResourceLocation(id));
                check.accept(definition!=null,"Real tooltip definition registered: "+id);
                @SuppressWarnings("unchecked") var raw=(BiConsumer<ItemStack,List<Component>>)originalField.get(definition);
                var before=new ArrayList<Component>();var after=new ArrayList<Component>();
                ItemStack stack=definition.asStack();if(raw!=null)raw.accept(stack,before);
                definition.getTooltipBuilder().accept(stack,after);
                check.accept(!after.isEmpty(),"Transformed tooltip chain produces rows: "+id);
                // Catalog format may be replaced. Every unrelated raw sentence must survive exactly.
                for(var row:before){
                    boolean catalog=row.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tr
                            &&tr.getKey().startsWith("gtceu.machine.available_recipe_map_");
                    boolean obsolete=MachineTooltips.containsKey(row,"tooltip.gtl_enhancedcore.star_ultimate_material_forge.modified");
                    if(!catalog&&!obsolete)check.accept(after.stream().anyMatch(line->line.equals(row)||line.getString().equals(row.getString())),
                            "Original third-party sentence survives wrapper chain: "+id+" / "+row.getString());
                }
                if(id.endsWith("star_ultimate_material_forge_factory")){
                    check.accept(after.stream().anyMatch(row->MachineTooltips.containsKey(row,"tooltip.gtl_enhancedcore.add_threads.threads")),
                            "ADD-thread wrapper survives alongside forge parallel wrapper");
                    check.accept(after.stream().anyMatch(row->MachineTooltips.containsKey(row,"tooltip.gtl_enhancedcore.star_ultimate_material_forge.parallel")),
                            "Forge parallel wrapper survives alongside ADD-thread wrapper");
                }
                result.put(id,after.stream().map(Component::getString).toList());
            }
            var target=Class.forName("org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime")
                    .getDeclaredMethod("isWirelessMeTargetObject",Object.class,ResourceLocation.class);target.setAccessible(true);
            for(String path:List.of("cloud_pattern_library","cloud_pattern_buffer")){
                var id=new ResourceLocation("gtl_enhancedcore",path);var definition=GTRegistries.MACHINES.get(id);
                var holder=(MetaMachineBlockEntity)definition.getBlockEntityType().create(new BlockPos(90,80,0),definition.defaultBlockState());
                holder.setLevel(fixture.player().level());
                try{
                    var machine=holder.getMetaMachine();
                    check.accept(machine instanceof IGridConnectedMachine,"Cloud machine implements real native grid contract: "+path);
                    var connected=(IGridConnectedMachine)machine;
                    check.accept(connected.getMainNode()!=null,"Own managed node initialized without requiring a preexisting network: "+path);
                    check.accept(machine.getTraits().stream().filter(GridNodeHolder.class::isInstance).count()==1,
                            "Exactly one registered GridNodeHolder lifecycle trait: "+path);
                    check.accept(Boolean.TRUE.equals(target.invoke(null,machine,id)),"Actual transformed wireless target gate accepts cloud instance: "+path);
                    var ui=((com.gregtechceu.gtceu.api.machine.feature.IUIMachine)machine).createUI(fixture.player());ui.initWidgets();
                    var nodes=new ArrayList<Widget>();visit(ui.mainGroup,nodes);
                    check.accept(nodes.stream().anyMatch(FancyMachineUIWidget.class::isInstance),"Native Fancy tree wraps cloud main page: "+path);
                    check.accept(nodes.stream().noneMatch(widget->widget.getClass().getSimpleName().contains("ReportPage")),
                            "Cloud tree contains no separate report page: "+path);
                    result.put(path+"Tree",nodes.stream().map(widget->widget.getClass().getName()).toList());
                }finally{holder.setLevel(null);}
            }
            return result;
        }finally{for(var holder:fixture.holders())holder.setLevel(null);}
    }
    private static void visit(Widget widget,List<Widget> output){output.add(widget);if(widget instanceof WidgetGroup group)for(var child:group.widgets)visit(child,output);}
}
