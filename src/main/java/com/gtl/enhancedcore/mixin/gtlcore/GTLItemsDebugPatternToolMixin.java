package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gtl.enhancedcore.common.registration.RetiredDebugPatternTool;
import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import net.minecraft.world.item.Item;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = GTLItems.class, remap = false)
public abstract class GTLItemsDebugPatternToolMixin {
    @Redirect(method = "<clinit>",
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=debug_pattern_test"),
                    to = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
                            target = "Lorg/gtlcore/gtlcore/common/data/GTLItems;DEBUG_PATTERN_TEST:Lcom/tterrag/registrate/util/entry/ItemEntry;")),
            at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/registry/registrate/GTRegistrate;item(Ljava/lang/String;Lcom/tterrag/registrate/util/nullness/NonNullFunction;)Lcom/tterrag/registrate/builders/ItemBuilder;"),
            require = 1, allow = 1)
    private static ItemBuilder<ComponentItem, GTRegistrate> gtlEnhancedcore$removeDebugTool(
            GTRegistrate owner, String name, NonNullFunction<Item.Properties, ComponentItem> factory) {
        return RetiredDebugPatternTool.replacementBuilder(owner, name);
    }
}
