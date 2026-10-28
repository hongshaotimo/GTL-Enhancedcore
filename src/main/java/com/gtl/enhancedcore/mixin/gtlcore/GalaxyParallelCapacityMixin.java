package com.gtl.enhancedcore.mixin.gtlcore;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=MultipleRecipesLogic.class,remap=false,priority=1300)
public abstract class GalaxyParallelCapacityMixin {
    // The second long local is the remaining batch budget in both GTLCore and ADD's
    // overwrite. Set it once, rather than regranting capacity for every candidate.
    @ModifyVariable(method="getRecipe",at=@At(value="STORE",ordinal=0),ordinal=1,require=1)
    private long galaxy$capacity(long original) {
        return GalaxyLinks.batchBudget((WorkableElectricMultiblockMachine)((RecipeLogic)(Object)this).getMachine(), original);
    }
    @Dynamic("com.gtladd.gtladditions.mixin.gtlcore.machine.trait.MultipleRecipesLogicMixin")
    @ModifyVariable(method="gtladditions$getWirelessRecipe",at=@At(value="STORE",ordinal=0),ordinal=0,require=1)
    private long galaxy$wirelessCapacity(long original) { return galaxy$capacity(original); }
}
