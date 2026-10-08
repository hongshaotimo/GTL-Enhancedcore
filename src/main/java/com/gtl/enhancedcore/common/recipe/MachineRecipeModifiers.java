package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.PlusFactoryMachine;
import java.util.List;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IAdvancedOCResult;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;

/** Processing policy kept separate from machine registration. */
public final class MachineRecipeModifiers {
    public static final int ASSEMBLY_LINE_PARALLEL = 64;
    public static final int NEUTRON_FACTORY_PARALLEL = 2048;
    private static final long NEUTRON_EU_PER_EVT = 2000L;
    private MachineRecipeModifiers() {}

    public static GTRecipe assemblyLine(MetaMachine machine, GTRecipe recipe, OCParams params, OCResult result) {
        // Reserve power for parallel recipes BEFORE overclocking consumes the voltage headroom.
        var parallel = com.gregtechceu.gtceu.common.data.GTRecipeModifiers
                .accurateParallel(machine, recipe, ASSEMBLY_LINE_PARALLEL, false);
        return parallel.getSecond() > 0 ? parallel.getFirst() : null;
    }

    public static GTRecipe assemblyLineAfterOverclock(MetaMachine machine, GTRecipe recipe,
                                                     OCParams params, OCResult result) {
        // Inputs/outputs/EU already include the actual parallel count. Preserve normal OC, but never
        // multiply the recipe again at the list tail or through sub-tick/time-window batching.
        int baseOcLevel = ((IAdvancedOCResult) (Object) result).getBaseOCLevel();
        result.init(result.getEut(), result.getDuration(), 1, 0L, baseOcLevel);
        IGTRecipe.of(recipe).setBatchProcessed(true);
        return recipe;
    }

    public static GTRecipe plusFactory(MetaMachine machine, GTRecipe recipe, OCParams params, OCResult result) {
        if (!(machine instanceof WorkableElectricMultiblockMachine workable)) return recipe;
        GTRecipe reduced = GTLRecipeModifiers.reduction(machine, recipe, 0.9, 0.6);
        if (reduced == null) return null;
        int parallel = machine instanceof PlusFactoryMachine plus ? plus.getMaxParallel() : PlusFactoryMachine.BASE_PARALLEL;
        long baseEUt = RecipeHelper.getInputEUt(reduced);
        long maxVoltage = machine instanceof PlusFactoryMachine plus ? plus.getOverclockVoltage() : Long.MAX_VALUE;
        if (baseEUt > 0L && maxVoltage > 0L) parallel = (int) Math.min(parallel, maxVoltage / baseEUt);
        parallel = Math.max(1, parallel);
        GTRecipe paralleled = com.gregtechceu.gtceu.common.data.GTRecipeModifiers
                .accurateParallel(machine, reduced, parallel, false).getFirst();
        if (paralleled == null) return null;
        return RecipeHelper.applyOverclock(OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK, paralleled,
                workable.getOverclockVoltage(), params, result);
    }

    public static GTRecipe maintenance(MetaMachine machine, GTRecipe recipe, OCParams params, OCResult result) {
        if (!(machine instanceof org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine rcm)
                || rcm.getMaintenanceMachine() == null || result == null) return recipe;
        float multiplier = rcm.getMaintenanceMachine().getDurationMultiplier();
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f || multiplier == 1.0f) return recipe;
        int duration = result.getDuration();
        int parallel = result.getParallel();
        if (duration > 1) result.setDuration(Math.max(1, (int) Math.round(duration * multiplier)));
        else if (parallel > 0) result.setParallel(Math.max(1, (int) Math.round(parallel / multiplier)));
        return recipe;
    }

    public static GTRecipe neutronFactory(MetaMachine machine, GTRecipe recipe,
                                                                OCParams params, OCResult result) {
        recipe = recipe.copy();
        long eut = Math.max(0L, (long) recipe.data.getInt("evt")) * NEUTRON_EU_PER_EVT;
        recipe.tickInputs.put(EURecipeCapability.CAP,
                List.of(new Content(eut, ChanceLogic.getMaxChancedValue(), ChanceLogic.getMaxChancedValue(), 0, null, null)));
        IGTRecipe.of(recipe).setHasTick(true);
        result.init(eut, recipe.duration, NEUTRON_FACTORY_PARALLEL, params.getOcAmount());
        return recipe;
    }
}
