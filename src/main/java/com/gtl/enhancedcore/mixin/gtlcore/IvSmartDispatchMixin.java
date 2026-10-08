package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import com.gtl.enhancedcore.common.recipe.iv.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Observe, never reduce, GTLCore's operations. Only IV-owned super buffers receive the context. */
@Mixin(value = CraftingCpuLogic.class, priority = 1200, remap = false)
public abstract class IvSmartDispatchMixin {
    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/crafting/CraftingPatternAutoExpand;getOperations(ZLappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;J)J"))
    private long iv$rememberOrder(boolean processing, ICraftingProvider provider, IPatternDetails pattern, long requested) {
        IvDispatchContext.clear();
        long operations = CraftingPatternAutoExpand.getOperations(processing, provider, pattern, requested);
        if (provider instanceof MEPatternBufferPartMachine buffer && IvBuffers.isolated(buffer))
            IvDispatchContext.set(provider, pattern, operations);
        return operations;
    }
    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean iv$dispatch(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] input) {
        try { return provider.pushPattern(pattern, input); }
        finally { IvDispatchContext.clear(); }
    }
}
