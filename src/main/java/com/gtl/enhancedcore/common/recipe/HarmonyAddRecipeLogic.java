package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.HarmonyMachine;

/** Native RecipeLogic lifecycle for Harmony's ADD-prepared cosmos batch. */
public final class HarmonyAddRecipeLogic extends RecipeLogic implements IRecipeStatus {
    private final HarmonyMachine owner;

    public HarmonyAddRecipeLogic(HarmonyMachine owner) {
        super(owner);
        this.owner = owner;
    }

    @Override public void findAndHandleRecipe() {
        lastRecipe = null;
        lastOriginRecipe = null;
        ((IRecipeStatus) this).setRecipeStatus(null);
        ((IRecipeStatus) this).setWorkingStatus(null);
        var batch = HarmonyAddRecipes.cosmos(owner, this);
        if (batch != null) {
            lastOriginRecipe = batch;
            setupRecipe(batch);
        }
    }

    @Override public void onRecipeFinish() {
        // Preserve the paid batch while outputs are blocked; a retry never rerolls its chances.
        if (lastRecipe != null && !RecipeRunnerHelper.matchRecipeOutput(owner, lastRecipe)) {
            setWaiting(null);
            return;
        }
        // GTLCore must not feed the already consumed, output-only batch into its reuse fast path.
        lastOriginRecipe = null;
        recipeDirty = true;
        super.onRecipeFinish();
        if (isIdle()) findAndHandleRecipe();
    }
}
