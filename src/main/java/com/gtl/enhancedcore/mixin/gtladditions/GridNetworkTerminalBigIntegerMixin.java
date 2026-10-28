package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.GridNetworkTerminalPayment;
import com.gtl.enhancedcore.common.recipe.GridEnergyRecipeInput;
import com.gtladd.gtladditions.common.machine.multiblock.part.WirelessEnergyNetworkTerminalPartMachineBase;
import com.gtladd.gtladditions.common.machine.trait.NetworkEnergyContainer;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.gtlcore.gtlcore.utils.datastructure.Int128;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps wireless terminal recipe input accounting in the BigInteger network ledger. */
@Pseudo
@Mixin(value = NetworkEnergyContainer.class, remap = false)
public abstract class GridNetworkTerminalBigIntegerMixin implements GridEnergyRecipeInput {
    private static final BigInteger INT128_MAX = Int128.MAX_VALUE.toBigInteger();

    @Shadow private Int128 energyInputPerSec;

    @Shadow public abstract WirelessEnergyNetworkTerminalPartMachineBase getMachine();

    @Shadow public abstract IO getHandlerIO();

    @Override public boolean gridEnergyConnected() {
        return getHandlerIO() == IO.IN && getMachine().getUUID() != null;
    }

    @Override public boolean gridEnergyUnboundedPower() { return true; }

    @Override public BigInteger gridEnergyAvailable() {
        UUID owner = getMachine().getUUID();
        return getHandlerIO() == IO.IN && owner != null
                ? WirelessEnergyManager.getUserEU(owner).max(BigInteger.ZERO) : BigInteger.ZERO;
    }

    @Override public boolean gridEnergyTransfer(BigInteger delta) {
        UUID owner = getMachine().getUUID();
        if (getHandlerIO() != IO.IN || owner == null
                || !WirelessEnergyManager.addEUToGlobalEnergyMap(owner, delta, getMachine())) return false;
        // Count direct order payments too; a transaction refund reverses its input statistic.
        BigInteger tracked = energyInputPerSec.toBigInteger().subtract(delta).max(BigInteger.ZERO).min(INT128_MAX);
        energyInputPerSec.set(Int128.fromBigInteger(tracked));
        return true;
    }

    @Inject(method = "handleRecipeInner", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$handleBigIntegerNetworkInput(IO io, GTRecipe recipe, List<Long> left,
            String slotName, boolean simulate, CallbackInfoReturnable<List<Long>> cir) {
        if (io != IO.IN) return;

        UUID owner = getMachine().getUUID();
        if (owner == null) {
            cir.setReturnValue(left);
            return;
        }

        GridNetworkTerminalPayment.Payment payment = GridNetworkTerminalPayment.plan(
                left, WirelessEnergyManager.getUserEU(owner), simulate);
        if (!payment.valid()) {
            cir.setReturnValue(left);
            return;
        }

        if (!simulate && payment.withdrawn().signum() > 0) {
            boolean paid = WirelessEnergyManager.addEUToGlobalEnergyMap(
                    owner, payment.withdrawn().negate(), getMachine());
            if (!paid) {
                cir.setReturnValue(left);
                return;
            }
            BigInteger tracked = energyInputPerSec.toBigInteger().max(BigInteger.ZERO)
                    .add(payment.withdrawn()).min(INT128_MAX);
            energyInputPerSec.set(Int128.fromBigInteger(tracked));
        }
        cir.setReturnValue(payment.remaining());
    }

}
