package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import java.util.List;

/** Read-only view of the physical handlers folded into GTCEu's aggregate energy container. */
public interface EnergyContainerListView {
    List<? extends IEnergyContainer> enhancedcore$energyContainers();
}
