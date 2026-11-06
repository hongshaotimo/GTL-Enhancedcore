package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIController;
import com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIModule;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.gtlcore.gtlcore.integration.gtmt.NewGTValues;
import org.gtlcore.gtlcore.utils.NumberUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** A garden has no local energy hatches: both recipe admission and UI must consult its host. */
@Mixin(value = BiosphereIIIModule.class, remap = false)
public abstract class BiosphereModulePowerMixin extends GTLAddWorkableElectricMultipleRecipesMachine {
    @Shadow private BiosphereIIIController host;

    protected BiosphereModulePowerMixin(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public long getMaxVoltage() {
        // Voltage eligibility is distinct from the host's power budget shared among gardens.
        return host != null && host.isFormed() ? host.getMaxVoltage() : 0;
    }

    @Override
    protected void addEnergyDisplay(List<Component> textList) {
        var wireless = getWirelessNetworkEnergyHandler();
        if (wireless != null && wireless.isOnline()) {
            // Keep ADD's arbitrary-precision wireless balance and its original labels.
            super.addEnergyDisplay(textList);
            return;
        }

        long power = host != null && host.isFormed() ? getOverclockVoltage() : 0;
        int powerTier = power == Long.MAX_VALUE ? 30 : NumberUtils.getFakeVoltageTier(power);
        textList.add(Component.translatable("gtceu.multiblock.max_energy_per_tick",
                        FormattingUtil.formatNumbers(power), Component.literal(NewGTValues.VNF[powerTier]))
                .withStyle(ChatFormatting.GRAY).withStyle(style -> style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("gtceu.multiblock.max_energy_per_tick_hover")
                                .withStyle(ChatFormatting.GRAY)))));

        // ADD's inherited implementation reads its local tier field, which remains ULV.
        int recipeTier = host != null && host.isFormed() ? getTier() : 0;
        textList.add(Component.translatable("gtceu.multiblock.max_recipe_tier",
                        Component.literal(GTValues.VNF[Math.min(Math.max(recipeTier, 0), GTValues.VNF.length - 1)]))
                .withStyle(ChatFormatting.GRAY).withStyle(style -> style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("gtceu.multiblock.max_recipe_tier_hover")
                                .withStyle(ChatFormatting.GRAY)))));
    }
}
