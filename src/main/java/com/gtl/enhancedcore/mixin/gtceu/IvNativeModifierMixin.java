package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtl.enhancedcore.common.recipe.iv.IvNativeMatchContext;
import com.mojang.datafixers.util.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Allocate native hatch/coil capacity without capping the subsequent native subtick multiplier. */
@Mixin(value = GTRecipeModifiers.class, remap = false, priority = 1200)
public abstract class IvNativeModifierMixin {
    @Redirect(method = {"hatchParallel", "accurateParallel"}, at = @At(value = "INVOKE",
            target = "Lcom/gregtechceu/gtceu/api/recipe/modifier/ParallelLogic;applyParallel(Lcom/gregtechceu/gtceu/api/machine/MetaMachine;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;IZ)Lcom/mojang/datafixers/util/Pair;"))
    private static Pair<GTRecipe,Integer> iv$capacity(MetaMachine machine, GTRecipe recipe, int parallel, boolean modifyDuration) {
        var context = IvNativeMatchContext.forMachine(machine);
        var result = ParallelLogic.applyParallel(machine, recipe, context == null ? parallel : context.capacity(parallel), modifyDuration);
        if (context != null) context.reserved(result.getSecond());
        return result;
    }
}
