package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
    @Unique @DescSynced private volatile boolean iv$crossRecipeEnabled;
    @Unique @DescSynced private volatile boolean iv$genericCloudTarget;
    @Unique @Persisted private boolean iv$managed;
    @Unique @Persisted @DescSynced private boolean iv$workingEnabled = true;
    @Unique @Persisted @DescSynced private boolean iv$workingStateStored;
    @Unique private boolean iv$workingCallback, iv$callbackReset, iv$coolantWaiting;
    @Unique private boolean iv$passivePatternLane;
    @Unique @Persisted @DescSynced private boolean iv$nativeInputsActive;
    @Unique private boolean iv$preferNativeInputs = true;
    @Unique private boolean iv$finishingNativeInputs, iv$nativeOutputReady;
    @Unique private long iv$nextNativeProbe;

    @Override public boolean iv$crossRecipeEnabled() { return iv$crossRecipeEnabled; }
    @Override public boolean iv$genericCloudTarget() { return iv$genericCloudTarget; }
    @Override public void iv$syncMode(boolean isolated, boolean cloudTarget) {
        iv$crossRecipeEnabled = isolated; iv$genericCloudTarget = cloudTarget;
    }
    @Override public RecipeScope iv$recipeContext(GTRecipe recipe, GTRecipe origin, int elapsed, int ticks) {
        var previousRecipe = lastRecipe; var previousOrigin = lastOriginRecipe;
        int previousProgress = progress, previousDuration = duration;
        lastRecipe = java.util.Objects.requireNonNull(recipe); lastOriginRecipe = origin;
        progress = elapsed; duration = ticks;
        return () -> {
            lastRecipe = previousRecipe; lastOriginRecipe = previousOrigin;
            progress = previousProgress; duration = previousDuration;
        };
    }

    @Override public boolean iv$onWorking() {
        var machine = (WorkableElectricMultiblockMachine)((RecipeLogic)(Object)this).getMachine();
        var id = machine.getDefinition().getId();
        boolean periodicCoolant = id.getNamespace().equals("gtceu")
                && (id.getPath().equals("cold_ice_freezer") || id.getPath().equals("blaze_blast_furnace"));
        if (periodicCoolant && iv$coolantWaiting && machine.getOffsetTimer() % 20 != 0) return false;
        iv$workingCallback = true; iv$callbackReset = false;
        try {
            boolean working = machine.onWorking();
            if (periodicCoolant) iv$coolantWaiting = iv$callbackReset;
            return working && !iv$callbackReset;
        }
        finally { iv$workingCallback = false; }
    }
    @Inject(method = "setProgress", at = @At("HEAD"))
    private void iv$observeWorkingReset(int value, CallbackInfo ci) {
        // GTLCore's cold freezer and blaze furnace report missing coolant by
        // resetting the native display progress while returning true.
        if (iv$workingCallback && value == 0) iv$callbackReset = true;
    }

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
    @Override public boolean iv$nativeInputsActive() { return iv$nativeInputsActive; }
    @Override public boolean iv$nativeInputExecution() { return iv$passivePatternLane; }
    @Override public void iv$nativeOutputAccepted() {
        if (iv$finishingNativeInputs) iv$nativeOutputReady = true;
    }
    @Override public void iv$publish(CompoundTag summary, String message, GTRecipe display, int elapsed, int ticks, RecipeLogic.Status status) {
        iv$summary = summary; iv$message = message; lastRecipe = display; progress = elapsed; duration = ticks;
        lastOriginRecipe = null; recipeDirty = false;
        ((RecipeLogic)(Object)this).lastFailedMatches = null;
        isActive = display != null && status == RecipeLogic.Status.WORKING; setStatus(status);
        var details = iv$details();
        waitingReason = status == RecipeLogic.Status.WAITING && !details.isEmpty() ? details.getFirst() : null;
    }
    @Unique private boolean iv$isCrossRecipeEnabled() {
        return !iv$passivePatternLane
                && IvMachineScope.crossRecipeEnabled(((RecipeLogic)(Object)this).getMachine());
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
        if (!IvMachineScope.crossRecipeEnabled(((RecipeLogic)(Object)this).getMachine())) return;
        iv$workingEnabled = enabled; iv$workingStateStored = true;
        ((RecipeLogic)(Object)this).getMachine().markDirty();
    }
    @Inject(method = "getProgressPercent", at = @At("HEAD"), cancellable = true)
    private void iv$orderProgressDisplay(CallbackInfoReturnable<Double> cir) {
        if (iv$isCrossRecipeEnabled()) cir.setReturnValue(duration <= 0 ? 0 : (double)progress / duration);
    }
    @Inject(method = {"onMachineLoad", "resetRecipeLogic"}, at = @At("HEAD"))
    private void iv$beforeLifecycleReset(CallbackInfo ci) {
        if (iv$isCrossRecipeEnabled()) iv$restoreWorkingState();
    }
    @Inject(method = "resetRecipeLogic", at = @At("TAIL"))
    private void iv$afterLifecycleReset(CallbackInfo ci) {
        iv$endNativeIfIdle();
        if (!iv$isCrossRecipeEnabled()) return;
        if (iv$engine != null) iv$engine.invalidateRuntime();
        if (!iv$workingEnabled) setStatus(RecipeLogic.Status.SUSPEND);
    }
    @WrapMethod(method = "serverTick")
    private void iv$tick(Operation<Void> original) {
        var logic = (RecipeLogic)(Object)this;
        if (!(logic.getMachine() instanceof WorkableElectricMultiblockMachine machine)
                || !IvMachineScope.crossRecipeEnabled(machine) || iv$passivePatternLane) { original.call(); return; }
        iv$runMixedInputs(() -> original.call(), allow -> iv$engine().tick(allow), () -> iv$engine().hasActiveBatches());
    }
    @Override public void iv$runMixedInputs(Runnable nativeTick, java.util.function.Consumer<Boolean> orderTick,
            java.util.function.BooleanSupplier activeBatches) {
        var logic = (RecipeLogic)(Object)this;
        var machine = (WorkableElectricMultiblockMachine) logic.getMachine();
        iv$restoreWorkingState();
        boolean physicalInputs = IvBuffers.genericCloudNativeInputs(machine);
        // A paid physical batch keeps its native state until output has really been
        // delivered. Cloud display publication must never replace its lastRecipe.
        if (iv$nativeInputsActive || physicalInputs && !iv$managed && lastRecipe != null
                && duration > 0 && !logic.isIdle()) {
            iv$managed = true; iv$nativeInputsActive = true;
            iv$passivePatternLane = true;
            try { nativeTick.run(); }
            finally {
                iv$passivePatternLane = false; iv$endNativeIfIdle(); logic.updateTickSubscription();
            }
            return;
        }
        long tick = machine.getOffsetTimer();
        if (physicalInputs && iv$preferNativeInputs && tick >= iv$nextNativeProbe
                && !activeBatches.getAsBoolean() && logic.isWorkingEnabled()
                && machine.isRecipeLogicAvailable() && machine.hasProxies()) {
            iv$managed = true; iv$clearNativeRecipe();
            iv$passivePatternLane = true;
            try { logic.findAndHandleRecipe(); }
            finally { iv$passivePatternLane = false; }
            if (lastRecipe != null && duration > 0 && !logic.isIdle()) {
                iv$nativeInputsActive = true; machine.markDirty(); return;
            }
            iv$clearNativeRecipe(); iv$preferNativeInputs = false; iv$nextNativeProbe = tick + 20;
        }
        iv$managed = true;
        orderTick.accept(!physicalInputs || !iv$preferNativeInputs || tick < iv$nextNativeProbe);
        if (logic.isWorkingEnabled()) iv$preferNativeInputs = true;
    }
    @Unique private void iv$clearNativeRecipe() {
        lastRecipe = null; lastOriginRecipe = null; progress = 0; duration = 0;
        isActive = false; recipeDirty = true; waitingReason = null;
        ((RecipeLogic)(Object)this).lastFailedMatches = null;
        setStatus(iv$workingEnabled ? RecipeLogic.Status.IDLE : RecipeLogic.Status.SUSPEND);
    }
    @Unique private void iv$endNativeIfIdle() {
        if (iv$nativeInputsActive && (lastRecipe == null || duration <= 0
                || ((RecipeLogic)(Object)this).isIdle())) {
            iv$nativeInputsActive = false; iv$preferNativeInputs = false;
            iv$clearNativeRecipe(); ((RecipeLogic)(Object)this).getMachine().markDirty();
        }
    }
    @WrapMethod(method = "onRecipeFinish")
    private void iv$finishNativeInputBatch(Operation<Void> original) {
        iv$finishNativeInputs(() -> original.call());
    }
    @Override public void iv$finishNativeInputs(Runnable finish) {
        if (!iv$nativeInputsActive || iv$finishingNativeInputs) { finish.run(); return; }
        var logic = (RecipeLogic)(Object)this;
        if (lastRecipe != null && !org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipeOutput(
                (com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine)logic.getMachine(), lastRecipe)) {
            logic.setWaiting(Component.translatable("gtl_enhancedcore.diagnostic.output")); return;
        }
        iv$finishingNativeInputs = true; iv$nativeOutputReady = false;
        boolean returned = false;
        try { finish.run(); returned = true; }
        finally {
            iv$finishingNativeInputs = false;
            if (returned && iv$nativeOutputReady) {
                // Preserve the upstream suspend-after-finish option as a real user pause.
                if (logic.isSuspend()) logic.setWorkingEnabled(false);
                iv$nativeInputsActive = false; iv$preferNativeInputs = false;
                iv$clearNativeRecipe(); ((RecipeLogic)(Object)this).getMachine().markDirty();
            }
        }
    }
    @Inject(method = "onRecipeFinish", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;afterWorking()V"))
    private void iv$nativeOutputAccepted(CallbackInfo ci) {
        iv$nativeOutputAccepted();
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
        if (iv$finishingNativeInputs || iv$isCrossRecipeEnabled()) ci.cancel();
    }
}
