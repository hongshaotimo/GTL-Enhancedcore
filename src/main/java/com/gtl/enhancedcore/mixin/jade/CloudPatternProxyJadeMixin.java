package com.gtl.enhancedcore.mixin.jade;

import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import net.minecraft.nbt.CompoundTag;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve upstream stock lines while removing only its tagged proxy count for cloud endpoints. */
@Mixin(targets = "org.gtlcore.gtlcore.integration.jade.provider.MEPatternBufferProvider", remap = false)
public abstract class CloudPatternProxyJadeMixin {
    @Inject(method = "putTag", at = @At("TAIL"))
    private static void cloud$withoutProxy(CompoundTag tag, MEPatternBufferPartMachineBase machine, CallbackInfo ci) {
        if (machine instanceof CloudPatternBufferMachine) tag.remove("proxies");
    }
}
