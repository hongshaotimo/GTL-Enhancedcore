package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gtl.enhancedcore.common.recipe.iv.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import net.minecraft.nbt.CompoundTag;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEIOPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = MEPatternBufferPartMachineBase.class, remap = false)
public abstract class IvBufferLifecycleMixin extends MEIOPartMachine implements IvBufferAccess {
    @Unique private IvBufferState iv$state;
    @Unique @com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced private boolean iv$dedicatedDisplay;

    protected IvBufferLifecycleMixin(IMachineBlockEntity holder, IO io) { super(holder, io); }

    @Override public IvBufferState iv$getState() {
        if (iv$state == null) iv$state = new IvBufferState();
        return iv$state;
    }
    @Override public boolean iv$isDedicatedDisplay() { return iv$dedicatedDisplay; }
    @Override public void iv$syncDedicated(boolean dedicated) { iv$dedicatedDisplay = dedicated; }

    @Override public boolean canShared() {
        return !IvBuffers.isolated((MEPatternBufferPartMachineBase)(Object)this) && super.canShared();
    }
    @Override public boolean isFormed() {
        var state = IvBuffers.state(this);
        return state != null && (!state.jobs.isEmpty() || !state.healthy()) || super.isFormed();
    }
    @Override public boolean hasController(net.minecraft.core.BlockPos pos) {
        var state = IvBuffers.state(this);
        if (state != null && (!state.jobs.isEmpty() || !state.healthy())) {
            var candidate = com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(getLevel(), pos);
            return candidate instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine machine
                    && IvBuffers.targetController(machine) && state.owner.equals(IvBuffers.ownerKey(machine));
        }
        return super.hasController(pos);
    }
    @Inject(method = "attachConfigurators", at = @At("TAIL"))
    private void iv$taskPanel(com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel panel, CallbackInfo ci) {
        var buffer = (MEPatternBufferPartMachineBase)(Object)this;
        if (IvBuffers.isolated(buffer)) panel.attachConfigurators(new IvBufferConfigurator(buffer));
    }
    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void iv$accept(IPatternDetails pattern, KeyCounter[] input, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof MEPatternBufferPartMachine buffer && IvBuffers.isolated(buffer))
            cir.setReturnValue(IvBuffers.push(buffer, pattern, input));
    }
    @Inject(method = {"getActiveSlots", "getActiveAndUnCachedSlots"}, at = @At("HEAD"), cancellable = true)
    private void iv$hidePrivateInputs(CallbackInfoReturnable<int[]> cir) {
        if (IvBuffers.isolated((MEPatternBufferPartMachineBase)(Object)this)) cir.setReturnValue(new int[0]);
    }
    @Inject(method = "saveCustomPersistedData", at = @At("TAIL"))
    private void iv$save(CompoundTag tag, boolean forDrop, CallbackInfo ci) {
        IvBufferState state = IvBuffers.state(this);
        if (state != null && state.dedicated()) {
            tag.put(IvBuffers.SAVE_KEY, state.save());
            IvTaskLog.event((MEPatternBufferPartMachineBase)(Object)this, null, "SAVE", "OK", "tasks", state.jobs.size(), "forDrop", forDrop);
        }
    }
    @Inject(method = "loadCustomPersistedData", at = @At("TAIL"))
    private void iv$load(CompoundTag tag, CallbackInfo ci) {
        IvBufferState state = IvBuffers.state(this);
        if (state != null && tag.contains(IvBuffers.SAVE_KEY)) {
            state.load(tag.getCompound(IvBuffers.SAVE_KEY));
            ((IvBufferAccess)this).iv$syncDedicated(state.dedicated());
            IvTaskLog.event((MEPatternBufferPartMachineBase)(Object)this, null, "LOAD", state.healthy() ? "OK" : "QUARANTINED", "tasks", state.jobs.size(), "message", state.message);
            for (IvJob job : state.jobs) IvTaskLog.event((MEPatternBufferPartMachineBase)(Object)this, job, "RESTORE_TASK", "OK", "remaining", job.remaining,
                    "parallel", job.parallel, "elapsed", job.elapsed, "energyLeft", job.energyLeft.toString(), "inventory", IvTaskLog.stock(job.inventory), "pending", IvTaskLog.stock(job.pending), "completion", IvTaskLog.stock(job.completion),
                    "totalOperations",job.totalOperations,"completedOperations",job.completedOperations,"deliveredOperations",job.deliveredOperations,"recoveredTail",job.recoveredTail,
                    "cancelledOperations",job.cancelledOperations,"refunds",IvTaskLog.stock(job.refunds),"accepting",state.accepting);
        }
    }
    @Inject(method = "update", at = @At("HEAD"))
    private void iv$refresh(CallbackInfo ci) {
        IvBuffers.refresh((MEPatternBufferPartMachineBase)(Object)this);
        IvCancellation.returnStock((MEPatternBufferPartMachineBase)(Object)this);
        if(IvBuffers.compatible(this) && (Object)this instanceof IvBufferAccess access
                && !((MEPatternBufferPartMachineBase)(Object)this).isRemote())
            access.iv$syncDedicated(IvBuffers.isolated((MEPatternBufferPartMachineBase)(Object)this));
    }
    @Inject(method = "refundAll", at = @At("HEAD"), cancellable = true)
    private void iv$protectRefund(com.lowdragmc.lowdraglib.gui.util.ClickData click, CallbackInfo ci) {
        var buffer = (MEPatternBufferPartMachineBase)(Object)this;
        IvBufferState state = IvBuffers.state(buffer);
        if (state != null && (!state.jobs.isEmpty() || !state.healthy())) {
            if(!click.isRemote)IvCancellation.cancel(buffer,-1);
            ci.cancel();
        }
    }
}
