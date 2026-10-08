package com.gtl.enhancedcore.common.recipe;

import com.google.gson.JsonElement;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

public final class RecipeInputAdmission {
    private record CatalystKey(RecipeCapability<?> capability, String slotName, String uiName, JsonElement predicate) {}
    private final Map<RecipeCapability<?>, List<Content>> consuming = new LinkedHashMap<>();
    private final Map<CatalystKey, Content> catalysts = new LinkedHashMap<>();

    public RecipeInputAdmission() {}

    public void include(IRecipeLogicMachine machine, GTRecipe recipe, boolean settleChances) {
        for (var entry : recipe.inputs.entrySet()) {
            var capability = entry.getKey();
            var contents = consuming.computeIfAbsent(capability, ignored -> new ArrayList<>());
            var chanced = new ArrayList<Content>();
            for (var content : entry.getValue()) {
                if (content.chance == 0 && content.maxChance > 0) {
                    var key = new CatalystKey(capability, content.slotName, content.uiName, predicate(capability, content.content));
                    catalysts.merge(key, content, (existing, incoming) -> amount(capability, incoming.content)
                            > amount(capability, existing.content) ? incoming : existing);
                } else if (!settleChances || !capability.doMatchInRecipe() || content.chance >= content.maxChance) {
                    contents.add(content);
                } else chanced.add(content);
            }
            if (chanced.isEmpty()) continue;
            var rolled = LongChanceLogic.OR.roll(chanced, recipe.getType().getChanceFunction(),
                    IGTRecipe.of(recipe).getEuTier(), machine.getChanceTier(), machine.getRecipeLogic().getChanceCaches().get(capability),
                    IGTRecipe.of(recipe).getRealParallels(), capability);
            if (rolled != null) for (var content : rolled)
                contents.add(new Content(content.content, 10000, 10000, 0, content.slotName, content.uiName));
        }
    }

    public void writeTo(Map<RecipeCapability<?>, List<Content>> target) {
        consuming.forEach((capability, contents) -> {
            if (!contents.isEmpty()) target.computeIfAbsent(capability, ignored -> new ArrayList<>()).addAll(contents);
        });
        catalysts.forEach((key, content) -> target.computeIfAbsent(key.capability(), ignored -> new ArrayList<>()).add(content));
    }

    private static <Value> JsonElement predicate(RecipeCapability<Value> capability, Object content) {
        if (capability == ItemRecipeCapability.CAP) {
            var ingredient = ItemRecipeCapability.CAP.of(content);
            while (ingredient instanceof SizedIngredient sized) ingredient = sized.getInner();
            return ingredient.toJson();
        }
        if (capability == FluidRecipeCapability.CAP) {
            var ingredient = FluidRecipeCapability.CAP.of(content).copy();
            ingredient.setAmount(1);
            return ingredient.toJson();
        }
        return capability.serializer.toJson(capability.of(content));
    }

    private static long amount(RecipeCapability<?> capability, Object content) {
        if (capability == ItemRecipeCapability.CAP) {
            var ingredient = ItemRecipeCapability.CAP.of(content);
            return ingredient instanceof LongIngredient large ? large.getActualAmount()
                    : ingredient instanceof SizedIngredient sized ? sized.getAmount() : 1;
        }
        if (capability == FluidRecipeCapability.CAP) return FluidRecipeCapability.CAP.of(content).getAmount();
        return content instanceof Number number ? number.longValue() : 1;
    }
}
