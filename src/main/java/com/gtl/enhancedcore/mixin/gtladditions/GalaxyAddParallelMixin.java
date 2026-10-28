package com.gtl.enhancedcore.mixin.gtladditions;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
@Mixin(value={GTLAddMultipleRecipesLogic.class,MutableRecipesLogic.class},remap=false)
public abstract class GalaxyAddParallelMixin {
    @ModifyExpressionValue(method="calculateParallels",at=@At(value="INVOKE",target="Lorg/gtlcore/gtlcore/api/machine/multiblock/ParallelMachine;getMaxParallel()I"),require=1)
    private int galaxy$parallel(int original) {
        return ((RecipeLogic)(Object)this).getMachine() instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine m && GalaxyLinks.unlimited(m) ? Integer.MAX_VALUE : original;
    }

    // Normal budgets already multiply the enhanced getMultipleThreads value.
    // Do not add another 10,000 lanes at this second entry point.
    @ModifyVariable(method="calculateParallels",at=@At(value="STORE",ordinal=0),ordinal=0,require=1)
    private long galaxy$budget(long original) {
        return ((RecipeLogic)(Object)this).getMachine() instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine m
                && GalaxyLinks.unlimited(m) ? (long)Integer.MAX_VALUE * Integer.MAX_VALUE : original;
    }
}
