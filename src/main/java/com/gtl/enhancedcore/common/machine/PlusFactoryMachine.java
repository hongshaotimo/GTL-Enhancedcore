package com.gtl.enhancedcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gtl.enhancedcore.common.recipe.PlusFactoryRecipeLogic;
import com.gtladd.gtladditions.api.machine.gui.MultiblockDisplayText;
import java.util.List;
import net.minecraft.network.chat.Component;

/** MV 通用工厂 Plus 系列；每台机器只处理自己的配方类型。 */
public final class PlusFactoryMachine extends WorkableElectricMultiblockMachine {
    public static final int BASE_PARALLEL = 16;

    public PlusFactoryMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new PlusFactoryRecipeLogic(this);
    }

    @Override
    public boolean keepSubscribing() {
        return true;
    }

    /** LV 为 16 并行，每升一级翻倍，IV 及以上固定为 256。 */
    public int getMaxParallel() {
        int tier = Math.min(GTUtil.getFloorTierByVoltage(getMaxVoltage()), GTValues.IV);
        long parallel = (long) BASE_PARALLEL << Math.max(0, tier - GTValues.LV);
        return (int) Math.min(Integer.MAX_VALUE, parallel);
    }

    @Override
    public void addDisplayText(List<Component> text) {
        super.addDisplayText(text);
        if (isFormed()) MultiblockDisplayText.builder(text, true).addParallelsLine(getMaxParallel());
    }
}
