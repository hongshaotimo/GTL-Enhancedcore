package com.gtl.enhancedcore.mixin.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.TieredParallelMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.BlockAccessor;

/** Supply the effective thread count to GTLAdditions' existing Jade row. */
@Mixin(targets = "com.gregtechceu.gtceu.integration.jade.provider.ParallelProvider",
        priority = 900, remap = false)
public abstract class IvParallelProviderMixin {
    @Inject(method = "appendServerData(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/BlockAccessor;)V",
            at = @At("TAIL"), remap = false, require = 1)
    private void gtle$effectiveThreads(CompoundTag data, BlockAccessor accessor, CallbackInfo ci) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity)
                || !(entity.getMetaMachine() instanceof WorkableElectricMultiblockMachine machine)
                || !machine.isFormed()
                || !IvMachineScope.crossRecipeEnabled(machine)
                || !(IvMachineScope.nativeTarget(machine) || machine instanceof TieredParallelMachine)) return;

        // ADD owns the tooltip wording and GOLD number style; it reads this shared key.
        data.putLong("threads", IvMachineScope.threads(machine));
    }
}
