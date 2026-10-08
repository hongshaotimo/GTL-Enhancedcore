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
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MachineDefinition.class, remap = false)
public abstract class IvNativeTooltipMixin {
    @Inject(method = "getTooltipBuilder", at = @At("RETURN"), cancellable = true)
    private void iv$tooltip(CallbackInfoReturnable<BiConsumer<ItemStack,List<Component>>> cir) {
        var definition = (MachineDefinition)(Object)this;
        var id = definition.getId();
        if (id == null) return;
        if (id.getNamespace().equals("gtceu") && id.getPath().equals("suprachronal_assembly_line_module")) {
            cir.setReturnValue(MachineTooltips.modify(cir.getReturnValue(), "suprachronal_module"));
            return;
        }
        // qft 走 ADD 可变多配方路线（不是 IV 隔离）：只追加「支持跨配方并行 / 拥有 512 条跨配方线程」。
        if (id.getNamespace().equals("gtceu") && id.getPath().equals("qft")) {
            cir.setReturnValue(MachineTooltips.modifyWithRecipes(cir.getReturnValue(), "qft", definition.getRecipeTypes()));
            return;
        }
        boolean isolated = IvMachineScope.nativeTarget(id);
        boolean maintenance = MegastructureMaintenancePolicy.matches(id.getNamespace(), id.getPath());
        boolean elevator = SpaceElevatorMaintenance.matches(id);
        if (!isolated && !maintenance && !elevator) return;
        if (isolated) {
            cir.setReturnValue(MachineTooltips.modifyNative(cir.getReturnValue(), id.getPath(), definition.getRecipeTypes()));
        }
        if (maintenance) {
            cir.setReturnValue(MachineTooltips.modifyWithRecipes(cir.getReturnValue(), "optional_maintenance", definition.getRecipeTypes()));
        }
        if (elevator) {
            cir.setReturnValue(MachineTooltips.modifyWithRecipes(cir.getReturnValue(), "forbidden_maintenance", definition.getRecipeTypes()));
        }
    }
}
