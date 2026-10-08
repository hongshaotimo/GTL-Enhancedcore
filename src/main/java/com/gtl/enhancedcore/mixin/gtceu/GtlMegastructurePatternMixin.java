package com.gtl.enhancedcore.mixin.gtceu;

import com.google.common.base.Suppliers;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns;
import com.gtl.enhancedcore.common.structure.SpaceElevatorMaintenance;
import java.util.List;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class GtlMegastructurePatternMixin {
    @ModifyVariable(method = "setPatternFactory", at = @At("HEAD"), argsOnly = true)
    private Supplier<BlockPattern> enhanced$replaceGeometry(Supplier<BlockPattern> original) {
        var definition = (MultiblockMachineDefinition) (Object) this;
        if (SpaceElevatorMaintenance.matches(definition.getId()))
            return Suppliers.memoize(() -> SpaceElevatorMaintenance.forbidden(original.get()));
        if (!GtlMegastructurePatterns.targets(definition.getId())) return original;
        return GtlMegastructurePatterns.wrapFactory(definition.getId(), original);
    }

    @ModifyVariable(method = "setShapes", at = @At("HEAD"), argsOnly = true)
    private Supplier<List<MultiblockShapeInfo>> enhanced$useMatchingPreview(Supplier<List<MultiblockShapeInfo>> original) {
        var definition = (MultiblockMachineDefinition) (Object) this;
        if (!GtlMegastructurePatterns.targets(definition.getId())
                && !SpaceElevatorMaintenance.matches(definition.getId())) return original;
        return Suppliers.memoize(() -> {
            var pattern = definition.getPatternFactory().get();
            int[] repetitions = new int[pattern.aisleRepetitions.length];
            java.util.Arrays.fill(repetitions, 1);
            return List.of(new MultiblockShapeInfo(pattern.getPreview(repetitions)));
        });
    }
}
