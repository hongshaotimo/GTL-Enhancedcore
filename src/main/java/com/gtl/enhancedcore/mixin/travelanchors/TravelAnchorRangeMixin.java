package com.gtl.enhancedcore.mixin.travelanchors;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes the configured radius from both anchor teleport lookup paths. */
@Mixin(targets = "de.castcrafter.travelanchors.TeleportHandler", remap = false)
@Pseudo
public abstract class TravelAnchorRangeMixin {
    @Inject(method = "getMaxDistance(Lnet/minecraft/world/entity/player/Player;)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void enhancedcore$unlimitedAnchorRange(Player player,
            CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(Double.POSITIVE_INFINITY);
    }
}
