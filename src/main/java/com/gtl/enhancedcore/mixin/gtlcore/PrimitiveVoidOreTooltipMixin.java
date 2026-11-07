package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.function.BiConsumer;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/** Replaces the upstream location-based tooltip with the circuit routing rule. */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class PrimitiveVoidOreTooltipMixin {
    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"), remap = false)
    private BiConsumer<ItemStack, List<Component>> enhancedcore$tooltip(BiConsumer<ItemStack, List<Component>> original) {
        MachineDefinition definition = (MachineDefinition) (Object) this;
        if (definition.getId() == null || !"gtceu:primitive_void_ore".equals(definition.getId().toString())) return original;
        return MachineTooltips.modify(original, "primitive_void_ore");
    }
}
