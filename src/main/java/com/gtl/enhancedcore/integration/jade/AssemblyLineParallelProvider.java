package com.gtl.enhancedcore.integration.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.recipe.AssemblyLineParallelDisplay;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public final class AssemblyLineParallelProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    private static final String KEY = "enhancedAssemblyLineParallel";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity
                && entity.getMetaMachine() instanceof WorkableElectricMultiblockMachine machine
                && AssemblyLineParallelDisplay.supports(machine)) {
            data.putLong(KEY, AssemblyLineParallelDisplay.current(machine));
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getServerData().contains(KEY)) {
            AssemblyLineParallelDisplay.lines(accessor.getServerData().getLong(KEY)).forEach(tooltip::add);
        }
    }

    @Override
    public ResourceLocation getUid() {
        return GTLEnhancedcore.id("assembly_line_parallel");
    }
}
