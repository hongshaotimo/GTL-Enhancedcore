package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtl.enhancedcore.common.recipe.iv.*;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;

public final class IvOrderInfoProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final String KEY="gtlEnhancedcoreIvOrders";
    @Override public ResourceLocation getUid(){return new ResourceLocation("gtl_enhancedcore","iv_orders");}

    @Override public void appendServerData(CompoundTag data,BlockAccessor accessor){
        data.remove(KEY);
        if(!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity))return;
        var machine = entity.getMetaMachine();
        if(machine instanceof TieredParallelMachine owned && IvMachineScope.crossRecipeEnabled(owned)){
            data.put(KEY,((IvRecipeLogic)owned.getRecipeLogic()).getOrderSummary());
            return;
        }
        if(machine instanceof WorkableElectricMultiblockMachine electric
                && IvMachineScope.nativeTarget(electric) && IvMachineScope.crossRecipeEnabled(electric)){
            data.put(KEY, ((IvNativeAccess)electric.getRecipeLogic()).iv$summary());
        }
    }

    @Override public void appendTooltip(ITooltip tooltip,BlockAccessor accessor,IPluginConfig config){
        var server=accessor.getServerData();
        if(server.contains(KEY)){
            var lines=new ArrayList<Component>();
            IvPresentation.append(lines,server.getCompound(KEY),false);
            for(var line:lines)tooltip.add(line);
            tooltip.add(Component.translatable("gtl_enhancedcore.gui.iv_output_scope").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
