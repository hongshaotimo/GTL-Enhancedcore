package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gtl.enhancedcore.common.performance.TerminalDismantleTasks;
import net.minecraft.world.entity.player.Player;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;
import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior.AutoBuildSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Queue before ADD's default-priority HEAD callback can restore blocks synchronously.
@Mixin(value = AdvancedBlockPattern.class, priority = 1500, remap = false)
public abstract class UltimateTerminalDismantleMixin {
    @Inject(method = "dismantleMultiblock", at = @At("HEAD"), cancellable = true, remap = false)
    private void enhancedcore$queueDismantle(IMultiController controller, Player player,
            AutoBuildSetting setting, CallbackInfo ci) {
        TerminalDismantleTasks.submit((AdvancedBlockPattern) (Object) this, controller, player, setting);
        ci.cancel();
    }

    @Inject(method = "autoBuild", at = @At("HEAD"), cancellable = true, remap = false)
    private void enhancedcore$avoidBuildingDuringDismantle(Player player, MultiblockState state,
            AutoBuildSetting setting, CallbackInfo ci) {
        if (TerminalDismantleTasks.busy(player)) ci.cancel();
    }
}
