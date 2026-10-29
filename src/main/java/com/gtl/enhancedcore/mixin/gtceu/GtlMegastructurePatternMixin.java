package com.gtl.enhancedcore.mixin.gtceu;

import com.google.common.base.Suppliers;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo;
import com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns;
import com.gtl.enhancedcore.common.structure.SpaceElevatorMaintenance;
import com.gtl.enhancedcore.common.structure.NativeIsolationPatterns;
import com.gtl.enhancedcore.common.structure.NativeStructureHollowing;
import com.gtl.enhancedcore.common.structure.RetiredLightHunter;
import com.gtl.enhancedcore.common.config.MachineOverrideConfig;
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
        boolean enhance = MachineOverrideConfig.current().enhanceOriginalMachines();
        boolean replace = MachineOverrideConfig.current().replaceOriginalStructures();
        if (enhance && SpaceElevatorMaintenance.matches(definition.getId()))
            return Suppliers.memoize(() -> {
                var pattern = SpaceElevatorMaintenance.forbidden(original.get());
                return replace ? NativeStructureHollowing.optimize(definition.getId(), pattern) : pattern;
            });
        if (enhance && (NativeIsolationPatterns.targets(definition.getId())
                || !replace && com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(definition.getId())))
            return Suppliers.memoize(() -> {
                var pattern = NativeIsolationPatterns.restrict(definition.getId(), original.get());
                return replace ? NativeStructureHollowing.optimize(definition.getId(), pattern) : pattern;
            });
        if (!replace) return original;
        if (GtlMegastructurePatterns.targets(definition.getId()))
            return GtlMegastructurePatterns.wrapFactory(definition.getId(), original);
        return Suppliers.memoize(() -> NativeStructureHollowing.optimize(definition.getId(), original.get()));
    }

    @ModifyVariable(method = "setShapes", at = @At("HEAD"), argsOnly = true)
    private Supplier<List<MultiblockShapeInfo>> enhanced$useMatchingPreview(Supplier<List<MultiblockShapeInfo>> original) {
        var definition = (MultiblockMachineDefinition) (Object) this;
        boolean replace = MachineOverrideConfig.current().replaceOriginalStructures();
        boolean enhance = MachineOverrideConfig.current().enhanceOriginalMachines();
        if (RetiredLightHunter.contains(definition.getId())
                || !(replace && GtlMegastructurePatterns.targets(definition.getId())
                        || enhance && SpaceElevatorMaintenance.matches(definition.getId()))) return original;
        // Ability patches and hidden-cell optimization retain native geometry. Their
        // supplied tier/length variants (or the native repetition DFS) must survive.
        // Only a replaced blueprint needs a newly generated matching shape.
        return Suppliers.memoize(() -> {
            var pattern = definition.getPatternFactory().get();
            int[] repetitions = NativeStructureHollowing.minimumRepetitions(pattern.aisleRepetitions);
            return List.of(new MultiblockShapeInfo(pattern.getPreview(repetitions)));
        });
    }
}
