package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.common.machine.galaxy.StellarVortexSpaceStationMachine;
import com.gtl.enhancedcore.common.structure.StellarVortexStructure;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import net.minecraft.resources.ResourceLocation;

public final class GalaxyMachineRegistration {
    private GalaxyMachineRegistration() {}
    public static MachineDefinition hatch(boolean source) {
        String id = source ? "galaxy_connection_source_hatch" : "galaxy_connection_target_hatch";
        var builder = source
                ? GTLEnhancedcore.REGISTRATE.machine("galaxy_connection_source_hatch", holder -> new GalaxyConnectionHatch(holder, true))
                : GTLEnhancedcore.REGISTRATE.machine("galaxy_connection_target_hatch", holder -> new GalaxyConnectionHatch(holder, false));
        return builder
                .langValue(source ? "星辰源仓" : "星辰靶仓").rotationState(RotationState.ALL).tier(GTValues.OpV)
                .abilities(PartAbility.IMPORT_ITEMS)
                .renderer(() -> new OverlayTieredMachineRenderer(GTValues.OpV, GTLEnhancedcore.id("block/machine/part/" + id)))
                .tooltipBuilder(MachineTooltips.create(source ? "galaxy_source" : "galaxy_target")).register();
    }
    public static MultiblockMachineDefinition station() {
        return GTLEnhancedcore.REGISTRATE.multiblock("stellar_vortex_space_station", StellarVortexSpaceStationMachine::new)
                .langValue("星璇号空间站").rotationState(RotationState.NON_Y_AXIS)
                .appearanceBlock(() -> MachineRegistrationSupport.requiredBlockBlock("gtlcore:dimensionally_transcendent_casing"))
                .recipeTypes(GTLEnhancedcoreRecipeTypes.BRILLIANT_STARS)
                .pattern(StellarVortexStructure::create)
                .shapeInfos(definition -> java.util.List.of(new com.gregtechceu.gtceu.api.pattern.MultiblockShapeInfo(StellarVortexStructure.preview(definition))))
                .renderer(() -> new com.gtl.enhancedcore.client.renderer.machine.StellarVortexMachineRenderer())
                .hasTESR(true)
                .tooltipBuilder(MachineTooltips.create("stellar_vortex", GTLEnhancedcoreRecipeTypes.BRILLIANT_STARS)).register();
    }
}
