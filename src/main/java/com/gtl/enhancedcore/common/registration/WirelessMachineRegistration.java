package com.gtl.enhancedcore.common.registration;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.data.machines.multiblock.GTLStructures;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.Map;
import java.util.function.Supplier;
import java.util.Locale;

import com.gtl.enhancedcore.common.machine.*;

/** Machine builders, invoked only during the GTCEu registration event. */
public final class WirelessMachineRegistration {
    private WirelessMachineRegistration() {}

    private static final int[] RESONATOR_TIERS = {
            GTValues.LV, GTValues.MV, GTValues.HV, GTValues.EV, GTValues.IV,
            GTValues.LuV, GTValues.ZPM, GTValues.UV, GTValues.UHV, GTValues.UEV,
            GTValues.UIV, GTValues.UXV, GTValues.OpV, GTValues.MAX
    };

    private static final int[] CHARGER_TIERS = {
            GTValues.LV, GTValues.MV
    };

    public static MachineDefinition[] registerCrystalResonators() {
        MachineDefinition[] defs = new MachineDefinition[GTValues.V.length];
        for (int tier : RESONATOR_TIERS) {
            String tierLower = GTValues.VN[tier].toLowerCase();
            String tierName = GTValues.VN[tier];
            long generation = EndCrystalResonatorMachine.generationForTier(tier);
            long voltage = GTValues.V[tier];
            long amperage = EndCrystalResonatorMachine.amperageForTier(tier);
            long capacity = EndCrystalResonatorMachine.capacityForTier(tier);

            MachineDefinition def = GTLEnhancedcore.REGISTRATE
                    .machine(tierLower + "_crystal_resonator",
                            holder -> new EndCrystalResonatorMachine(holder, tier))
                    .langValue(tierName + "-末晶谐振器")
                    .rotationState(RotationState.NON_Y_AXIS)
                    .tier(tier)
                    .tooltipBuilder(MachineTooltips.create("crystal_resonator", Map.of(
                            "tooltip.gtl_enhancedcore.crystal_resonator.rate", new Object[]{generation},
                            "tooltip.gtl_enhancedcore.crystal_resonator.detail", new Object[]{voltage, amperage},
                            "tooltip.gtl_enhancedcore.crystal_resonator.buffer", new Object[]{capacity})))
                    .register();
            defs[tier] = def;
            GTLEnhancedcore.LOGGER.debug("Registered {}-crystal_resonator (id: {})", tierName, def.getId());
        }
        return defs;
    }

    public static MultiblockMachineDefinition[] registerWirelessChargers() {
        MultiblockMachineDefinition[] defs = new MultiblockMachineDefinition[GTValues.V.length];
        for (int tier : CHARGER_TIERS) {
            String tierLower = GTValues.VN[tier].toLowerCase(Locale.ROOT);
            String tierName = GTValues.VN[tier];

            MultiblockMachineDefinition def = GTLEnhancedcore.REGISTRATE
                    .multiblock(tierLower + "_wireless_charger",
                            holder -> new WirelessChargerMachine(holder, tier))
                    .langValue(tier == GTValues.LV ? "低压无线充能器" : "高压无线充能器")
                    .rotationState(RotationState.NON_Y_AXIS)
                    // 外观与背面/侧面材质按电压等级对应 GTCEu 机械外壳（与 GTMachines.HULL 同款）
                    .appearanceBlock((Supplier) GTMachines.HULL[tier]::getBlock)
                    .recipeTypes(GTLEnhancedcoreRecipeTypes.WIRELESS_CHARGER)
                    // LV 台使用 LVwxcnq.schem、MV 台使用 mvwxcnq.schem 专用结构（均无仓室位）
                    .pattern(definition -> tier == GTValues.LV
                            ? GTLStructures.lvWirelessCharger(definition)
                            : GTLStructures.mvWirelessCharger(definition))
                    .tooltipBuilder(MachineTooltips.create("wireless_charger", Map.of(
                            "tooltip.gtl_enhancedcore.wireless_charger.power", new Object[]{GTValues.VN[tier]},
                            "tooltip.gtl_enhancedcore.wireless_charger.scan", new Object[]{20})))
                    .workableCasingRenderer(
                            GTCEu.id("block/casings/voltage/" + tierLower + "/side"),
                            GTLEnhancedcore.id("block/multiblock/wireless_charger"))
                    .register();
            defs[tier] = def;
            GTLEnhancedcore.LOGGER.debug("Registered {}-wireless_charger (id: {})", tierName, def.getId());
        }
        return defs;
    }
}
