package com.gtl.enhancedcore.mixin.gtceu;

import com.google.common.base.Suppliers;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtl.enhancedcore.common.config.GalaxyConnectionConfig;
import com.gtl.enhancedcore.common.structure.GalaxyConnectionPatterns;
import com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;

/** Apply after native, addon and project factory setters, including cancelled setter injections. */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class GalaxyConnectionPatternMixin {
    @WrapMethod(method = "setPatternFactory")
    private void galaxy$inputPorts(Supplier<BlockPattern> factory, Operation<Void> original) {
        original.call(factory);
        var definition = (MultiblockMachineDefinition) (Object) this;
        if (!GalaxyConnectionConfig.allows(definition.getId())) return;
        var pattern = definition.getPatternFactory();
        Supplier<BlockPattern> extended = Suppliers.memoize(() -> GalaxyConnectionPatterns.extend(pattern.get()));
        ((MultiblockMachineDefinitionAccessor) this).gtlEnhancedcore$setPatternFactory(
                () -> GtlMegastructurePatterns.resolvingOriginalFactory() ? pattern.get() : extended.get());
    }
}
