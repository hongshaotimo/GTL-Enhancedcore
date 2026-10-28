package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyMutableModeAccess;
import org.gtlcore.gtlcore.api.recipe.BatchProcessing;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

/** ADD hides the batch tab using effective mode, but stellar links resolve only on the server. */
@Mixin(value = BatchProcessing.class, remap = false, priority = 1500)
public abstract class GalaxyBatchConfiguratorMixin {
    @WrapMethod(method = "canConfigureBatchProcessing")
    private static boolean galaxy$stableBatchTab(IRecipeLogicMachine machine, Operation<Boolean> original) {
        if (machine instanceof WorkableElectricMultiblockMachine electric && GalaxyLinks.allowed(electric)
                && machine.getRecipeLogic() instanceof GalaxyMutableModeAccess mode)
            return !mode.galaxy$nativeMultipleRecipeMode();
        return original.call(machine);
    }
}
