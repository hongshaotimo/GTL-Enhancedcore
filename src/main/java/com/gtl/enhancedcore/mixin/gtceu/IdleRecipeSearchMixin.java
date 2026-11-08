package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.performance.IdleRecipeSearchState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Limits failed idle searches at the virtual call site, including specialized recipe-logic subclasses. */
@Mixin(value = RecipeLogic.class, remap = false)
public abstract class IdleRecipeSearchMixin {
    @Shadow protected GTRecipe lastRecipe;
    @Shadow protected boolean recipeDirty;
    @Unique private final IdleRecipeSearchState enhancedcore$idleSearch = new IdleRecipeSearchState();
    @Unique private boolean enhancedcore$skippedIdleSearch;

    @WrapMethod(method = "serverTick")
    private void enhancedcore$idleSearchTick(Operation<Void> original) {
        boolean previous = enhancedcore$skippedIdleSearch;
        enhancedcore$skippedIdleSearch = false;
        try {
            original.call();
        } finally {
            enhancedcore$skippedIdleSearch = previous;
        }
    }

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/machine/trait/RecipeLogic;findAndHandleRecipe()V"), require = 2, allow = 2)
    private void enhancedcore$searchWhenDue(RecipeLogic logic, Operation<Void> original) {
        var lock = (ILockRecipe) logic;
        long now = logic.getMachine().getOffsetTimer();
        if (!enhancedcore$idleSearch.shouldSearch(now, logic.isIdle(), lastRecipe != null, recipeDirty,
                logic.machine.getRecipeType(), lock.isLock(), lock.getLockRecipe())) {
            enhancedcore$skippedIdleSearch = true;
            return;
        }
        original.call(logic);
        enhancedcore$idleSearch.searched(now, logic.isIdle() && lastRecipe == null);
    }

    // Only the fallback-entry check is hidden. The later subscription check sees the actual cache.
    @ModifyExpressionValue(method = "serverTick", at = @At(value = "FIELD", target =
            "Lcom/gregtechceu/gtceu/api/machine/trait/RecipeLogic;lastFailedMatches:Ljava/util/List;",
            opcode = Opcodes.GETFIELD, ordinal = 0), require = 1, allow = 1)
    private List<GTRecipe> enhancedcore$skipFailedFallback(List<GTRecipe> matches) {
        return enhancedcore$skippedIdleSearch ? null : matches;
    }

    // A short-lived cached failure must not strand an inventory-triggered subscription before retry is due.
    @ModifyExpressionValue(method = "serverTick", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;keepSubscribing()Z", ordinal = 1),
            require = 1, allow = 1)
    private boolean enhancedcore$keepDeferredRetrySubscribed(boolean keepSubscribing) {
        return keepSubscribing || enhancedcore$skippedIdleSearch;
    }

    @Inject(method = {"markLastRecipeDirty", "resetRecipeLogic", "onMachineLoad", "loadCustomPersistedData", "setWorkingEnabled"},
            at = @At("HEAD"), require = 5, allow = 5)
    private void enhancedcore$invalidateIdleSearch(CallbackInfo ci) {
        enhancedcore$idleSearch.invalidate();
    }
}
