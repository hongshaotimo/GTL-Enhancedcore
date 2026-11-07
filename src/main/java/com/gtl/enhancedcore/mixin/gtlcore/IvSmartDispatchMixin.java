package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.api.stacks.AEKey;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import net.minecraft.nbt.CompoundTag;
import com.gtl.enhancedcore.common.recipe.iv.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.gtlcore.gtlcore.mixin.ae2.logic.ExecutingCraftingJobAccessor;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve upstream sizing and the exact IV dispatch amount before AE extracts material. */
@Mixin(value = CraftingCpuLogic.class, priority = 1200, remap = false)
public abstract class IvSmartDispatchMixin {
    @Shadow @Final private ListCraftingInventory inventory;
    @Shadow private ExecutingCraftingJob job;
    @Shadow @Final private CraftingCPUCluster cluster;
    @Unique private final ProcessingInitialInputs iv$initialInputs = new ProcessingInitialInputs();

    @Inject(method = "trySubmitJob", at = @At("RETURN"), require = 1)
    private void iv$rememberInitialInputs(IGrid grid, ICraftingPlan plan, IActionSource source,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue().successful()) iv$initialInputs.submitted(plan);
    }
    @Inject(method = "writeToNBT", at = @At("TAIL"), require = 1)
    private void iv$saveInitialInputs(CompoundTag tag, CallbackInfo ci) { iv$initialInputs.write(tag); }
    @Inject(method = "readFromNBT", at = @At("TAIL"), require = 1)
    private void iv$loadInitialInputs(CompoundTag tag, CallbackInfo ci) { iv$initialInputs.read(tag); }

    @Unique private long iv$waiting(AEKey key) {
        return ((ExecutingCraftingJobAccessor) job).getWaitingFor().list.get(key);
    }
    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/crafting/CraftingPatternAutoExpand;getOperations(ZLappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;J)J"))
    private long iv$rememberOrder(boolean processing, ICraftingProvider provider, IPatternDetails pattern, long requested) {
        IvDispatchContext.clear();
        long operations = CraftingPatternAutoExpand.getOperations(processing, provider, pattern, requested);
        if (processing && provider instanceof MEPatternBufferPartMachine && !iv$initialInputs.isEmpty())
            iv$initialInputs.replenish(pattern, operations, inventory, cluster.getGrid(), cluster.getSrc(), cluster::markDirty);
        if (processing) operations = Math.max(1, ProcessingDispatchBatch.limit(pattern, operations, inventory, this::iv$waiting));
        if (provider instanceof MEPatternBufferPartMachine buffer && IvBuffers.isolated(buffer))
            IvDispatchContext.set(provider, pattern, operations);
        return operations;
    }
    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/AEUtils;extractForProcessingPattern(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lappeng/api/stacks/KeyCounter;J)[Lappeng/api/stacks/KeyCounter;"), require = 1, allow = 1)
    private KeyCounter[] iv$extractBatch(IPatternDetails pattern, ICraftingInventory source, KeyCounter expected, long operations) {
        return ProcessingDispatchBatch.extract(pattern, source, expected, operations, this::iv$waiting);
    }
    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/AEUtils;extractForProcessingPattern(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"), require = 1, allow = 1)
    private KeyCounter[] iv$extractSingle(IPatternDetails pattern, ICraftingInventory source, KeyCounter expected) {
        return ProcessingDispatchBatch.extract(pattern, source, expected, 1, this::iv$waiting);
    }
    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean iv$dispatch(ICraftingProvider provider, IPatternDetails pattern, KeyCounter[] input) {
        try { return provider.pushPattern(pattern, input); }
        finally { IvDispatchContext.clear(); }
    }
}
