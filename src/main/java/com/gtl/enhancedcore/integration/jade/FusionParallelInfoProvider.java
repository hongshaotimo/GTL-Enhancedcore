package com.gtl.enhancedcore.integration.jade;

import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import com.gtl.enhancedcore.GTLEnhancedcore;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;


/**
 * UV/UHV/UEV 聚变反应堆 Jade 并行显示：
 * 与 GUI 共用翻译键 gui.gtl_enhancedcore.fusion_reactor.parallel（同时处理%d个配方）。
 * 只作用于普通版三个 ID，压缩版自带 PARALLEL_HATCH 不走这里。
 */
public class FusionParallelInfoProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ResourceLocation UID = GTLEnhancedcore.id("fusion_parallel_info");


    private static final String KEY_PARALLEL = "gtlEnhancedcoreFusionParallel";

    private static boolean isTargetFusion(MachineDefinition def) {
        ResourceLocation id = def.getId();
        return id != null && FusionParallelPolicy.limit(id.getNamespace(), id.getPath()) > 0;
    }

    private static int configuredParallel(MachineDefinition def) {
        ResourceLocation id = def.getId();
        if (id == null) {
            return 0;
        }
        return FusionParallelPolicy.limit(id.getNamespace(), id.getPath());
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity machineBE)) {
            return;
        }
        MetaMachine machine = machineBE.getMetaMachine();
        if (!(machine instanceof FusionReactorMachine) || !isTargetFusion(machine.getDefinition())) {
            return;
        }
        GTRecipe last = ((WorkableMultiblockMachine) machine).getRecipeLogic().getLastRecipe();
        int parallels = (last != null && last.parallels > 1) ? last.parallels : configuredParallel(machine.getDefinition());
        data.putInt(KEY_PARALLEL, parallels);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(KEY_PARALLEL)) {
            return;
        }
        int parallels = data.getInt(KEY_PARALLEL);
        if (parallels > 1) {
            tooltip.add(Component.translatable("gui.gtl_enhancedcore.fusion_reactor.parallel",
                    Component.literal(String.valueOf(parallels)).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
