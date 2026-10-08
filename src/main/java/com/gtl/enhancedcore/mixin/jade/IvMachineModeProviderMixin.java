package com.gtl.enhancedcore.mixin.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.BlockAccessor;

/** Keep Jade's original machine-mode renderer, replacing only the active IV mode data. */
@Mixin(targets = "com.gregtechceu.gtceu.integration.jade.provider.MachineModeProvider",
        priority = 900, remap = false)
public abstract class IvMachineModeProviderMixin {
    @Inject(method = "appendServerData(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/BlockAccessor;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtle$crossRecipeMode(CompoundTag data, BlockAccessor accessor, CallbackInfo ci) {
        if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity entity)
                || !(entity.getMetaMachine() instanceof WorkableElectricMultiblockMachine machine)
                || !IvMachineScope.crossRecipeEnabled(machine)) return;

        // GTCEu's appendTooltip translates each ResourceLocation as namespace.path.
        // A single synthetic entry lets its compact and expanded views use the same mode.
        ListTag modes = new ListTag();
        modes.add(StringTag.valueOf("gtl_enhancedcore:gui.iv_mode"));
        data.put("RecipeTypes", modes);
        data.putInt("CurrentRecipeType", 0);
        ci.cancel();
    }
}
