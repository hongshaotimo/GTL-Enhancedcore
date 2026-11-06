package com.gtl.enhancedcore.common.recipe.iv;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.math.BigInteger;
import java.util.UUID;

/** The wireless bottle pays its surcharge separately from the order's ordinary work cost. */
public final class IvTimeAcceleration {
    private IvTimeAcceleration() {}

    public static boolean accelerate(RecipeLogic logic, UUID player) {
        if (logic.getMachine().isRemote() || !IvMachineScope.crossRecipeEnabled(logic.getMachine())
                || !logic.isWorkingEnabled() || !logic.isWorking()) return false;
        int steps = IvTimeAccelerationBudget.steps(logic.getDuration(), logic.getProgress());
        return IvTimeAccelerationBudget.run(steps, () -> cost(logic),
                amount -> WirelessEnergyManager.addEUToGlobalEnergyMap(player, amount.negate(), logic.getMachine()),
                amount -> WirelessEnergyManager.addEUToGlobalEnergyMap(player, amount, logic.getMachine()),
                () -> logic instanceof IvRecipeLogic own ? own.accelerateTick()
                        : logic instanceof IvNativeAccess nativeAccess && nativeAccess.iv$engine().accelerateTick());
    }

    private static BigInteger cost(RecipeLogic logic) {
        if (!logic.isWorking() || !(logic.getMachine() instanceof WorkableElectricMultiblockMachine machine))
            return BigInteger.ZERO;
        BigInteger eut = BigInteger.ZERO;
        // Native wireless orders deliberately publish physical EU/t=0; use their real wide ledger.
        for (var buffer : IvBuffers.collect(machine)) {
            var state = IvBuffers.state(buffer);
            if (state == null) continue;
            for (var job : state.jobs) {
                if (job.active() && !job.halted)
                    eut = eut.add((job.nativeWireless ? job.nativeWirelessEUt : BigInteger.valueOf(job.eut))
                            .max(BigInteger.ZERO));
            }
        }
        return eut.multiply(BigInteger.valueOf(8));
    }
}
