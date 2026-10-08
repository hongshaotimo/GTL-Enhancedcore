package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gtl.enhancedcore.common.recipe.MachineDiagnostics;
import com.gtl.enhancedcore.common.recipe.AssemblyLineParallelDisplay;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class MachineDiagnosticDisplayMixin {
    @ModifyVariable(method = "setAdditionalDisplay", at = @At("HEAD"), argsOnly = true)
    private BiConsumer<IMultiController, List<Component>> enhanced$diagnostics(
            BiConsumer<IMultiController, List<Component>> original) {
        return (controller, lines) -> {
            if (original != null) original.accept(controller, lines);
            MachineDiagnostics.append(controller.self(), lines);
            AssemblyLineParallelDisplay.append(controller.self(), lines);
        };
    }
}
