package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/** Connects controllers which pay EU directly to the same grid source used by recipe handlers. */
public final class GridEnergyInputs {
    private GridEnergyInputs() {}

    public static GridEnergyRecipeInput find(WorkableElectricMultiblockMachine machine) {
        return findIn(energyInputs(machine));
    }

    public static List<IEnergyContainer> energyInputs(WorkableElectricMultiblockMachine machine) {
        var result = new ArrayList<IEnergyContainer>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var direction : List.of(IO.IN, IO.BOTH)) {
            var handlers = machine.getCapabilitiesProxy().get(direction, EURecipeCapability.CAP);
            if (handlers != null) for (var handler : handlers) collect(handler, result, seen);
        }
        return List.copyOf(result);
    }

    private static void collect(Object handler, List<IEnergyContainer> result, Set<Object> seen) {
        if (handler == null || !seen.add(handler)) return;
        if (handler instanceof EnergyContainerListView aggregate) {
            for (IEnergyContainer child : aggregate.enhancedcore$energyContainers()) collect(child, result, seen);
        } else if (handler instanceof IEnergyContainer energy) {
            result.add(energy);
        }
    }

    public static GridEnergyRecipeInput findIn(Iterable<?> handlers) {
        GridEnergyRecipeInput best = null;
        BigInteger available = BigInteger.ZERO;
        var flattened = new ArrayList<IEnergyContainer>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object handler : handlers) collect(handler, flattened, seen);
        for (var input : flattened) if (input instanceof GridEnergyRecipeInput grid && grid.gridEnergyConnected()) {
            BigInteger candidate = grid.gridEnergyAvailable();
            if (best == null || candidate.compareTo(available) > 0
                    || candidate.equals(available) && grid.gridEnergyUnboundedPower() && !best.gridEnergyUnboundedPower()) {
                best = grid;
                available = candidate;
            }
        }
        return best;
    }

    public static long stored(WorkableElectricMultiblockMachine machine) {
        var grid = find(machine);
        if (grid != null) return GridRecipePayment.availableAsLong(grid.gridEnergyAvailable());
        var energy = machine.getEnergyContainer();
        return energy == null ? 0 : Math.max(0, energy.getEnergyStored());
    }

    public static long power(IEnergyContainer energy) {
        if (energy instanceof GridEnergyRecipeInput grid && grid.gridEnergyConnected()) return Long.MAX_VALUE;
        return RecipePowerBudget.power(energy.getInputVoltage(), energy.getInputAmperage());
    }

    public static long remove(IEnergyContainer energy, GridEnergyRecipeInput grid, long amount) {
        if (amount == 0) return 0;
        return grid == null ? energy.removeEnergy(amount) : grid.gridEnergyPay(amount) ? amount : 0;
    }

    /** Refund to the captured payment source, even if another hatch now advertises more energy. */
    public static long refund(IEnergyContainer energy, GridEnergyRecipeInput grid, long amount) {
        return grid == null ? energy.addEnergy(amount)
                : grid.gridEnergyTransfer(BigInteger.valueOf(amount)) ? amount : 0;
    }
}
