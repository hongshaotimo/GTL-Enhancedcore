package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gtl.enhancedcore.common.config.GalaxyConnectionConfig;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyEngineSupport;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = MachineDefinition.class, remap = false, priority = 900)
public abstract class GalaxyDefinitionPolicyMixin {
    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"))
    private BiConsumer<ItemStack, List<Component>> galaxy$capabilities(BiConsumer<ItemStack, List<Component>> upstream) {
        var definition = (MachineDefinition)(Object)this;
        if (!(definition instanceof MultiblockMachineDefinition multiblock)) return upstream;
        var original = upstream;
        upstream = (stack, lines) -> {
            MachineTooltips.appendGalaxyCapability(original, stack, lines,
                    GalaxyConnectionConfig.allows(definition.getId()), GalaxyEngineSupport.supports(multiblock));
        };
        return upstream;
    }
}
