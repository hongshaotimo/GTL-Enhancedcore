package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.machine.IntegratedUniversalFactoryMachine;
import com.gtl.enhancedcore.common.structure.IntegratedFactoryStructure;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.Map;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import net.minecraft.resources.ResourceLocation;

import static com.gtl.enhancedcore.common.registration.MachineRegistrationSupport.*;

/** Machine builders, invoked only during the GTCEu registration event. */
public final class IntegratedFactoryRegistration {
    private IntegratedFactoryRegistration() {}

    public static MultiblockMachineDefinition register() {
        GTRecipeType[] recipeTypes = recipeTypes();
        return GTLEnhancedcore.REGISTRATE
                .multiblock("universal_joint_factory", IntegratedUniversalFactoryMachine::new)
                .langValue("通用联合工厂")
                .rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> requiredBlockBlock("gtlcore:multi_functional_casing"))
                .recipeTypes(recipeTypes)
                .pattern(IntegratedFactoryStructure::create)
                .tooltipBuilder(MachineTooltips.create("universal_joint_factory", Map.of(
                        "tooltip.gtl_enhancedcore.universal_joint_factory.parallel",
                        new Object[]{IntegratedUniversalFactoryMachine.THREADS,
                                (long) IntegratedUniversalFactoryMachine.THREADS * IntegratedUniversalFactoryMachine.PARALLEL}),
                        recipeTypes))
                .workableCasingRenderer(new ResourceLocation("gtlcore", "block/multi_functional_casing"),
                        GTCEu.id("block/multiblock/processing_array"))
                .register();
    }

    public static GTRecipeType[] recipeTypes() {
        return new GTRecipeType[]{
                GTRecipeTypes.BENDER_RECIPES, GTRecipeTypes.COMPRESSOR_RECIPES,
                GTRecipeTypes.FORGE_HAMMER_RECIPES, GTRecipeTypes.CUTTER_RECIPES,
                GTRecipeTypes.EXTRUDER_RECIPES, GTRecipeTypes.LATHE_RECIPES,
                GTRecipeTypes.WIREMILL_RECIPES, GTRecipeTypes.FORMING_PRESS_RECIPES,
                GTRecipeTypes.POLARIZER_RECIPES, GTRecipeTypes.LASER_ENGRAVER_RECIPES,
                GTRecipeTypes.FLUID_SOLIDFICATION_RECIPES,
                GTRecipeTypes.ASSEMBLER_RECIPES, GTRecipeTypes.CIRCUIT_ASSEMBLER_RECIPES,
                GTRecipeTypes.CENTRIFUGE_RECIPES, GTRecipeTypes.THERMAL_CENTRIFUGE_RECIPES,
                GTRecipeTypes.ELECTROLYZER_RECIPES, GTRecipeTypes.SIFTER_RECIPES,
                GTRecipeTypes.MACERATOR_RECIPES, GTRecipeTypes.EXTRACTOR_RECIPES,
                GTLRecipeTypes.DEHYDRATOR_RECIPES,
                GTRecipeTypes.CHEMICAL_RECIPES, GTRecipeTypes.MIXER_RECIPES,
                GTRecipeTypes.CHEMICAL_BATH_RECIPES, GTRecipeTypes.ORE_WASHER_RECIPES
        };
    }
}
