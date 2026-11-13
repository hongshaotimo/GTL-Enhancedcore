package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeEngine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeLogic.class, remap = false, priority = 1350)
public abstract class GalaxyRecipeLogicMixin implements com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeAccess {
    @Shadow protected GTRecipe lastRecipe;
    @Shadow protected GTRecipe lastOriginRecipe;
    @Shadow protected boolean recipeDirty;
    @Shadow protected com.gregtechceu.gtceu.api.machine.TickableSubscription subscription;
    @Unique private int galaxy$rotation;
    @Unique private GalaxyIndependentRecipeEngine galaxy$independent;
    @Unique private boolean galaxy$nativeFallback;
    @Override public boolean galaxy$hasIndependentJobs() {
        return galaxy$independent != null && galaxy$independent.hasJobs();
    }
    @Override public boolean galaxy$blockNativeSearch() {
        var logic = (RecipeLogic)(Object)this;
        if (galaxy$nativeFallback || !GalaxyIndependentRecipeEngine.supports(logic)
                || !GalaxyLinks.crossRecipeEnabledByConnection((WorkableElectricMultiblockMachine)logic.getMachine()))
            return false;
        if (!galaxy$hasIndependentJobs()) {
            lastOriginRecipe = null;
            lastRecipe = null;
            logic.setStatus(RecipeLogic.Status.IDLE);
        }
        return true;
    }
    @Unique private boolean galaxy$independentMode() {
        var logic = (RecipeLogic)(Object)this;
        if (!GalaxyIndependentRecipeEngine.supports(logic)) return false;
        if (galaxy$independent != null && galaxy$independent.hasJobs()) return true;
        // An already-paid native batch finishes on its original execution path.
        if (logic.getLastRecipe() != null && logic.getDuration() > 0 && !logic.isIdle()) return false;
        return GalaxyLinks.crossRecipeEnabledByConnection((WorkableElectricMultiblockMachine)logic.getMachine());
    }
    @Unique private GalaxyIndependentRecipeEngine galaxy$engine() {
        if (galaxy$independent == null) galaxy$independent = new GalaxyIndependentRecipeEngine((RecipeLogic)(Object)this);
        return galaxy$independent;
    }
    @WrapMethod(method = "serverTick")
    private void galaxy$independentTick(Operation<Void> original) {
        if (!galaxy$independentMode()) { original.call(); return; }
        var engine = galaxy$engine();
        engine.tick();
        // Independent admission deliberately skips specialized work such as total CWU.
        // With no owned work, let the original search/setup path consider those recipes.
        if (!engine.hasJobs() && ((RecipeLogic)(Object)this).isWorkingEnabled()) {
            galaxy$nativeFallback = true;
            try { original.call(); }
            finally { galaxy$nativeFallback = false; }
        }
    }
    @Inject(method = "updateTickSubscription", at = @At("HEAD"), cancellable = true)
    private void galaxy$independentSubscription(CallbackInfo ci) {
        var logic = (RecipeLogic)(Object)this;
        if (!galaxy$independentMode()) return;
        var machine = (WorkableElectricMultiblockMachine)logic.getMachine();
        if (machine.isRemote()) return;
        if (machine.isFormed()) subscription = machine.subscribeServerTick(subscription, logic::serverTick);
        else if (subscription != null) { subscription.unsubscribe(); subscription = null; }
        ci.cancel();
    }
    @Inject(method = "saveCustomPersistedData", at = @At("TAIL"))
    private void galaxy$saveIndependent(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        if (galaxy$independent != null) tag.put("gtleIndependentRecipes", galaxy$independent.save());
    }
    @Inject(method = "loadCustomPersistedData", at = @At("TAIL"))
    private void galaxy$loadIndependent(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("gtleIndependentRecipes")) galaxy$engine().load(tag.getCompound("gtleIndependentRecipes"));
    }
    @Inject(method = {"inValid", "resetRecipeLogic", "handleRecipeWorking", "onRecipeFinish"},
            at = @At("HEAD"), cancellable = true)
    private void galaxy$preserveIndependent(CallbackInfo ci) {
        if (galaxy$independent != null && galaxy$independent.hasJobs()) ci.cancel();
    }
    @Inject(method = "findAndHandleRecipe", at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxy$batch(CallbackInfo ci) {
        var logic = (RecipeLogic)(Object)this;
        if (galaxy$blockNativeSearch()) {
            ci.cancel();
            return;
        }
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
    @ModifyExpressionValue(method = "onRecipeFinish", at = @At(value = "FIELD", target =
            "Lcom/gregtechceu/gtceu/api/machine/trait/RecipeLogic;recipeDirty:Z"), require = 1)
    private boolean galaxy$finishBeforeIndependent(boolean dirty) {
        var logic = (RecipeLogic)(Object)this;
        // This read follows native output delivery and controls immediate recipe reuse.
        return dirty || !galaxy$nativeFallback && GalaxyIndependentRecipeEngine.supports(logic)
                && GalaxyLinks.crossRecipeEnabledByConnection((WorkableElectricMultiblockMachine)logic.getMachine());
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
