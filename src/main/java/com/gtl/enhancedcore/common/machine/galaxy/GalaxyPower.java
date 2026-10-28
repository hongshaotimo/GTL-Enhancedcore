package com.gtl.enhancedcore.common.machine.galaxy;

import java.math.BigInteger;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

/** A linked target can spend its owner's full current grid balance per tick. */
public final class GalaxyPower {
    public static final int MAX_TIER = GTValues.MAX + 16;
    public static final String EU_TAG = "gtle_galaxy_eut";
    public static final String LEFT_TAG = "gtle_galaxy_energy_left";
    private GalaxyPower() {}
    public static BigInteger eut(GTRecipe recipe) {
        return recipe.data.contains(EU_TAG) ? new BigInteger(recipe.data.getString(EU_TAG))
                : BigInteger.valueOf(Math.max(0, RecipeHelper.getInputEUt(recipe)));
    }
    public static BigInteger payment(GTRecipe recipe) {
        return recipe.data.contains(LEFT_TAG) ? eut(recipe).min(new BigInteger(recipe.data.getString(LEFT_TAG))) : eut(recipe);
    }
    public static int tier(WorkableElectricMultiblockMachine machine, int original) {
        return GalaxyLinks.active(machine) ? MAX_TIER : original;
    }
    public static BigInteger availablePower(WorkableElectricMultiblockMachine machine) {
        var handler = GalaxyWirelessEnergyHandler.forMachine(machine);
        return handler == null ? BigInteger.ZERO : handler.getMaxAvailableEnergy().max(BigInteger.ZERO);
    }
    public static boolean ready(WorkableElectricMultiblockMachine machine, BigInteger amount) {
        return amount.signum() >= 0
                && availablePower(machine).compareTo(amount) >= 0;
    }
    public static boolean pay(WorkableElectricMultiblockMachine machine, BigInteger amount) {
        var handler = GalaxyWirelessEnergyHandler.forMachine(machine);
        return ready(machine, amount) && handler != null && handler.consumeEnergy(amount.negate());
    }
    public record Payment(java.util.UUID owner, WorkableElectricMultiblockMachine machine, BigInteger amount) {
        public void refund() {
            if (amount.signum() > 0 && !com.hepdd.gtmthings.api.misc.WirelessEnergyManager
                    .addEUToGlobalEnergyMap(owner, amount, machine))
                throw new IllegalStateException("Galaxy energy refund rejected");
        }
    }
    public static Payment withdraw(WorkableElectricMultiblockMachine machine, BigInteger amount) {
        var station = GalaxyLinks.linkedStation(machine);
        if (station == null || !ready(machine, amount)) return null;
        var owner = station.getUserId();
        return owner != null && com.hepdd.gtmthings.api.misc.WirelessEnergyManager
                .addEUToGlobalEnergyMap(owner, amount.negate(), machine) ? new Payment(owner, machine, amount) : null;
    }
}
