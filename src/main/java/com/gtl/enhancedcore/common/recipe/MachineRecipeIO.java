package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.ArrayList;
import java.util.List;

/** Carry remainders across handlers; callers own the recipe context and commit policy. */
public final class MachineRecipeIO {
    private MachineRecipeIO() {}

    @SuppressWarnings("unchecked")
    public static List<Ingredient> transfer(List<IRecipeHandler<?>> handlers, IO io, GTRecipe context,
                                             List<ItemStack> stacks, boolean simulate) {
        List<Ingredient> remaining = new ArrayList<>();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) remaining.add(SizedIngredient.create(stack.copy()));
        for (IRecipeHandler<?> handler : handlers) {
            if (remaining == null || remaining.isEmpty()) return List.of();
            remaining = ((IRecipeHandler<Ingredient>) handler).handleRecipe(io, context, remaining, null, simulate);
        }
        return remaining == null ? List.of() : remaining;
    }

    public static int remainingCount(List<Ingredient> remaining) {
        int count = 0;
        for (Ingredient ingredient : remaining) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) count = Math.addExact(count, stacks[0].getCount());
        }
        return count;
    }
}
