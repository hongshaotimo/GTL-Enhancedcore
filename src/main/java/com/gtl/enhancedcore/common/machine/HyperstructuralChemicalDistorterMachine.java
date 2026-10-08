package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gtl.enhancedcore.common.recipe.SingleRecipeParallel;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;

public class HyperstructuralChemicalDistorterMachine extends CoilWorkableElectricMultiblockMachine
        implements IThreadModifierMachine {
    private IThreadModifierPart threadPart;

    public HyperstructuralChemicalDistorterMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    public static final RecipeModifier PARALLEL = (machine, recipe, params, result) -> {
        if (!(machine instanceof HyperstructuralChemicalDistorterMachine distorter)
                || !distorter.checkTemperature(recipe)) return null;
        long excess = (long) distorter.getCoilType().getCoilTemperature() - recipe.data.getInt("ebf_temp");
        int coilParallel = (int) Math.min(Integer.MAX_VALUE, Math.max(1, excess / 100 * 4));
        return SingleRecipeParallel.apply(distorter, recipe, coilParallel);
    };

    private boolean checkTemperature(GTRecipe recipe) {
        if (recipe.data.getInt("ebf_temp") <= getCoilType().getCoilTemperature()) return true;
        RecipeResult.of(this, RecipeResult.FAIL_NO_ENOUGH_TEMPERATURE);
        return false;
    }

    @Override
    public boolean beforeWorking(GTRecipe recipe) {
        return checkTemperature(recipe) && super.beforeWorking(recipe);
    }

    @Override
    public boolean alwaysTryModifyRecipe() {
        return true;
    }

    @Override
    public IThreadModifierPart getThreadPartMachine() {
        return threadPart;
    }

    @Override
    public void setThreadPartMachine(IThreadModifierPart part) {
        threadPart = part;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        threadPart = null;
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        threadPart = null;
    }

    @Override
    public void addDisplayText(List<Component> lines) {
        super.addDisplayText(lines);
        if (isFormed()) lines.add(Component.translatable("gtl_enhancedcore.parallel.coil_temperature",
                getCoilType().getCoilTemperature()));
        SingleRecipeParallel.append(this, lines);
    }
}
