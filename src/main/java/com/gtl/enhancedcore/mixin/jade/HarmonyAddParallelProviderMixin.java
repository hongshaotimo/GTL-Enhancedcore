package com.gtl.enhancedcore.mixin.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gtl.enhancedcore.common.recipe.HarmonyAddRecipes;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph;
import net.minecraft.nbt.CompoundTag;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.HarmonyMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.BlockAccessor;

/** ADD's existing Jade rows consume these same keys for the two no-energy controllers. */
@Mixin(targets = "com.gregtechceu.gtceu.integration.jade.provider.ParallelProvider", remap = false, priority = 850)
public abstract class HarmonyAddParallelProviderMixin {
    @Inject(method = "appendServerData(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/BlockAccessor;)V",
            at = @At("TAIL"), require = 1, allow = 1)
    private void gtle$harmonyThreads(CompoundTag data, BlockAccessor accessor, CallbackInfo ci) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity)
                || !(entity.getMetaMachine() instanceof HarmonyMachine machine)
                || !HarmonyAddRecipes.enabled(machine) || !machine.isFormed()) return;
        data.putLong("parallel", machine instanceof ArcanicAstrograph astrograph ? astrograph.getParallelAmount() : 1L);
        data.putLong("threads", HarmonyAddRecipes.THREADS);
    }
}
