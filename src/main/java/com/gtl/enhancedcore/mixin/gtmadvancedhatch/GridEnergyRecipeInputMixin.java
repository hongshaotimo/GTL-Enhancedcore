package com.gtl.enhancedcore.mixin.gtmadvancedhatch;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.GridEnergyRecipeInput;
import com.gtl.enhancedcore.common.recipe.GridRecipePayment;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both ordinary and adaptive grid inputs use these two container implementations. */
@Pseudo
@Mixin(targets = {
        "com.xingmot.gtmadvancedhatch.api.NoConsumeNotifiabbleEnergyContainer",
        "com.xingmot.gtmadvancedhatch.api.NoConsumeNotifiabbleLaserContainer"
}, remap = false)
public abstract class GridEnergyRecipeInputMixin extends NotifiableEnergyContainer implements GridEnergyRecipeInput {
    @Shadow(remap = false) public UUID owner_uuid;

    protected GridEnergyRecipeInputMixin(MetaMachine machine, long capacity, long inputVoltage,
                                        long inputAmperage, long outputVoltage, long outputAmperage) {
        super(machine, capacity, inputVoltage, inputAmperage, outputVoltage, outputAmperage);
    }

    @Override public boolean gridEnergyConnected() {
        return owner_uuid != null && getInputVoltage() > 0 && getInputAmperage() > 0;
    }

    @Override public BigInteger gridEnergyAvailable() {
        return gridEnergyConnected() ? WirelessEnergyManager.getUserEU(owner_uuid).max(BigInteger.ZERO) : BigInteger.ZERO;
    }

    @Override public boolean gridEnergyTransfer(BigInteger delta) {
        return owner_uuid != null && WirelessEnergyManager.addEUToGlobalEnergyMap(owner_uuid, delta, getMachine());
    }

    @Inject(method = "handleRecipeInner", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$payFromGrid(IO io, GTRecipe recipe, List<Long> left, String slotName,
                                        boolean simulate, CallbackInfoReturnable<List<Long>> cir) {
        if (io != IO.IN || owner_uuid == null) return;
        boolean paid = gridEnergyConnected() && GridRecipePayment.pay(left, simulate, this::gridEnergyAvailable,
                this::gridEnergyTransfer);
        // A failed grid debit must not fall back to a permanently full local cache.
        cir.setReturnValue(paid ? null : left);
    }
}
