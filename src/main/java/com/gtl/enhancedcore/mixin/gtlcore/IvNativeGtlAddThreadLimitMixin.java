package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GTLAddMultipleRecipesLogic.class, remap = false, priority = 1200)
public abstract class IvNativeGtlAddThreadLimitMixin {
    @Inject(method = "getMultipleThreads", at = @At("HEAD"), cancellable = true)
    private void iv$singleThreadWithoutBuffer(CallbackInfoReturnable<Integer> cir) {
        var machine = ((RecipeLogic)(Object)this).getMachine();
        if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine electric
                && IvMachineScope.unlimited(electric)) return;
        if (!IvMachineScope.nativeTarget(machine) || IvMachineScope.crossRecipeEnabled(machine)) return;
        // 个别机器（如 inferno_cleft_smelting_vault）改造前就是 ADD 多配方机器、
        // 无总成时本就在跑 128 线程；压到 1 会削弱其原生能力，故保留上游线程。
        if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine electric
                && IvMachineScope.keepsNativeThreads(electric)) return;
        cir.setReturnValue(1);
    }
}
