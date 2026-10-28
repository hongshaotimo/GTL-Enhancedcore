package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All electric-machine capacity declarations in the pinned dependency set, including
 * overrides used by custom schedulers. Active-link checks leave other machines untouched. */
@Mixin(targets={
        "com.gtladd.gtladditions.api.machine.multiblock.GTLAddCoilWorkableElectricMultipleRecipesMultiblockMachine",
        "com.gtladd.gtladditions.api.machine.multiblock.GTLAddCoilWorkableElectricParallelHatchMultipleRecipesMachine",
        "com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricParallelHatchMultipleRecipesMachine",
        "com.gtladd.gtladditions.api.machine.mutable.MutableCoilElectricMultiblockMachine",
        "com.gtladd.gtladditions.api.machine.mutable.MutableCoilElectricParallelHatchMultiblockMachine",
        "com.gtladd.gtladditions.api.machine.mutable.MutableElectricMultiblockMachine",
        "com.gtladd.gtladditions.api.machine.mutable.MutableElectricParallelHatchMultiblockMachine",
        "com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessCoilWorkableElectricMultipleRecipesMultiblockMachine",
        "com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessCoilWorkableElectricParallelHatchMultipleRecipesMachine",
        "com.gtladd.gtladditions.api.machine.wireless.GTLAddWirelessWorkableElectricParallelHatchMultipleRecipesMachine",
        "com.gtladd.gtladditions.common.machine.multiblock.MultiBlockMachine$28$1",
        "com.gtladd.gtladditions.common.machine.multiblock.MultiBlockMachine$32$1",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.AdvancedSpaceElevatorModuleMachine",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.ApocalypticTorsionQuantumMatrix",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.BiologicalSimulationLaboratory",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.DimensionFocusInfinityCraftingArray",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.HeartOfTheUniverse",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.MacroAtomicResonantFragmentStripper",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.OreProcessorMachine",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.SkeletonShiftRiftEngine",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.TaixuTurbidArray",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIModule",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.CreateAggregation",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.DoorOfCreate",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableFusionReactorMachine",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutablePCBFactoryMachine",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableSuprachronalAssemblyLineMachine",
        "com.gtladd.gtladditions.common.machine.multiblock.controller.mutable.MutableTierCasingMachine",
        "com.gtladd.gtladditions.common.modify.MutableMultiBlockModify$setOtherMutable$3$1",
        "com.gtladd.gtladditions.common.modify.MutableMultiBlockModify$setOtherMutable$7$1",
        "org.gtlcore.gtlcore.api.machine.multiblock.CoilWorkableElectricMultipleRecipesMachine",
        "org.gtlcore.gtlcore.common.machine.multiblock.electric.CoilWorkableElectricMultipleRecipesMultiblockMachine",
        "org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricMultipleRecipesMachine",
        "org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricParallelHatchMultipleRecipesMachine"},remap=false,priority=1400)
public abstract class GalaxyMachineCapacityMixin {
    @Inject(method="getMaxParallel()I",at=@At("HEAD"),cancellable=true,require=1)
    private void galaxy$machineParallel(CallbackInfoReturnable<Integer> cir) {
        if (GalaxyLinks.unlimited((WorkableElectricMultiblockMachine)(Object)this)) cir.setReturnValue(Integer.MAX_VALUE);
    }
}
