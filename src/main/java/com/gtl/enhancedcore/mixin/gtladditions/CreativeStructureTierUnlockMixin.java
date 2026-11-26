package com.gtl.enhancedcore.mixin.gtladditions;

import com.gtladd.gtladditions.api.machine.feature.TierRequirement;
import com.gtladd.gtladditions.common.machine.trait.StructureTierTrait;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(value = StructureTierTrait.class, remap = false)
public abstract class CreativeStructureTierUnlockMixin {
    @ModifyExpressionValue(method = "unlock", at = @At(value = "INVOKE", target =
            "Lcom/gtladd/gtladditions/common/machine/trait/StructureTierTrait;requirement(I)Lcom/gtladd/gtladditions/api/machine/feature/TierRequirement;"), require = 1)
    private TierRequirement enhancedcore$creativeRequirement(TierRequirement requirement, Player player) {
        if (requirement != null && player instanceof ServerPlayer serverPlayer && serverPlayer.isCreative()) {
            return new TierRequirement(List.of(), List.of());
        }
        return requirement;
    }
}
