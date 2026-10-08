package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.*;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

/** Task-local matching. Does not invoke GTL's machine-wide recipe/slot cache. */
public final class IvRecipeInputs {
    private IvRecipeInputs() {}
    public static long itemAmount(Ingredient ingredient) {
        if (ingredient instanceof LongIngredient large) return large.getActualAmount();
        if (ingredient instanceof SizedIngredient sized) return sized.getAmount();
        ItemStack[] stacks = ingredient.getItems();
        return stacks.length == 0 ? 0 : stacks[0].getCount();
    }
    private static Predicate<AEKey> itemMatcher(Ingredient ingredient) {
        while (ingredient instanceof SizedIngredient sized) ingredient = sized.getInner();
        Ingredient predicate = ingredient;
        return key -> key instanceof AEItemKey item && predicate.test(item.toStack(1));
    }
    public static AEFluidKey fluidKey(FluidStack stack) {
        return stack.hasTag() ? AEFluidKey.of(stack.getFluid(), stack.getTag()) : AEFluidKey.of(stack.getFluid());
    }
    public static Map<AEKey, Long> plan(GTRecipe recipe, Map<AEKey, Long> input, Map<AEKey, Long> virtual, long count) {
        return plan(recipe.inputs, input, virtual, count);
    }
    public static Map<AEKey, Long> plan(Map<RecipeCapability<?>, List<Content>> inputs,
                                       Map<AEKey, Long> input, Map<AEKey, Long> virtual, long count) {
        List<LongAllocation.Supply<AEKey>> supplies = new ArrayList<>();
        input.forEach((key, amount) -> supplies.add(new LongAllocation.Supply<>(key, amount, true)));
        virtual.forEach((key, amount) -> supplies.add(new LongAllocation.Supply<>(key, amount, false)));
        List<LongAllocation.Need<AEKey>> needs = new ArrayList<>();
        for (var entry : inputs.entrySet()) for (Content content : entry.getValue()) {
            boolean consume = content.chance > 0;
            long amount;
            Predicate<AEKey> matches;
            if (entry.getKey() == ItemRecipeCapability.CAP) {
                Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
                amount = itemAmount(ingredient); matches = itemMatcher(ingredient);
            } else if (entry.getKey() == FluidRecipeCapability.CAP) {
                FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.content).copy();
                amount = ingredient.getAmount(); ingredient.setAmount(1);
                matches = key -> key instanceof AEFluidKey fluid && ingredient.test(FluidStack.create(fluid.getFluid(), 1, fluid.getTag()));
            } else throw new IllegalArgumentException("不支持的任务输入能力：" + entry.getKey().name);
            if (amount <= 0) throw new IllegalArgumentException("配方输入数量无效");
            needs.add(new LongAllocation.Need<>(matches, Math.multiplyExact(amount, consume ? count : 1), consume));
        }
        return LongAllocation.plan(supplies, needs);
    }
    public static Map<AEKey, Long> outputs(Map<RecipeCapability<?>, List<Content>> outputs) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (var entry : outputs.entrySet()) for (Content content : entry.getValue()) {
            AEKey key; long amount;
            if (entry.getKey() == ItemRecipeCapability.CAP) {
                Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
                ItemStack[] stacks = ingredient.getItems();
                if (stacks.length == 0 || stacks[0].isEmpty()) continue;
                key = AEItemKey.of(stacks[0]); amount = itemAmount(ingredient);
            } else if (entry.getKey() == FluidRecipeCapability.CAP) {
                FluidIngredient ingredient = FluidRecipeCapability.CAP.of(content.content);
                FluidStack[] stacks = ingredient.getStacks();
                if (stacks.length == 0 || stacks[0].isEmpty()) continue;
                key = fluidKey(stacks[0]); amount = ingredient.getAmount();
            } else throw new IllegalArgumentException("不支持的任务输出能力：" + entry.getKey().name);
            if (amount > 0) result.merge(key, amount, Math::addExact);
        }
        return result;
    }
    public static long maxParallel(GTRecipe recipe, Map<AEKey, Long> input, Map<AEKey, Long> virtual, long limit) {
        long low = 0, high = limit;
        while (low < high) {
            long mid = low + (high - low) / 2 + 1;
            boolean fits;
            try { fits = plan(recipe, input, virtual, mid) != null; }
            catch (ArithmeticException overflow) { fits = false; }
            if (fits) low = mid; else high = mid - 1;
        }
        return low;
    }
    public static boolean hasConsumedInputs(GTRecipe recipe) {
        return recipe.inputs.values().stream().flatMap(List::stream).anyMatch(content -> content.chance > 0);
    }
    /** Pattern output counts are AE promises, not recipe multipliers or guaranteed chance yields. */
    public static long patternOperations(GTRecipe recipe, Map<AEKey, Long> input, Map<AEKey, Long> virtual,
                                         Map<AEKey, Long> expected) {
        if (expected.isEmpty() || expected.values().stream().anyMatch(amount -> amount <= 0)) return 0;
        Map<AEKey, Long> possibleOutputs = outputs(recipe.outputs);
        if (!possibleOutputs.keySet().containsAll(expected.keySet())) return 0;
        if (hasConsumedInputs(recipe)) {
            // The pattern's input stack defines how many base recipe operations one pattern run represents.
            // This remains valid when the output stack was manually edited or contains chance alternatives.
            long limit = 0;
            for (long amount : input.values()) {
                if (amount < 0) throw new IllegalArgumentException("样板输入数量无效");
                limit = limit > Long.MAX_VALUE - amount ? Long.MAX_VALUE : limit + amount;
            }
            long operations = maxParallel(recipe, input, virtual, limit);
            return operations > 0 && plan(recipe, input, virtual, operations) != null ? operations : 0;
        }
        // Preserve finite sizing for genuinely input-free recipes; never interpret a reusable tool
        // or virtual catalyst as an unlimited processing budget.
        long scale = -1;
        for (var output : expected.entrySet()) {
            long base = possibleOutputs.get(output.getKey());
            if (output.getValue() % base != 0) return 0;
            long ratio = output.getValue() / base;
            if (scale >= 0 && scale != ratio) return 0;
            scale = ratio;
        }
        return scale > 0 && plan(recipe, input, virtual, scale) != null ? scale : 0;
    }
    public static void subtract(Map<AEKey, Long> inventory, Map<AEKey, Long> consumed) {
        // Validate the entire commit before touching any entry.
        consumed.forEach((key, count) -> { if (count < 0 || inventory.getOrDefault(key, 0L) < count) throw new IllegalStateException("任务库存已改变"); });
        consumed.forEach((key, count) -> {
            long left = inventory.get(key) - count;
            if (left == 0) inventory.remove(key); else inventory.put(key, left);
        });
    }
}
