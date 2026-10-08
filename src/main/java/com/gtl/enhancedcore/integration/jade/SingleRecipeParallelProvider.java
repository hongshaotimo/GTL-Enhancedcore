package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.SingleRecipeParallel;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** The native hatch row is a configured maximum; this row reports the actual running recipe. */
public final class SingleRecipeParallelProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final String KEY = "enhancedSingleRecipeParallel";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity be)
                || !SingleRecipeParallel.supports(be.getMetaMachine())) return;
        var machine = (WorkableElectricMultiblockMachine) be.getMetaMachine();
        if (!machine.isFormed()) return;
        var value = new CompoundTag();
        value.putLong("current", SingleRecipeParallel.current(machine));
        value.putLong("lanes", SingleRecipeParallel.lanes((IThreadModifierMachine) machine));
        data.put(KEY, value);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(KEY)) return;
        var value = accessor.getServerData().getCompound(KEY);
        tooltip.add(Component.translatable("gtl_enhancedcore.parallel.same_recipe_lanes", value.getLong("lanes")));
        tooltip.add(Component.translatable("gtl_enhancedcore.parallel.current", value.getLong("current")));
    }

    @Override
    public ResourceLocation getUid() {
        return GTLEnhancedcore.id("single_recipe_parallel");
    }
}
