package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import net.minecraft.core.BlockPos;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.SuprachronalAssemblyLineMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.SuprachronalAssemblyLineModuleMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SuprachronalAssemblyLineModuleMachine.class, remap = false)
public abstract class SuprachronalAssemblyLineModuleRemovalMixin
        implements IModularMachineModule<SuprachronalAssemblyLineMachine,
                SuprachronalAssemblyLineModuleMachine>, IRecipeLogicMachine {
    @Shadow(remap = false)
    private SuprachronalAssemblyLineMachine host;

    @Shadow(remap = false)
    private BlockPos hostPosition;

    @Override
    public void connectToHost(SuprachronalAssemblyLineMachine controller) {
        enhanced$detachLegacyConnection();
    }

    @Override
    public boolean findAndConnectToHost() {
        enhanced$detachLegacyConnection();
        return false;
    }

    @Override
    public boolean isValidHost(MetaMachine controller) {
        return false;
    }

    @Override
    public boolean isConnectedToHost() {
        enhanced$detachLegacyConnection();
        return false;
    }

    @Override
    public boolean isRecipeLogicAvailable() {
        enhanced$detachLegacyConnection();
        return false;
    }

    @Inject(method = {
            "getHost()Lorg/gtlcore/gtlcore/common/machine/multiblock/electric/SuprachronalAssemblyLineMachine;",
            "getHostPosition"
    }, at = @At("HEAD"))
    private void enhanced$clearLegacyHostReads(CallbackInfoReturnable<?> callback) {
        enhanced$detachLegacyConnection();
    }

    @Inject(method = "setHost(Lorg/gtlcore/gtlcore/common/machine/multiblock/electric/SuprachronalAssemblyLineMachine;)V",
            at = @At("HEAD"), cancellable = true)
    private void enhanced$rejectHostAssignment(SuprachronalAssemblyLineMachine controller, CallbackInfo callback) {
        if (controller != null) {
            enhanced$detachLegacyConnection();
            callback.cancel();
        }
    }

    @Inject(method = "getHostScanPositions", at = @At("HEAD"), cancellable = true)
    private void enhanced$removeHostScanPositions(CallbackInfoReturnable<BlockPos[]> callback) {
        callback.setReturnValue(new BlockPos[0]);
    }

    @Inject(method = "getParallel", at = @At("HEAD"), cancellable = true)
    private void enhanced$removeSharedParallel(CallbackInfoReturnable<Integer> callback) {
        enhanced$detachLegacyConnection();
        callback.setReturnValue(0);
    }

    @Unique
    private void enhanced$detachLegacyConnection() {
        if (host != null || hostPosition != null) {
            ((SuprachronalAssemblyLineModuleMachine) (Object) this).removeFromHost(host);
        }
    }
}
