package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.gtl.enhancedcore.common.cloud.CloudCpuRefill;
import com.gtl.enhancedcore.common.recipe.iv.*;
import org.gtlcore.gtlcore.mixin.ae2.logic.ExecutingCraftingJobAccessor;
import org.gtlcore.gtlcore.mixin.ae2.logic.ExecutingCraftingJobTaskProgressAccessor;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.integration.ae2.crafting.CraftingPatternAutoExpand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserve upstream sizing for IV; cloud endpoints clip exact whole batches before AE extracts material. */
@Mixin(value = CraftingCpuLogic.class, priority = 1200, remap = false)
public abstract class IvSmartDispatchMixin {
    @Shadow @Final private CraftingCPUCluster cluster;
    @Shadow private ExecutingCraftingJob job;
    @Shadow @Final private ListCraftingInventory inventory;

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void iv$refillCloudInputs(int budget, CraftingService service,
                                      appeng.api.networking.energy.IEnergyService energy,
                                      net.minecraft.world.level.Level level, CallbackInfoReturnable<Integer> cir) {
        if (job == null) return;
        var grid = cluster.getGrid();
        var source = cluster.getSrc();
        for (var task : ((ExecutingCraftingJobAccessor)job).getTasks().entrySet()) {
            long remaining = ((ExecutingCraftingJobTaskProgressAccessor)task.getValue()).getValue();
            CloudCpuRefill.replenish(task.getKey(), remaining, inventory, service, grid, source, cluster::markDirty);
        }
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE", target = "Lorg/gtlcore/gtlcore/integration/ae2/crafting/CraftingPatternAutoExpand;getOperations(ZLappeng/api/networking/crafting/ICraftingProvider;Lappeng/api/crafting/IPatternDetails;J)J"))
    private long iv$rememberOrder(boolean processing, ICraftingProvider provider, IPatternDetails pattern, long requested) {
        IvDispatchContext.clear();
        long operations = CraftingPatternAutoExpand.getOperations(processing, provider, pattern, requested);
        if (provider instanceof com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine cloud) {
            operations = cloud.cloudDispatchCapacity(pattern, operations);
            operations = Math.max(1, CloudCpuRefill.availableOperations(pattern, operations, inventory));
        }
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
