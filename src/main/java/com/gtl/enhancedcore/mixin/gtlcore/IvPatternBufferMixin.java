package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.crafting.IPatternDetails;
import com.gtl.enhancedcore.common.recipe.iv.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applied after GTLAdditions' getRealPattern overwrite; ordinary buffers return the original result. */
@Mixin(value = MEPatternBufferPartMachine.class, priority = 1200, remap = false)
public abstract class IvPatternBufferMixin implements IvBufferMethods {
    @Invoker("refreshAllByProduct") public abstract void iv$refreshPatterns();
    @Invoker("onPatternChange") public abstract void iv$patternChanged(int slot);
    @Invoker("getRealPattern") public abstract IPatternDetails iv$realPattern(int slot, ItemStack stack);
    @Inject(method = "getRealPattern", at = @At("RETURN"), cancellable = true)
    private void iv$route(int slot, ItemStack stack, CallbackInfoReturnable<IPatternDetails> cir) {
        if ((Object)this instanceof IvBufferAccess)
            cir.setReturnValue(IvBuffers.routedPattern((MEPatternBufferPartMachine)(Object)this, slot, stack, cir.getReturnValue()));
    }
    @Inject(method = "getAvailablePatterns", at = @At("RETURN"), cancellable = true)
    private void iv$publishAllowedTypes(CallbackInfoReturnable<java.util.List<IPatternDetails>> cir) {
        var buffer = (MEPatternBufferPartMachine)(Object)this;
        if (!IvBuffers.isolated(buffer)) return;
        var controller = IvBuffers.controller(buffer);
        var state = IvBuffers.state(buffer);
        if (controller == null || state == null || !state.healthy() || !state.accepting) { cir.setReturnValue(java.util.List.of()); return; }
        var types = java.util.Arrays.stream(controller.getDefinition().getRecipeTypes()).map(type -> type.registryName).collect(java.util.stream.Collectors.toSet());
        cir.setReturnValue(cir.getReturnValue().stream().filter(pattern -> {
            var tag = pattern.getDefinition().getTag();
            if (tag == null || !tag.contains(IvBuffers.ROUTE_KEY)) return false;
            var original = ItemStack.of(tag.getCompound(IvBuffers.ROUTE_KEY).getCompound("original"));
            return org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata.readRecipeTypeIds(original).stream().anyMatch(types::contains);
        }).toList());
    }
    @Inject(method = "onPatternChange", at = @At("HEAD"))
    private void iv$protectPattern(int slot, CallbackInfo ci) {
        var buffer = (MEPatternBufferPartMachine)(Object)this;
        IvBufferState state = IvBuffers.state(buffer);
        if (state == null) return;
        for (IvJob job : state.jobs) if (job.slot == slot && !job.pattern.equals(buffer.getPatternInventory().getStackInSlot(slot).save(new CompoundTag()))) {
            state.message = "gtl_enhancedcore.diagnostic.iv_pattern_snapshot";
            IvTaskLog.event(buffer, job, "EDIT_PATTERN", "SNAPSHOT_RETAINED", "reason", state.message);
            buffer.markDirty();
        }
    }
    @Inject(method = "cutToTag", at = @At("HEAD"), cancellable = true)
    private void iv$protectCut(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        var buffer = (MEPatternBufferPartMachine)(Object)this;
        if (!IvBufferTransfer.allowCut(buffer)) {
            cir.setReturnValue(tag);
        }
    }
    @Inject(method = "cutToTag", at = @At("RETURN"))
    private void iv$refreshAfterCut(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        IvBufferTransfer.cutCompleted((MEPatternBufferPartMachine)(Object)this, cir.getReturnValue());
    }
    @Inject(method = "pasteFromTag", at = @At("HEAD"), cancellable = true)
    private void iv$protectPaste(CompoundTag tag, CallbackInfoReturnable<Boolean> cir) {
        var buffer = (MEPatternBufferPartMachine)(Object)this;
        if (!IvBufferTransfer.allowPaste(buffer, tag)) {
            cir.setReturnValue(false);
        }
    }
}
