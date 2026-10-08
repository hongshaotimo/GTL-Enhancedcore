package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.integration.terminal.LampBuildCandidates;
import com.hepdd.gtmthings.common.item.AdvancedTerminalBehavior;
import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Both terminals consume the chosen candidate through their existing inventory/AE paths. */
@Mixin(value = {
        UltimateTerminalBehavior.AutoBuildSetting.class,
        AdvancedTerminalBehavior.AutoBuildSetting.class
}, remap = false)
public abstract class TerminalLampCandidatesMixin {
    @Inject(method = "apply", at = @At("RETURN"), cancellable = true, require = 1, remap = false)
    private void gtlEnhancedcore$includeLampConfigurations(CallbackInfoReturnable<List<ItemStack>> cir) {
        cir.setReturnValue(LampBuildCandidates.expand(cir.getReturnValue()));
    }
}
