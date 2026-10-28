package com.gtl.enhancedcore.common.machine.galaxy;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** Resolve only already-loaded blocks. Links never force-load a dimension or chunk. */
public final class GalaxyLinks {
    private GalaxyLinks() {}
    public static List<IMultiController> controllers(GalaxyConnectionHatch hatch) {
        if (!(hatch.getLevel() instanceof ServerLevel level)) return hatch.getControllers();
        var result = new ArrayList<IMultiController>();
        for (var pos : ((MultiblockPartControllerPositionsAccessor) hatch).enhancedcore$getControllerPositions()) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(pos) instanceof IMachineBlockEntity holder
                    && holder.getMetaMachine() instanceof IMultiController controller) result.add(controller);
        }
        return result;
    }
    public static boolean allowed(WorkableElectricMultiblockMachine machine) {
        return machine != null && com.gtl.enhancedcore.common.config.GalaxyConnectionConfig.allows(machine.getDefinition().getId());
    }
    public static boolean installed(WorkableElectricMultiblockMachine machine) {
        return allowed(machine) && machine.getParts().stream().anyMatch(p -> p instanceof GalaxyConnectionHatch h && !h.isSource());
    }
    public static boolean active(WorkableElectricMultiblockMachine machine) {
        if (!allowed(machine) || !machine.isFormed() || !machine.isRecipeLogicAvailable()) return false;
        return machine.getParts().stream().anyMatch(p -> p instanceof GalaxyConnectionHatch h && !h.isSource() && h.linkActive());
    }
    public static StellarVortexSpaceStationMachine linkedStation(WorkableElectricMultiblockMachine machine) {
        if (!active(machine)) return null;
        return machine.getParts().stream()
                .filter(p -> p instanceof GalaxyConnectionHatch h && !h.isSource() && h.linkActive())
                .map(p -> ((GalaxyConnectionHatch)p).sourceStation())
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }
    public static boolean hasThreadModifierHatch(WorkableElectricMultiblockMachine machine) {
        return machine != null && machine.getParts().stream().anyMatch(
                p -> p instanceof com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart);
    }
    public static boolean crossRecipeEnabledByConnection(WorkableElectricMultiblockMachine machine) {
        return active(machine) && !hasThreadModifierHatch(machine);
    }
    public static boolean connectionProvidesUnlimitedCapacity(boolean linkActive, boolean hasThreadModifierHatch) {
        return linkActive && !hasThreadModifierHatch;
    }
    public static boolean unlimited(WorkableElectricMultiblockMachine machine) {
        return connectionProvidesUnlimitedCapacity(active(machine), hasThreadModifierHatch(machine));
    }
    public static int threads(WorkableElectricMultiblockMachine machine, int original) {
        if (!active(machine)) return original;
        return unlimited(machine) ? Integer.MAX_VALUE : original;
    }
    public static long batchBudget(WorkableElectricMultiblockMachine machine, long original) {
        if (!active(machine)) return original;
        if (unlimited(machine)) return (long)Integer.MAX_VALUE * Integer.MAX_VALUE;
        return original;
    }
}
