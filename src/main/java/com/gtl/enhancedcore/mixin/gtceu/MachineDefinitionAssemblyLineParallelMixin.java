package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gtl.enhancedcore.common.recipe.MachineRecipeModifiers;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allocates parallel power before upstream overclocking and retains the fixed cap afterwards. */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionAssemblyLineParallelMixin {
    @Unique private RecipeModifier enhanced$assemblyBase;
    @Unique private RecipeModifier[] enhanced$assemblyElements;
    @Unique private RecipeModifier enhanced$assemblyCombined;

    @Inject(method = "getRecipeModifier", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhanced$assemblyParallel(CallbackInfoReturnable<RecipeModifier> cir) {
        var id = ((MachineDefinition) (Object) this).getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !"assembly_line".equals(id.getPath())) return;
        RecipeModifier base = cir.getReturnValue();
        if (base == null) return;
        RecipeModifier[] elements = base instanceof RecipeModifierList list
                ? ((RecipeModifierListAccessor) list).gtlEnhancedcore$getModifiers() : null;
        if (enhanced$assemblyBase == base && enhanced$assemblyCombined != null
                && Arrays.equals(enhanced$assemblyElements, elements)) {
            cir.setReturnValue(enhanced$assemblyCombined);
            return;
        }
        // One flat list, with power-aware parallel admission before OC and no second multiplier after it.
        RecipeModifier[] original = elements == null ? new RecipeModifier[]{base} : elements;
        RecipeModifier[] combined = new RecipeModifier[original.length + 2];
        combined[0] = MachineRecipeModifiers::assemblyLine;
        System.arraycopy(original, 0, combined, 1, original.length);
        combined[combined.length - 1] = MachineRecipeModifiers::assemblyLineAfterOverclock;
        enhanced$assemblyBase = base;
        enhanced$assemblyElements = elements == null ? null : elements.clone();
        enhanced$assemblyCombined = new RecipeModifierList(combined);
        cir.setReturnValue(enhanced$assemblyCombined);
    }
}
