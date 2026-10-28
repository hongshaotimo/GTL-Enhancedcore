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

@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionFissionTooltipMixin {

    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"), remap = false)
    private BiConsumer<ItemStack, List<Component>> gtlcore$fissionReactorTooltip(BiConsumer<ItemStack, List<Component>> upstream) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !"fission_reactor".equals(id.getPath())) {
            return upstream;
        }
        upstream = MachineTooltips.modifyWithRecipes(upstream, "fission_reactor", self.getRecipeTypes());
        return upstream;
    }
}
