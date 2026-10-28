package com.gtl.enhancedcore.common.machine.galaxy;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.math.BigInteger;
import java.util.UUID;

/** BigInteger bridge from ADD recipe ticks to the linked station owner's wireless grid. */
public final class GalaxyWirelessEnergyHandler implements IWirelessNetworkEnergyHandler {
    private final WorkableElectricMultiblockMachine machine;

    private GalaxyWirelessEnergyHandler(WorkableElectricMultiblockMachine machine) {
        this.machine = machine;
    }

    public static IWirelessNetworkEnergyHandler forMachine(WorkableElectricMultiblockMachine machine) {
        return GalaxyLinks.linkedStation(machine) == null ? null : new GalaxyWirelessEnergyHandler(machine);
    }

    private UUID owner() {
        var station = GalaxyLinks.linkedStation(machine);
        return station == null ? null : station.getUserId();
    }

    @Override public boolean consumeEnergy(int amount) {
        return consumeEnergy(BigInteger.valueOf(amount));
    }

    @Override public boolean consumeEnergy(long amount) {
        return consumeEnergy(BigInteger.valueOf(amount));
    }

    @Override public boolean consumeEnergy(BigInteger amount) {
        // ADD passes a signed grid delta (wireless input recipes carry negative EU/t).
        if (amount == null || amount.signum() > 0) return false;
        UUID owner = owner();
        return owner != null && WirelessEnergyManager.addEUToGlobalEnergyMap(owner, amount, machine);
    }

    @Override public BigInteger getMaxAvailableEnergy() {
        UUID owner = owner();
        return owner == null ? BigInteger.ZERO : WirelessEnergyManager.getUserEU(owner).max(BigInteger.ZERO);
    }

    @Override public boolean isOnline() {
        return owner() != null;
    }
}
