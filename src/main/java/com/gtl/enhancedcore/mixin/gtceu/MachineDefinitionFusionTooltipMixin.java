package com.gtl.enhancedcore.mixin.gtceu;

import com.gtl.enhancedcore.common.recipe.FusionParallelPolicy;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionFusionTooltipMixin {

    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"), remap = false)
    private BiConsumer<ItemStack, List<Component>> gtlcore$fusionReactorTooltip(BiConsumer<ItemStack, List<Component>> upstream) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null) {
            return upstream;
        }
        final int parallel = FusionParallelPolicy.limit(id.getNamespace(), id.getPath());
        if (parallel == 0) return upstream;
        upstream = MachineTooltips.modifyWithRecipes(upstream, "fusion_reactor", Map.of(
                "tooltip.gtl_enhancedcore.fusion_reactor.max_parallel", new Object[]{parallel}), self.getRecipeTypes());
        return upstream;
    }
}
