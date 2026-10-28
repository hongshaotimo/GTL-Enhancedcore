package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = MultipleRecipesLogic.class, remap = false, priority = 1200)
public abstract class IvNativeRecipeThreadLimitMixin {
    @ModifyArg(method = "getRecipe", at = @At(value = "INVOKE", target =
            "Lorg/gtlcore/gtlcore/api/recipe/IParallelLogic;getMaxParallel(Lcom/gregtechceu/gtceu/api/capability/recipe/IRecipeCapabilityHolder;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;J)J"),
            index = 2, require = 1)
    private long iv$singleParallelBudget(long original) {
        var machine = (WorkableElectricMultiblockMachine)((RecipeLogic)(Object)this).getMachine();
        if (IvMachineScope.unlimited(machine)) return original;
        if (!IvMachineScope.nativeTarget(machine) || IvMachineScope.crossRecipeEnabled(machine)) return original;
        // 保留原生线程的机器同样保留其原生并行预算，避免接入 IV 后削弱原有能力。
        if (IvMachineScope.keepsNativeThreads(machine)) return original;
        return IvMachineScope.parallel(machine);
    }
}
