package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.recipe.MvCircuitAssemblerRecipe;
import com.gtl.enhancedcore.common.util.PlayerTooltipStyles;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MvCircuitAssemblerTooltipMixin {
    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"), remap = false)
    private BiConsumer<ItemStack, List<Component>> enhancedcore$mvCircuitTip(BiConsumer<ItemStack, List<Component>> upstream) {
        var definition = (MachineDefinition) (Object) this;
        if (definition.getId() == null || !MvCircuitAssemblerRecipe.ID.equals(definition.getId().toString())) return upstream;
        var original = upstream;
        upstream = (stack, lines) -> {
            if (original != null) original.accept(stack, lines);
            lines.add(PlayerTooltipStyles.rainbow("tooltip.gtl_enhancedcore.credit_modified"));
            lines.add(Component.translatable("tooltip.gtl_enhancedcore.mv_circuit_assembler.circuit"));
        };
        return upstream;
    }
}
