package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.structure.LanthanideRefiningHubStructure;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.requiredBlockBlock;

/** LuV integrated extraction and separation of rare-earth dusts. */
public final class LanthanideMachineRegistration {
    private LanthanideMachineRegistration() {}

    public static MultiblockMachineDefinition register() {
        return GTLEnhancedcore.REGISTRATE
                .multiblock("lanthanide_refining_hub", WorkableElectricMultiblockMachine::new)
                .langValue("镧系联萃精炼中枢")
                .tier(GTValues.LuV)
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtceu:computer_casing"))
                .recipeTypes(GTLEnhancedcoreRecipeTypes.LANTHANIDE_REFINING)
                .recipeModifiers(GTRecipeModifiers.PARALLEL_HATCH,
                        GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(OverclockingLogic.NON_PERFECT_OVERCLOCK))
                .pattern(LanthanideRefiningHubStructure::create)
                .tooltipBuilder(MachineTooltips.create("lanthanide_refining_hub",
                        GTLEnhancedcoreRecipeTypes.LANTHANIDE_REFINING))
                .workableCasingRenderer(GTCEu.id("block/computer_casing"),
                        GTCEu.id("block/multiblock/gcym/large_extractor"))
                .register();
    }
}
