package com.gtl.enhancedcore.audit.mixin;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVein;
import com.gregtechceu.gtceu.api.data.worldgen.ores.OreGenerator;
import com.gtl.enhancedcore.audit.OreGenerationDiagnostics;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = OreGenerator.class, remap = false)
public abstract class OreGenerationAuditMixin {
    @Inject(method = "generateOres(Lcom/gregtechceu/gtceu/api/data/worldgen/ores/OreGenerator$VeinConfiguration;Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/ChunkPos;)Ljava/util/Optional;",
            at = @At("HEAD"))
    private void audit$generation(OreGenerator.VeinConfiguration config, WorldGenLevel level, ChunkPos pos,
                                 CallbackInfoReturnable<Optional<GeneratedVein>> cir) {
        OreGenerationDiagnostics.generating(config);
    }
}
