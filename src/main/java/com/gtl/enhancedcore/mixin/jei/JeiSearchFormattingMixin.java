package com.gtl.enhancedcore.mixin.jei;

import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Normalize colored names before JEI stores the query and notifies its search listeners. */
@Mixin(targets = "mezz.jei.gui.filter.FilterTextSource", remap = false)
public abstract class JeiSearchFormattingMixin {
    @ModifyVariable(method = "setFilterText(Ljava/lang/String;)Z", at = @At("HEAD"),
            argsOnly = true, ordinal = 0, require = 1, allow = 1, remap = false)
    private String enhancedcore$stripSearchFormatting(String filterText) {
        return ChatFormatting.stripFormatting(filterText);
    }
}
