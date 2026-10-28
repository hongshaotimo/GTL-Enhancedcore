package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import java.util.List;
import java.util.function.BiConsumer;

/** Keeps the precision-mode restriction beside the waived computation cost. */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionEngravingLaserTooltipMixin {

    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"), remap = false)
    private BiConsumer<ItemStack, List<Component>> gtlcore$engravingLaserPlantTooltip(BiConsumer<ItemStack, List<Component>> upstream) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !"engraving_laser_plant".equals(id.getPath())) {
            return upstream;
        }
        upstream = MachineTooltips.modifyWithRecipes(upstream, "engraving_laser_plant", self.getRecipeTypes());
        return upstream;
    }
}
