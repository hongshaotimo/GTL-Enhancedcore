package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Uncached execution must pay the same pattern slot that satisfies its selectors. */
@Mixin(value = MEPatternRecipeHandlePart.class, remap = false)
public abstract class PatternInputPaymentMixin {
    @Shadow public abstract IMERecipeHandler<?, ?>[] getMERecipeHandlers();

    @WrapMethod(method = "handleRecipe")
    private int enhancedcore$paySelectedSlot(GTRecipe recipe,
            Reference2ObjectMap<RecipeCapability<?>, List<Object>> contents,
            boolean simulate, boolean cache, Operation<Integer> original) {
        if (simulate || cache) return original.call(recipe, contents, simulate, cache);

        var selection = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
        contents.forEach((capability, values) -> {
            if (!values.isEmpty()) selection.put(capability, new ArrayList<>(values));
        });
        // Actual contents already contain the probability roll and omit catalysts.
        // Restore only constraints, never unrolled inputs or shared fluid demands.
        recipe.inputs.forEach((capability, values) -> {
            for (var content : values) {
                if (content.chance != 0 || content.maxChance <= 0) continue;
                boolean circuit = capability == ItemRecipeCapability.CAP
                        && Arrays.stream(ItemRecipeCapability.CAP.of(content.content).getItems())
                                .anyMatch(IntCircuitBehaviour::isIntegratedCircuit);
                if (selection.containsKey(capability) || circuit)
                    selection.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(content.content);
            }
        });
        int slot = original.call(recipe, selection, true, false);
        if (slot < 0) return -1;

        var handlers = getMERecipeHandlers(); // Core contract: item handler, fluid handler.
        RecipeCapability<?>[] capabilities = {ItemRecipeCapability.CAP, FluidRecipeCapability.CAP};
        for (int index = 0; index < capabilities.length; index++) {
            var values = contents.get(capabilities[index]);
            if (values == null || values.isEmpty()) continue;
            handlers[index].initMEHandleContents(recipe, values, false);
            if (!handlers[index].meHandleRecipe(recipe, false, slot)) return -1;
        }
        return slot;
    }
}
