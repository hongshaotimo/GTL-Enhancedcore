package com.gtl.enhancedcore.mixin.gtladditions;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(targets={
        "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic",
        "com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.ApocalypticTorsionQuantumMatrix$Companion$ApocalypticTorsionQuantumMatrixLogic",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.HeartOfTheUniverse$Companion$HeartOfTheUniverseLogic",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray$Companion$TaixuTurbidArrayLogic"},remap=false,priority=1100)
public abstract class GalaxyThreadCapacityMixin {
    // Wrap the entire transformed getter, including IV guards' early returns.
    // OreProcessorRecipeLogic delegates to the base getter, then adds its hatch
    // bonus. Injecting that override too would grant the extra 10,000 twice.
    @WrapMethod(method="getMultipleThreads")
    private int galaxy$threads(Operation<Integer> original) {
        int threads = original.call();
        return ((RecipeLogic)(Object)this).getMachine() instanceof WorkableElectricMultiblockMachine m
                ? GalaxyLinks.threads(m, threads) : threads;
    }
}
