package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gtl.enhancedcore.common.recipe.EnergyContainerListView;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EnergyContainerList.class, remap = false)
public interface EnergyContainerListAccessor extends EnergyContainerListView {
    @Override
    @Accessor("energyContainerList")
    List<? extends IEnergyContainer> enhancedcore$energyContainers();
}
