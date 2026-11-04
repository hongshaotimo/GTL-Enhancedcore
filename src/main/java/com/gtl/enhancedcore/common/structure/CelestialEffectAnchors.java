package com.gtl.enhancedcore.common.structure;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import net.minecraft.world.phys.Vec3;

/** Centers from the imported blueprints, relative to the controller's block center. */
public final class CelestialEffectAnchors {
    private CelestialEffectAnchors() {}

    public static Vec3 eye(MultiblockControllerMachine machine) {
        return fromControllerOrigin(machine, -55, -91);
    }

    public static Vec3 astrograph(MultiblockControllerMachine machine) {
        return fromControllerOrigin(machine, 103, -75);
    }

    private static Vec3 fromControllerOrigin(MultiblockControllerMachine machine, int height, int depth) {
        // Match GTCEu's actual front/up/flip transform, then add the unrotated half block.
        var delta = ((BlockPatternAccessor) machine.getPattern()).enhanced$chamberOffset(
                0, height, depth, machine.getFrontFacing(), machine.getUpwardsFacing(), machine.isFlipped());
        return Vec3.atLowerCornerOf(delta).add(.5, .5, .5);
    }
}
