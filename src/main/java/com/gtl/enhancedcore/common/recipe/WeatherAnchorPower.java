package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.misc.EnergyContainerList;

/** A field tick is paid only at the required physical hatch voltage and full energy cost. */
public final class WeatherAnchorPower {
    private WeatherAnchorPower() {}

    public static boolean tryConsume(EnergyContainerList container, long minimumVoltage, long cost) {
        if (container == null || container.getHighestInputVoltage() < minimumVoltage
                || container.getEnergyStored() < cost) return false;
        long paid = container.removeEnergy(cost);
        if (paid == cost) return true;
        if (paid > 0) container.addEnergy(paid);
        return false;
    }
}
