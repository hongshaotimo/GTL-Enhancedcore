package com.gtl.enhancedcore.mixin.gtladditions;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyWirelessEnergyHandler;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyWirelessHandlerSelection;
import com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/** Supplies a linked grid only when the machine has no native wireless handler. */
@Pseudo
@Mixin(value = IWirelessElectricMultiblockMachine.class, remap = false)
public interface GalaxyWirelessEnergyHandlerMixin {
    /** The target interface's verified default returns null; class overrides remain authoritative. */
    @Overwrite
    public default IWirelessNetworkEnergyHandler getWirelessNetworkEnergyHandler() {
        if (!((Object)this instanceof WorkableElectricMultiblockMachine machine)) return null;
        return GalaxyWirelessHandlerSelection.select(null,
                () -> GalaxyWirelessEnergyHandler.forMachine(machine));
    }
}
