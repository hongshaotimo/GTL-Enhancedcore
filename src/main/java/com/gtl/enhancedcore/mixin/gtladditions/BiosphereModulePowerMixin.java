package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtladd.gtladditions.api.machine.multiblock.GTLAddWorkableElectricMultipleRecipesMachine;
import com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIController;
import com.gtladd.gtladditions.common.machine.multiblock.controller.bs.BiosphereIIIModule;
import com.gtl.enhancedcore.common.cloud.CloudPowerAccess;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.gtlcore.gtlcore.integration.gtmt.NewGTValues;
import org.gtlcore.gtlcore.utils.NumberUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** A garden has no local energy hatches: both recipe admission and UI must consult its host. */
@Mixin(value = BiosphereIIIModule.class, remap = false)
public abstract class BiosphereModulePowerMixin extends GTLAddWorkableElectricMultipleRecipesMachine implements CloudPowerAccess {
    @Shadow private BiosphereIIIController host;

    protected BiosphereModulePowerMixin(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Unique private boolean cloud$hostReady() {
        return getLevel() instanceof net.minecraft.server.level.ServerLevel level && level.getServer().isSameThread()
                && isFormed() && host != null && host.getLevel() == level && host.isFormed()
                && host.getTier() >= 10 && host.isWorkingEnabled();
    }

    @Override public long cloudPowerLimit() { return cloud$hostReady() ? Math.max(0, getOverclockVoltage()) : 0; }

    @Override public boolean cloudPowerReady(BigInteger payment) {
        if (payment == null || payment.signum() < 0 || !cloud$hostReady()) return false;
        var wireless = host.getWirelessNetworkEnergyHandler();
        if (wireless != null) return wireless.isOnline() && wireless.getMaxAvailableEnergy().compareTo(payment) >= 0;
        if (payment.compareTo(BigInteger.valueOf(cloudPowerLimit())) > 0 || payment.bitLength() > 63) return false;
        var energy = host.getEnergyContainer();
        return energy != null && energy.getEnergyStored() >= payment.longValueExact();
    }

    @Override public Payment cloudPowerPay(BigInteger payment) {
        if (!cloudPowerReady(payment)) return null;
        var paidHost = host;
        var wireless = paidHost.getWirelessNetworkEnergyHandler();
        if (payment.signum() == 0) return () -> {};
        if (wireless != null) {
            if (!paidHost.requestWirelessEnergy$gtladditions(payment.negate())) return null;
            return new OncePayment(() -> {
                if (!wireless.consumeEnergy(payment)) throw new IllegalStateException("Biosphere wireless refund failed");
                ((BiosphereHostEnergyInvoker)(Object)paidHost).cloud$recordEnergyConsumption(payment.negate());
            });
        }
        var energy = paidHost.getEnergyContainer();
        long amount = payment.longValueExact();
        if (!paidHost.requestEnergy$gtladditions(amount)) return null;
        return new OncePayment(() -> {
            if (energy.addEnergy(amount) != amount) throw new IllegalStateException("Biosphere wired refund was incomplete");
            ((BiosphereHostEnergyInvoker)(Object)paidHost).cloud$recordEnergyConsumption(payment.negate());
        });
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
