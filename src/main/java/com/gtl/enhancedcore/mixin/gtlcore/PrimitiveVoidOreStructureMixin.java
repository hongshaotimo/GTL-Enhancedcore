package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import java.util.Arrays;
import org.gtlcore.gtlcore.common.data.machines.MultiBlockMachineB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps the original geometry while allowing the existing X input face to host a circuit. */
@Mixin(value = MultiBlockMachineB.class, remap = false)
public abstract class PrimitiveVoidOreStructureMixin {
    @ModifyArg(method = "lambda$static$12",
            at = @At(value = "INVOKE",
                    target = "Lcom/gregtechceu/gtceu/api/pattern/Predicates;abilities([Lcom/gregtechceu/gtceu/api/machine/multiblock/PartAbility;)Lcom/gregtechceu/gtceu/api/pattern/TraceabilityPredicate;",
                    ordinal = 1, remap = false),
            index = 0, require = 1, remap = false)
    private static PartAbility[] enhancedcore$inputItems(PartAbility[] abilities) {
        for (PartAbility ability : abilities) if (ability == PartAbility.IMPORT_ITEMS) return abilities;
        PartAbility[] result = Arrays.copyOf(abilities, abilities.length + 1);
        result[result.length - 1] = PartAbility.IMPORT_ITEMS;
        return result;
    }
}
