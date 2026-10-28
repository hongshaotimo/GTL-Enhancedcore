package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.structure.MegastructureMaintenancePolicy;
import com.gtl.enhancedcore.common.structure.SpaceElevatorMaintenance;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = MachineDefinition.class, remap = false)
public abstract class IvNativeTooltipMixin {
    @ModifyReturnValue(method = "getTooltipBuilder", at = @At("RETURN"))
    private BiConsumer<ItemStack, List<Component>> iv$tooltip(BiConsumer<ItemStack, List<Component>> upstream) {
        var definition = (MachineDefinition)(Object)this;
        var id = definition.getId();
        if (id == null) return upstream;
        if (id.getNamespace().equals("gtceu") && id.getPath().equals("suprachronal_assembly_line_module")) {
            upstream = MachineTooltips.modify(upstream, "suprachronal_module");
            return upstream;
        }
        // qft 走 ADD 可变多配方路线（不是 IV 隔离）：只追加「支持跨配方并行 / 拥有 512 条跨配方线程」。
        if (id.getNamespace().equals("gtceu") && id.getPath().equals("qft")) {
            upstream = MachineTooltips.modifyWithRecipes(upstream, "qft", definition.getRecipeTypes());
            return upstream;
        }
        if (id.getNamespace().equals("gtceu") && id.getPath().equals("wood_distillation")) {
            String threadLine = "tooltip.gtl_enhancedcore.wood_distillation.threads";
            upstream = MachineTooltips.modify(upstream, "wood_distillation",
                    java.util.Map.of(threadLine,
                            new Object[]{com.gtl.enhancedcore.common.machine.WoodDistillationMutableMachine.CROSS_RECIPE_THREADS}));
            return upstream;
        }
        // 2026-10-12：三台只加 ADD 跨配方线程（不进 IV 隔离）的机器，同样只追加 ADD 能力提示。
        if (IvMachineScope.addThreadOnly(id)) {
            upstream = MachineTooltips.modifyWithRecipes(upstream,
                    "add_threads", definition.getRecipeTypes());
            return upstream;
        }
        boolean isolated = IvMachineScope.nativeTarget(id);
        boolean maintenance = MegastructureMaintenancePolicy.matches(id.getNamespace(), id.getPath());
        boolean elevator = SpaceElevatorMaintenance.matches(id);
        if (!isolated && !maintenance && !elevator) return upstream;
        if (isolated) {
            upstream = MachineTooltips.modifyNative(upstream, id.getPath(), definition.getRecipeTypes());
        }
        if (maintenance) {
            upstream = MachineTooltips.modifyWithRecipes(upstream, "optional_maintenance", definition.getRecipeTypes());
        }
        if (elevator) {
            upstream = MachineTooltips.modifyWithRecipes(upstream, "forbidden_maintenance", definition.getRecipeTypes());
        }
        return upstream;
    }
}
