package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gtl.enhancedcore.common.structure.RetiredLightHunter;
import java.util.List;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class RetiredLightHunterDefinitionMixin {
    @ModifyVariable(method = "setPatternFactory", at = @At("HEAD"), argsOnly = true, require = 1)
    private Supplier<BlockPattern> retired$discardPattern(Supplier<BlockPattern> original) {
        var definition = (MultiblockMachineDefinition)(Object)this;
        return RetiredLightHunter.contains(definition.getId()) ? () -> RetiredLightHunter.pattern(definition) : original;
    }
    @ModifyVariable(method = "setShapes", at = @At("HEAD"), argsOnly = true, require = 1)
    private Supplier<List<MultiblockShapeInfo>> retired$discardPreview(Supplier<List<MultiblockShapeInfo>> original) {
        return RetiredLightHunter.contains(((MultiblockMachineDefinition)(Object)this).getId()) ? List::of : original;
    }
    @Inject(method = "getMatchingShapes", at = @At("HEAD"), cancellable = true, require = 1)
    private void retired$noPreview(CallbackInfoReturnable<List<MultiblockShapeInfo>> cir) {
        if (RetiredLightHunter.contains(((MultiblockMachineDefinition)(Object)this).getId())) cir.setReturnValue(List.of());
    }
}
