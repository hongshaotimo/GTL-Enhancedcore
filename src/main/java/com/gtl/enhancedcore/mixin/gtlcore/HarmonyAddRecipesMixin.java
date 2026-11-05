package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gtl.enhancedcore.common.recipe.HarmonyAddRecipeLogic;
import com.gtl.enhancedcore.common.recipe.HarmonyAddRecipes;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.HarmonyMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HarmonyMachine.class, remap = false, priority = 900)
public abstract class HarmonyAddRecipesMixin extends NoEnergyMultiblockMachine {
    protected HarmonyAddRecipesMixin(IMachineBlockEntity holder, Object... args) { super(holder, args); }

    @Override protected RecipeLogic createRecipeLogic(Object... args) {
        var owner = (HarmonyMachine) (Object) this;
        return HarmonyAddRecipes.enabled(owner) ? new HarmonyAddRecipeLogic(owner) : super.createRecipeLogic(args);
    }

    @Inject(method = "addDisplayText", at = @At("TAIL"), require = 1, allow = 1)
    private void gtle$addThreads(List<Component> lines, CallbackInfo ci) {
        HarmonyAddRecipes.appendDisplay((HarmonyMachine) (Object) this, lines);
    }
}
