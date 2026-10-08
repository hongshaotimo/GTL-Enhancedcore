package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.machines.multiblock.GTLStructures;
import com.gtl.enhancedcore.common.machine.PlusFactoryMachine;
import com.gtl.enhancedcore.common.recipe.MachineRecipeModifiers;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import net.minecraft.resources.ResourceLocation;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** Registration for the four independent MV passive production lines. */
public final class PlusFactoryRegistration {
    private PlusFactoryRegistration() {}

    public static MultiblockMachineDefinition registerPlusFactory(String id, String zhName, String tooltipKey,
                                                                   GTRecipeType[] recipeTypes, String centerBlock) {
        return GTLEnhancedcore.REGISTRATE.multiblock(id, PlusFactoryMachine::new)
                .langValue(zhName)
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtlcore:multi_functional_casing"))
                .recipeTypes(recipeTypes)
                .pattern(definition -> GTLStructures.plusFactory(definition, centerBlock))
                .recipeModifiers(new RecipeModifier[]{MachineRecipeModifiers::plusFactory, MachineRecipeModifiers::maintenance})
                .tooltipBuilder(MachineTooltips.create(tooltipKey, recipeTypes))
                .workableCasingRenderer(new ResourceLocation("gtlcore", "block/multi_functional_casing"),
                        GTCEu.id("block/multiblock/processing_array"))
                .register();
    }

    public static GTRecipeType[] processingPlusRecipeTypes() {
        return new GTRecipeType[]{GTRecipeTypes.BENDER_RECIPES, GTRecipeTypes.COMPRESSOR_RECIPES,
                GTRecipeTypes.FORGE_HAMMER_RECIPES, GTRecipeTypes.CUTTER_RECIPES, GTRecipeTypes.EXTRUDER_RECIPES,
                GTRecipeTypes.LATHE_RECIPES, GTRecipeTypes.WIREMILL_RECIPES, GTRecipeTypes.FORMING_PRESS_RECIPES,
                GTRecipeTypes.POLARIZER_RECIPES, GTRecipeTypes.LASER_ENGRAVER_RECIPES,
                GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES};
    }

    public static GTRecipeType[] assemblingPlusRecipeTypes() {
        return new GTRecipeType[]{GTRecipeTypes.ASSEMBLER_RECIPES, GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES};
    }

    public static GTRecipeType[] separatingPlusRecipeTypes() {
        return new GTRecipeType[]{GTRecipeTypes.CENTRIFUGE_RECIPES, GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES,
                GTRecipeTypes.ELECTROLYZER_RECIPES, GTRecipeTypes.SIFTER_RECIPES, GTRecipeTypes.MACERATOR_RECIPES,
                GTRecipeTypes.EXTRACTOR_RECIPES, GTLRecipeTypes.DEHYDRATOR_RECIPES};
    }

    public static GTRecipeType[] mixingPlusRecipeTypes() {
        return new GTRecipeType[]{GTRecipeTypes.CHEMICAL_RECIPES, GTRecipeTypes.MIXER_RECIPES,
                GTRecipeTypes.CHEMICAL_BATH_RECIPES, GTRecipeTypes.ORE_WASHER_RECIPES};
    }
}
