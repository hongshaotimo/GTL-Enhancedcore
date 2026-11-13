package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import com.gtl.enhancedcore.common.machine.AddThreadMutableMachine;
import com.gtl.enhancedcore.mixin.gtceu.IvNativeMutableAccessor;
import com.gtl.enhancedcore.mixin.gtlcore.IvNativeAddMultipleAccessor;
import com.gtl.enhancedcore.mixin.gtlcore.IvNativeMultipleAccessor;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.AddMutableRecipesLogic;
import com.gtladd.gtladditions.api.machine.IGTLAddMultiRecipeMachine;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

/** Pure preparation for the stock multi-recipe engines; their searches withdraw inputs. */
public final class GalaxyNativeRecipePreparation {
    private static final int BATCH_TICKS = 20;
    static final String EXACT_ENERGY_TAG = "gtle_galaxy_native_energy";

    private GalaxyNativeRecipePreparation() {}

    /** Custom subclasses can own extra admission, energy or completion contracts. */
    public static boolean supports(RecipeLogic logic) {
        Class<?> type = logic.getClass();
        return type == MultipleRecipesLogic.class || type == MutableRecipesLogic.class
                || type == AddMutableRecipesLogic.class || type == AddThreadMutableMachine.AddThreadRecipeLogic.class
                || type == GTLAddMultipleRecipesLogic.class || type == ThreadLimitedRecipeLogic.class;
    }

    /** Retains native predicates without matching or consuming shared inventories. */
    public static boolean allowed(RecipeLogic logic, GTRecipe recipe) {
        if (!supports(logic)) return true;
        var machine = (WorkableElectricMultiblockMachine) logic.getMachine();
        if (IGTRecipe.of(recipe).getEuTier() > GalaxyPower.tier(machine, machine.getTier())
                || !recipe.checkConditions(logic).isSuccess()) return false;
        if (logic instanceof MultipleRecipesLogic multiple && multiple.getDataCheck() != null
                && !multiple.getDataCheck().test(recipe.data, machine)) return false;
        if (logic instanceof MutableRecipesLogic<?>) {
            var check = ((IvNativeMutableAccessor) logic).iv$recipeCheck();
            if (check != null && !check.test(recipe, machine)) return false;
        }
        if (logic instanceof GTLAddMultipleRecipesLogic) {
            var check = ((IvNativeAddMultipleAccessor) logic).iv$addRecipeCheck();
            if (check != null && !check.test(recipe, machine)) return false;
        }
        return true;
    }

    /** Call once at final admission, never during candidate search or binary simulation. */
    public static boolean beforeAdmission(RecipeLogic logic) {
        if (!supports(logic)) return true;
        if (logic instanceof GTLAddMultipleRecipesLogic) {
            var before = ((IvNativeAddMultipleAccessor) logic).iv$addBeforeWorking();
            return before == null || before.test((WorkableElectricMultiblockMachine) logic.getMachine());
        }
        return true;
    }

    /**
     * Projects one native batch onto its native minimum duration, retaining integer energy.
     * Keep fractional native cost until all input copies have been allocated.
     * Extra tick capabilities and total-CWU work keep their original scheduler.
     */
    public static GTRecipe prepare(RecipeLogic logic, GTRecipe origin) {
        if (!supports(logic) || origin.duration <= 0
                || origin.data.getBoolean("duration_is_total_cwu")
                || origin.tickInputs.entrySet().stream().anyMatch(entry ->
                        entry.getKey() != EURecipeCapability.CAP && !entry.getValue().isEmpty())
                || origin.tickOutputs.values().stream().anyMatch(contents -> !contents.isEmpty())) return null;
        long baseEUt = logic instanceof MutableRecipesLogic<?>
                ? ((IvNativeMutableAccessor) logic).iv$recipeEUt(origin) : RecipeHelper.getInputEUt(origin);
        if (baseEUt < 0) return null;
        double multiplier = logic instanceof MutableRecipesLogic<?>
                ? ((IvNativeMutableAccessor) logic).iv$euMultiplier()
                : ((IvNativeMultipleAccessor) logic).iv$euMultiplier();
        if (!Double.isFinite(multiplier) || multiplier < 0) return null;
        BigInteger baseEnergy = BigInteger.valueOf(baseEUt).multiply(BigInteger.valueOf(origin.duration));
        BigDecimal exactEnergy = new BigDecimal(baseEnergy).multiply(BigDecimal.valueOf(multiplier));
        BigInteger total = exactEnergy.toBigInteger();
        int duration = logic instanceof GTLAddMultipleRecipesLogic
                ? Math.max(1, ((IGTLAddMultiRecipeMachine) logic.getMachine()).getLimitedDuration()) : BATCH_TICKS;
        BigInteger ticks = BigInteger.valueOf(duration);
        BigInteger eut = total.add(ticks.subtract(BigInteger.ONE)).divide(ticks);
        GTRecipe prepared = origin.copy();
        prepared.data = origin.data.copy();
        RecipeExtensionCopier.copy(origin, prepared);
        prepared.tickInputs.remove(EURecipeCapability.CAP);
        prepared.duration = duration;
        prepared.data.putString(EXACT_ENERGY_TAG, exactEnergy.toPlainString());
        prepared.data.putString(GalaxyPower.EU_TAG, eut.toString());
        prepared.data.putString(GalaxyPower.LEFT_TAG, total.toString());
        IGTRecipe.of(prepared).setBatchProcessed(true);
        return prepared;
    }

    /** ADD getters already include the connection's extra threads. */
    public static int threads(RecipeLogic logic) {
        if (!supports(logic)) return 0;
        if (logic instanceof MutableRecipesLogic<?> mutable) return Math.max(1, mutable.getMultipleThreads());
        if (logic instanceof GTLAddMultipleRecipesLogic add) return Math.max(1, add.getMultipleThreads());
        return GalaxyLinks.threads((WorkableElectricMultiblockMachine) logic.getMachine(), 64);
    }
}
