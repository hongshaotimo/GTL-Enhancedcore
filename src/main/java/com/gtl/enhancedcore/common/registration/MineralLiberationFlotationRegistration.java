package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.structure.MineralLiberationFlotationStructure;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import net.minecraft.resources.ResourceLocation;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.requiredBlockBlock;

/** UHV single-stage ore liberation and froth flotation. */
public final class MineralLiberationFlotationRegistration {
    private MineralLiberationFlotationRegistration() {}

    public static MultiblockMachineDefinition register() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("mineral_liberation_flotation_complex", WorkableElectricMultiblockMachine::new)
                .langValue("澄渊研浮联合机")
                .tier(GTValues.UHV)
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtlcore:pikyonium_machine_casing"))
                .recipeTypes(GTLEnhancedcoreRecipeTypes.MINERAL_LIBERATION_FLOTATION_RECIPES)
                .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .pattern(MineralLiberationFlotationStructure::create)
                .tooltipBuilder(MachineTooltips.create("mineral_liberation_flotation_complex",
                        GTLEnhancedcoreRecipeTypes.MINERAL_LIBERATION_FLOTATION_RECIPES))
                .workableCasingRenderer(new ResourceLocation("gtlcore", "block/casings/pikyonium_machine_casing"),
                        GTLEnhancedcore.id("block/multiblock/mineral_liberation_flotation_complex"))
                .register();
    }
}
