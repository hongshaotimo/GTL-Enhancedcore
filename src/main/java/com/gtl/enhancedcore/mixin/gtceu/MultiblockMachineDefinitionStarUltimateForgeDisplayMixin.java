package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * 恒星终极物质锻造工厂 GUI 附加显示（GTL-Enhancedcore）：
 * 原注册 additionalDisplay 硬编码 "1000"，改写为 "50000"（键 gtceu.multiblock.parallel = 同时处理至多%d个配方）。
 */
@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class MultiblockMachineDefinitionStarUltimateForgeDisplayMixin {

    private static final String TARGET_MACHINE = "star_ultimate_material_forge_factory";

    @Inject(method = "getAdditionalDisplay", at = @At("RETURN"), cancellable = true, remap = false)
    private void gtlcore$starUltimateForgeAdditionalDisplay(CallbackInfoReturnable<BiConsumer<IMultiController, List<Component>>> cir) {
        MultiblockMachineDefinition self = (MultiblockMachineDefinition) (Object) this;
        ResourceLocation id = self.getId();
        if (id == null || !"gtceu".equals(id.getNamespace()) || !TARGET_MACHINE.equals(id.getPath())) {
            return;
        }
        BiConsumer<IMultiController, List<Component>> original = cir.getReturnValue();
        cir.setReturnValue((controller, components) -> {
            if (original != null) original.accept(controller, components);
            for (int i = 0; i < components.size(); i++) {
                Component component = components.get(i);
                if (component instanceof MutableComponent mutable
                        && mutable.getContents() instanceof TranslatableContents translatable
                        && "gtceu.multiblock.parallel".equals(translatable.getKey())) {
                    components.set(i, Component.translatable("gtceu.multiblock.parallel",
                            Component.literal("50000").withStyle(ChatFormatting.DARK_PURPLE)).withStyle(ChatFormatting.GRAY));
                }
            }
        });
    }
}
