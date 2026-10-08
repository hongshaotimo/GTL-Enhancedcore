package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.BiConsumer;

/** Keeps the precision-mode restriction beside the waived computation cost. */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionEngravingLaserTooltipMixin {

    @Inject(method = "getTooltipBuilder", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$engravingLaserPlantTooltip(CallbackInfoReturnable<BiConsumer<ItemStack, List<Component>>> cir) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !"engraving_laser_plant".equals(id.getPath())) {
            return;
        }
        cir.setReturnValue(MachineTooltips.modifyWithRecipes(cir.getReturnValue(), "engraving_laser_plant", self.getRecipeTypes()));
    }
}
