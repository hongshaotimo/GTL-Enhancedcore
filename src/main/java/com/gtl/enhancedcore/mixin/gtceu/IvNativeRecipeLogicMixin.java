package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RecipeLogic.class, remap = false, priority = 1200)
public abstract class IvNativeRecipeLogicMixin implements IvNativeAccess {
    @Shadow protected GTRecipe lastRecipe;
    @Shadow protected GTRecipe lastOriginRecipe;
    @Shadow protected boolean recipeDirty;
    @Shadow protected int progress;
    @Shadow protected int duration;
    @Shadow private boolean isActive;
    @Shadow private Component waitingReason;
    @Shadow protected com.gregtechceu.gtceu.api.machine.TickableSubscription subscription;
    @Shadow public abstract void setStatus(RecipeLogic.Status status);
    @Unique private IvNativeEngine iv$engine;
    @Unique @DescSynced private CompoundTag iv$summary = new CompoundTag();
    @Unique @DescSynced private String iv$message = "";
    @Unique @Persisted private boolean iv$managed;
    @Unique @Persisted private boolean iv$workingEnabled = true;
    @Unique @Persisted private boolean iv$workingStateStored;

    @Override public IvNativeEngine iv$engine() {
        if (iv$engine == null) {
            var logic = (RecipeLogic)(Object)this;
            iv$engine = new IvNativeEngine((WorkableElectricMultiblockMachine)logic.getMachine(), logic, this);
        }
        return iv$engine;
    }
    @Override public CompoundTag iv$summary() { return iv$summary.copy(); }
    @Override public String iv$message() { return iv$message; }
    @Override public boolean iv$managed() { return iv$managed; }
    @Override public void iv$managed(boolean value) { iv$managed = value; }
    @Override public void iv$publish(CompoundTag summary, String message, GTRecipe display, int elapsed, int ticks, RecipeLogic.Status status) {
        iv$summary = summary; iv$message = message; lastRecipe = display; progress = elapsed; duration = ticks;
        lastOriginRecipe = null; recipeDirty = false;
        ((RecipeLogic)(Object)this).lastFailedMatches = null;
        isActive = display != null && status == RecipeLogic.Status.WORKING; setStatus(status);
        var details = iv$details();
        waitingReason = status == RecipeLogic.Status.WAITING && !details.isEmpty() ? details.getFirst() : null;
    }
    @Unique private boolean iv$isCrossRecipeEnabled() {
        return IvMachineScope.crossRecipeEnabled(((RecipeLogic)(Object)this).getMachine());
    }
    @Unique private void iv$restoreWorkingState() {
        if (!iv$workingStateStored) {
            iv$workingEnabled = !((RecipeLogic)(Object)this).isSuspend();
            iv$workingStateStored = true;
        }
    }
    @Inject(method = "isWorkingEnabled", at = @At("HEAD"), cancellable = true)
    private void iv$workingEnabled(CallbackInfoReturnable<Boolean> cir) {
        if (iv$isCrossRecipeEnabled()) cir.setReturnValue(IvNativeRecoveryState.enabled(iv$workingStateStored,
                iv$workingEnabled, ((RecipeLogic)(Object)this).isSuspend()));
    }
    @Inject(method = "setWorkingEnabled", at = @At("HEAD"))
    private void iv$setWorkingEnabled(boolean enabled, CallbackInfo ci) {
        if (!iv$isCrossRecipeEnabled()) return;
        iv$workingEnabled = enabled; iv$workingStateStored = true;
        ((RecipeLogic)(Object)this).getMachine().markDirty();
    }
    @Inject(method = {"onMachineLoad", "resetRecipeLogic"}, at = @At("HEAD"))
    private void iv$beforeLifecycleReset(CallbackInfo ci) {
        if (iv$isCrossRecipeEnabled()) iv$restoreWorkingState();
    }
    @Inject(method = "resetRecipeLogic", at = @At("TAIL"))
    private void iv$afterLifecycleReset(CallbackInfo ci) {
        if (!iv$isCrossRecipeEnabled()) return;
        if (iv$engine != null) iv$engine.invalidateRuntime();
        if (!iv$workingEnabled) setStatus(RecipeLogic.Status.SUSPEND);
    }
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void iv$tick(CallbackInfo ci) {
        if (!iv$isCrossRecipeEnabled()) return;
        iv$engine().tick();
        ci.cancel();
    }
    @Inject(method = "updateTickSubscription", at = @At("HEAD"), cancellable = true)
    private void iv$subscription(CallbackInfo ci) {
        var logic = (RecipeLogic)(Object)this;
        if (!IvMachineScope.crossRecipeEnabled(logic.getMachine())) return;
        var machine = (WorkableElectricMultiblockMachine)logic.getMachine();
        if (machine.isRemote()) return;
        if (machine.isFormed()) subscription = machine.subscribeServerTick(subscription, logic::serverTick);
        else if (subscription != null) { subscription.unsubscribe(); subscription = null; }
        ci.cancel();
    }
    @Inject(method = {"findAndHandleRecipe", "onRecipeFinish", "handleRecipeWorking", "inValid"}, at = @At("HEAD"), cancellable = true)
    private void iv$noSharedExecution(CallbackInfo ci) {
        if (iv$isCrossRecipeEnabled()) ci.cancel();
    }
    @Inject(method = "checkMatchedRecipeAvailable", at = @At("HEAD"), cancellable = true)
    private void iv$noFailedCacheExecution(GTRecipe recipe, CallbackInfoReturnable<Boolean> cir) {
        if (iv$isCrossRecipeEnabled()) cir.setReturnValue(false);
    }
    @Inject(method = "setupRecipe", at = @At("HEAD"), cancellable = true)
    private void iv$noExternalSetup(GTRecipe recipe, CallbackInfo ci) {
        if (iv$isCrossRecipeEnabled()) ci.cancel();
    }
}
