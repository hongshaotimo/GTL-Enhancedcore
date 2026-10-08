package com.gtl.enhancedcore.mixin.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 让 {@code gtceu:engraving_laser_plant}（激光蚀刻工厂）不消耗算力（CWU）。
 * <p>
 * 原理：当该机器装配的 COMPUTATION_DATA_RECEPTION 仓尝试消耗 CWU 时，
 * 直接返回 null（完全满足），跳过实际从光学网络请求 CWU。
 */
@Mixin(NotifiableComputationContainer.class)
public class NotifiableComputationContainerMixin {

    private static final ResourceLocation ENGRAVING_LASER_PLANT_ID = new ResourceLocation("gtceu", "engraving_laser_plant");

    @Inject(method = "handleRecipeInner", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$skipCWUForLaserPlant(IO io, GTRecipe recipe, List<Integer> left, String slotName, boolean simulate,
                                               CallbackInfoReturnable<List<Integer>> cir) {
        if (io != IO.IN) return;
        NotifiableComputationContainer self = (NotifiableComputationContainer) (Object) this;
        MetaMachine machine = self.getMachine();
        // 检查是否是 engraving_laser_plant 的控制器
        if (machine instanceof IMultiPart multiPart) {
            boolean target = false;
            for (IMultiController controller : multiPart.getControllers()) {
                if (controller == null || !controller.isFormed()) continue;
                // A shared hatch must not grant free computation to an unrelated controller.
                if (!ENGRAVING_LASER_PLANT_ID.equals(controller.self().getDefinition().getId())) return;
                target = true;
            }
            if (target) cir.setReturnValue(null);
        }
    }
}
