package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtl.enhancedcore.common.structure.CelestialEffectAnchors;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.gtladd.gtladditions.common.machine.trait.AstralArrayCompressionTrait", remap = false)
public abstract class ArcanicAstrographCollectionAnchorMixin {
    @Shadow(remap = false) public abstract ArcanicAstrograph getMachine();

    @ModifyReturnValue(method = "getCompressionCenter", at = @At("RETURN"), require = 1, allow = 1)
    private Vec3 enhanced$blueprintCollectionCenter(Vec3 original) {
        var machine = getMachine();
        return Vec3.atLowerCornerOf(machine.getPos()).add(CelestialEffectAnchors.astrograph(machine));
    }
}
