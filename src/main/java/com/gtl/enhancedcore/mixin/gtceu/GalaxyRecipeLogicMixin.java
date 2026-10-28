package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeLogic.class, remap = false, priority = 1350)
public abstract class GalaxyRecipeLogicMixin {
    @Shadow protected GTRecipe lastRecipe;
    @Shadow protected GTRecipe lastOriginRecipe;
    @Shadow protected boolean recipeDirty;
    @Unique private int galaxy$rotation;
    @Inject(method = "findAndHandleRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxy$batch(CallbackInfo ci) {
        var logic = (RecipeLogic)(Object)this;
        if (!GalaxyRecipeBatch.applicable(logic)) return;
        GTRecipe batch = GalaxyRecipeBatch.find((WorkableElectricMultiblockMachine)logic.getMachine(), logic, galaxy$rotation);
        if (batch == null) return; // Keep custom generated recipes and specialized work ledgers available.
        galaxy$rotation++;
        lastOriginRecipe = null;
        lastRecipe = null;
        logic.setupRecipe(batch);
        // Machine-specific beforeWorking or IO handlers may reject a combined candidate.
        // In that case let GTCEu continue its native single-recipe search this tick.
        if (lastRecipe != batch || !logic.isActive()) return;
        logic.lastFailedMatches = null;
        ci.cancel();
    }
    @ModifyVariable(method="setupRecipe",at=@At("HEAD"),argsOnly=true,require=1)
    private GTRecipe galaxy$customCandidate(GTRecipe recipe) {
        return GalaxyRecipeBatch.expand((RecipeLogic)(Object)this, recipe);
    }
    @Inject(method = "onRecipeFinish", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxy$rebuildNextBatch(CallbackInfo ci) {
        // Never repeat an already scaled recipe, including after source loss or reload.
        if (lastRecipe != null && lastRecipe.data.getBoolean(GalaxyRecipeBatch.MARKER)) {
            recipeDirty = true;
            if (!org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipeOutput(((RecipeLogic)(Object)this).machine, lastRecipe)) {
                ((RecipeLogic)(Object)this).setWaiting(null);
                ci.cancel();
            }
        }
    }
}
