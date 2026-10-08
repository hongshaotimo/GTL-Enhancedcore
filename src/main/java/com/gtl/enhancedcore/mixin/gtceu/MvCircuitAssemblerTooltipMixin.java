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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MvCircuitAssemblerTooltipMixin {
    @Inject(method = "getTooltipBuilder", at = @At("RETURN"), cancellable = true, remap = false)
    private void enhancedcore$mvCircuitTip(CallbackInfoReturnable<BiConsumer<ItemStack, List<Component>>> cir) {
        var definition = (MachineDefinition) (Object) this;
        if (definition.getId() == null || !MvCircuitAssemblerRecipe.ID.equals(definition.getId().toString())) return;
        var original = cir.getReturnValue();
        cir.setReturnValue((stack, lines) -> {
            if (original != null) original.accept(stack, lines);
            lines.add(PlayerTooltipStyles.rainbow("tooltip.gtl_enhancedcore.credit_modified"));
            lines.add(Component.translatable("tooltip.gtl_enhancedcore.mv_circuit_assembler.circuit"));
        });
    }
}
