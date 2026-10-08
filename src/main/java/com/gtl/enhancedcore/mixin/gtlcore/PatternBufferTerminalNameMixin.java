package com.gtl.enhancedcore.mixin.gtlcore;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferAutoName;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferNameAccess;
import net.minecraft.network.chat.Component;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fixed Chinese in AE, including proven automatic keys saved by earlier versions. */
@Mixin(value = MEPatternBufferPartMachine.class, remap = false)
public abstract class PatternBufferTerminalNameMixin {

    @Inject(method = "getTerminalGroup", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlEnhancedcore$translateTerminalName(CallbackInfoReturnable<PatternContainerGroup> cir) {
        PatternContainerGroup group = cir.getReturnValue();
        if (group == null || !com.gtl.enhancedcore.common.recipe.iv.IvBuffers.compatible(this)) return;
        String stored = ((MEPatternBufferPartMachine) (Object) this).getCustomName();
        if (stored == null || stored.isEmpty()) return;
        if (!((Object) this instanceof SuperBufferNameAccess names)
                || !SuperBufferAutoName.shouldTranslate(stored,
                        names.enhanced$getAutomaticName(), names.enhanced$isManualName())) return;
        cir.setReturnValue(new PatternContainerGroup(group.icon(),
                Component.literal(SuperBufferAutoName.displayName(stored)), group.tooltip()));
    }
}
