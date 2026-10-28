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

/** Replaces the obsolete parallel limit instead of appending contradictory tips. */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionStarUltimateForgeTooltipMixin {

    private static final String TARGET_MACHINE = "star_ultimate_material_forge_factory";

    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"), remap = false)
    private BiConsumer<ItemStack, List<Component>> gtlcore$starUltimateForgeTooltip(BiConsumer<ItemStack, List<Component>> upstream) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !TARGET_MACHINE.equals(id.getPath())) {
            return upstream;
        }
        upstream = MachineTooltips.modifyWithRecipes(upstream, "star_ultimate_material_forge", self.getRecipeTypes());
        return upstream;
    }
}
