package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

/** Invoke run() after Forge registrations/Mixins; no world, account or inventory is modified. */
public final class StellarInputRollChecks {
    private StellarInputRollChecks() {}

    public static Map<String, Object> run() {
        long parallel = 10_000;
        var scaled = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "input_roll_probe"),
                GTRecipeTypes.WIREMILL_RECIPES).duration(20).buildRawRecipe();
        var ingredient = LongIngredient.create(Ingredient.of(Items.APPLE), parallel);
        var content = new Content(ingredient, 1, 10_000, 0, null, null);
        scaled.inputs.put(ItemRecipeCapability.CAP, List.of(content));
        IGTRecipe.of(scaled).setRealParallels(parallel);

        var raw = scaled.copy();
        require(IGTRecipe.of(raw).getRealParallels() == 1, "Upstream copy loses realParallels");
        var inputRoll = scaled.copy();
        RecipeExtensionCopier.copy(scaled, inputRoll);
        require(IGTRecipe.of(inputRoll).getRealParallels() == parallel, "Input copy preserves realParallels");

        // The cache fixes the residual chance to zero. With an exact 10,000-operation
        // window, chance 1/10,000 must consume exactly one item, independent of RNG.
        var cache = new Object2IntOpenHashMap<Object>();
        cache.put(ingredient, 0);
        var rolled = LongChanceLogic.OR.roll(List.of(content), ChanceBoostFunction.NONE, 0, 0,
                cache, IGTRecipe.of(inputRoll).getRealParallels(), ItemRecipeCapability.CAP);
        require(rolled != null, "Deterministic input roll returns its exact expected item");
        var output = IvRecipeInputs.outputs(Map.of(ItemRecipeCapability.CAP, rolled));
        require(output.equals(Map.of(AEItemKey.of(Items.APPLE), 1L)), "10,000 operations consume one item");

        cache.put(ingredient, 0);
        var broken = LongChanceLogic.OR.roll(List.of(content), ChanceBoostFunction.NONE, 0, 0,
                cache, IGTRecipe.of(raw).getRealParallels(), ItemRecipeCapability.CAP);
        require(broken == null || broken.isEmpty(), "Lost parallel metadata reproduces incorrect zero consumption");
        require(IvRecipeInputs.itemAmount(ingredient) == parallel
                && IGTRecipe.of(scaled).getRealParallels() == parallel, "Source recipe remains unchanged");
        return Map.of("status", "PASS", "parallel", parallel, "chance", "1/10000",
                "expectedConsumed", 1, "actualConsumed", output.get(AEItemKey.of(Items.APPLE)),
                "rawCopyParallels", IGTRecipe.of(raw).getRealParallels(),
                "fixedCopyParallels", IGTRecipe.of(inputRoll).getRealParallels());
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
