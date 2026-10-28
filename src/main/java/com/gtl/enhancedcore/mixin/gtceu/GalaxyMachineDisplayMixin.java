package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineDisplay;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Capacity projection remains available when original-machine enhancements are disabled. */
@Mixin(value = WorkableElectricMultiblockMachine.class, remap = false)
public abstract class GalaxyMachineDisplayMixin {
    // The virtual addDisplayText callback may be completely overridden by GTLCore/ADD subclasses.
    // Project only after it returns, for both initial text and subsequent server updates.
    @ModifyArg(method = "createUIWidget", at = @At(value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib/gui/widget/ComponentPanelWidget;<init>(IILjava/util/function/Consumer;)V"),
            index = 2, require = 1, allow = 1)
    private java.util.function.Consumer<List<Component>> iv$initialDisplay(
            java.util.function.Consumer<List<Component>> upstream) {
        return IvMachineDisplay.wrap((WorkableElectricMultiblockMachine)(Object)this, upstream);
    }

    @ModifyArg(method = "createUIWidget", at = @At(value = "INVOKE",
            target = "Lcom/lowdragmc/lowdraglib/gui/widget/ComponentPanelWidget;textSupplier(Ljava/util/function/Consumer;)Lcom/lowdragmc/lowdraglib/gui/widget/ComponentPanelWidget;"),
            index = 0, require = 1, allow = 1)
    private java.util.function.Consumer<List<Component>> iv$updatedDisplay(
            java.util.function.Consumer<List<Component>> upstream) {
        return IvMachineDisplay.wrap((WorkableElectricMultiblockMachine)(Object)this, upstream);
    }
}
