package com.gtl.enhancedcore.mixin.gtceu;

import com.google.common.base.Suppliers;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
import com.gtl.enhancedcore.common.structure.NativeStructureHollowing;
import com.gtl.enhancedcore.common.structure.PrimitiveCloudPorts;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep native geometry while sharing the cloud-port quota with the generated preview. */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class PrimitiveCloudPatternMixin {
    @WrapMethod(method = "setPatternFactory")
    private void primitive$cloudPort(Supplier<BlockPattern> factory, Operation<Void> original) {
        original.call(factory);
        var definition = (MultiblockMachineDefinition) (Object) this;
        if (!MachineOverrideConfig.current().enhanceOriginalMachines()
                || !PrimitiveCloudPorts.targets(definition.getId())) return;
        var nativeFactory = definition.getPatternFactory();
        ((MultiblockMachineDefinitionAccessor) this).gtlEnhancedcore$setPatternFactory(
                Suppliers.memoize(() -> PrimitiveCloudPorts.copy(nativeFactory.get())));
    }

    @Inject(method = "getMatchingShapes", at = @At("HEAD"), cancellable = true)
    private void primitive$matchingPreview(CallbackInfoReturnable<List<MultiblockShapeInfo>> cir) {
        var definition = (MultiblockMachineDefinition) (Object) this;
        if (!MachineOverrideConfig.current().enhanceOriginalMachines()
                || !PrimitiveCloudPorts.targets(definition.getId())) return;
        var pattern = definition.getPatternFactory().get();
        cir.setReturnValue(List.of(new MultiblockShapeInfo(pattern.getPreview(
                NativeStructureHollowing.minimumRepetitions(pattern.aisleRepetitions)))));
    }
}
