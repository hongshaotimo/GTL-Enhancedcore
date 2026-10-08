package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gtl.enhancedcore.common.machine.HyperstructuralChemicalDistorterMachine;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

/** Same-recipe lanes, with native material, output and power limits applied once. */
public final class SingleRecipeParallel {
    private SingleRecipeParallel() {}

    public static boolean supports(Object machine) {
        return machine instanceof HyperstructuralChemicalDistorterMachine;
    }

    public static long lanes(IThreadModifierMachine machine) {
        return 1L + Math.max(0, machine.getAdditionalThread());
    }

    public static int limit(WorkableElectricMultiblockMachine machine, int base) {
        var hatch = ((IRecipeCapabilityMachine) machine).getParallelHatch();
        long total = (long) Math.max(1, base) * (hatch == null ? 1 : Math.max(1, hatch.getCurrentParallel()));
        // Clamp before each multiplication; the engine can itself report Integer.MAX_VALUE.
        total = Math.min(Integer.MAX_VALUE, total);
        total *= lanes((IThreadModifierMachine) machine);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    public static GTRecipe apply(WorkableElectricMultiblockMachine machine, GTRecipe recipe, int base) {
        var parallel = ParallelLogic.applyParallel(machine, recipe.copy(), limit(machine, base), false);
        // OCResult.parallel belongs to subtick overclocking, not this already-applied multiplier.
        return parallel.getSecond() > 0 ? parallel.getFirst() : null;
    }

    public static long current(WorkableElectricMultiblockMachine machine) {
        var logic = machine.getRecipeLogic();
        var recipe = logic.getLastRecipe();
        return !machine.isFormed() || logic.isIdle() || logic.getDuration() <= 0 || recipe == null
                ? 0 : Math.max(1, IGTRecipe.of(recipe).getRealParallels());
    }

    public static void append(WorkableElectricMultiblockMachine machine, List<Component> lines) {
        if (!machine.isFormed()) return;
        lines.add(Component.translatable("gtl_enhancedcore.parallel.same_recipe_lanes",
                lanes((IThreadModifierMachine) machine)).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("gtl_enhancedcore.parallel.current", current(machine))
                .withStyle(ChatFormatting.GREEN));
    }
}
