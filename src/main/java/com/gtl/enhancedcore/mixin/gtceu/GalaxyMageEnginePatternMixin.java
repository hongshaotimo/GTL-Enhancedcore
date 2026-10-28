package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyEngineSupport;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wrap the final factory getter so upstream geometry and project replacements both receive it. */
@Mixin(value = MultiblockMachineDefinition.class, remap = false, priority = 900)
public abstract class GalaxyMageEnginePatternMixin {
    @org.spongepowered.asm.mixin.Unique private Supplier<BlockPattern> galaxy$rawFactory;
    @org.spongepowered.asm.mixin.Unique private Supplier<BlockPattern> galaxy$engineFactory;
    @Inject(method = "getPatternFactory", at = @At("RETURN"), cancellable = true)
    private void galaxy$mageEngine(CallbackInfoReturnable<Supplier<BlockPattern>> cir) {
        var definition = (MultiblockMachineDefinition)(Object)this;
        if (!definition.getId().toString().equals("gtceu:mage_assembler") || cir.getReturnValue() == null) return;
        var original = cir.getReturnValue();
        if (galaxy$rawFactory != original) {
            galaxy$rawFactory = original;
            galaxy$engineFactory = com.google.common.base.Suppliers.memoize(() -> GalaxyEngineSupport.addMageEngine(original.get()));
        }
        cir.setReturnValue(galaxy$engineFactory);
    }
}
