package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import com.gtl.enhancedcore.common.recipe.MachineRecipeModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Adds buffer support without a second animated attribution line. */
@Mixin(value = MachineDefinition.class, remap = false)
public abstract class MachineDefinitionPatternBufferTooltipMixin {

    private static final Set<String> TARGET_IDS = Set.of(
            "assembly_line", "circuit_assembly_line"
    );

    @Inject(method = "getTooltipBuilder", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$patternBufferTooltip(CallbackInfoReturnable<BiConsumer<ItemStack, List<Component>>> cir) {
        MachineDefinition self = (MachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !TARGET_IDS.contains(id.getPath())) {
            return;
        }
        if ("assembly_line".equals(id.getPath())) {
            cir.setReturnValue(MachineTooltips.modifyWithRecipes(cir.getReturnValue(), "assembly_line",
                    Map.of("tooltip.gtl_enhancedcore.assembly_line.parallel_fixed",
                            new Object[]{MachineRecipeModifiers.ASSEMBLY_LINE_PARALLEL}), self.getRecipeTypes()));
        } else {
            cir.setReturnValue(MachineTooltips.modifyWithRecipes(cir.getReturnValue(), "pattern_buffer", self.getRecipeTypes()));
        }
    }
}
