package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Do not permanently change the user's ADD mode; finish paid batches after disconnect. */
@Mixin(value=MutableRecipesLogic.class,remap=false)
public abstract class GalaxyMutableModeMixin implements com.gtl.enhancedcore.common.machine.galaxy.GalaxyMutableModeAccess {
    @Shadow private boolean useMultipleRecipes;
    @Override public boolean galaxy$nativeMultipleRecipeMode() { return useMultipleRecipes; }
    @Unique @Persisted private boolean galaxy$ownsBatch;
    @Unique private boolean galaxy$active() {
        var machine = ((MutableRecipesLogic<?>)(Object)this).getMachine();
        // Isolated orders already have their own capacity and scheduler. A temporary ADD
        // mode would change native timing and make GTLCore reject the batch configurator.
        return !com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.crossRecipeEnabled(machine)
                && GalaxyLinks.crossRecipeEnabledByConnection(machine);
    }
    @ModifyExpressionValue(method="findAndHandleRecipe",at=@At(value="FIELD",target="Lcom/gtladd/gtladditions/api/machine/logic/MutableRecipesLogic;useMultipleRecipes:Z"),require=1)
    private boolean galaxy$startMode(boolean original) {
        galaxy$ownsBatch = !original && galaxy$active();
        return original || galaxy$ownsBatch;
    }
    @ModifyExpressionValue(method={"onRecipeFinish","handleRecipeWorking"},at=@At(value="FIELD",target="Lcom/gtladd/gtladditions/api/machine/logic/MutableRecipesLogic;useMultipleRecipes:Z"),require=2)
    private boolean galaxy$paidMode(boolean original) { return original || galaxy$ownsBatch; }
    @ModifyExpressionValue(method="isMultipleRecipeMode",at=@At(value="FIELD",target="Lcom/gtladd/gtladditions/api/machine/logic/MutableRecipesLogic;useMultipleRecipes:Z"),require=1)
    private boolean galaxy$displayMode(boolean original) { return original || galaxy$ownsBatch || galaxy$active(); }
    @Inject(method="getRecipe",at=@At("HEAD"),cancellable=true,require=1)
    private void galaxy$restoreAfterPaidBatch(CallbackInfoReturnable<GTRecipe> cir) {
        if (galaxy$ownsBatch && !useMultipleRecipes && !galaxy$active()) {
            galaxy$ownsBatch=false;
            cir.setReturnValue(null);
        }
    }
}
