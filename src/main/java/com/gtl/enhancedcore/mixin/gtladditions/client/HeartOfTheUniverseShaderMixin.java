package com.gtl.enhancedcore.mixin.gtladditions.client;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Retains ADD's reload, deferred targets and compositor while selecting our volume material. */
@Mixin(targets = "com.gtladd.gtladditions.client.render.machine.heart.HeartBlackHoleShaders", remap = false)
public abstract class HeartOfTheUniverseShaderMixin {
    @ModifyArg(method = "register(Lnet/minecraftforge/client/event/RegisterShadersEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;<init>"
                    + "(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;"
                    + "Lcom/mojang/blaze3d/vertex/VertexFormat;)V", ordinal = 0, remap = false),
            index = 1, require = 1, allow = 1)
    private ResourceLocation enhanced$celestialVolumeMaterial(ResourceLocation original) {
        return new ResourceLocation("gtl_enhancedcore", "heart/celestial_black_hole");
    }
}
