package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gtl.enhancedcore.common.patterncut.PatternCutService;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MEPatternBufferCutBehavior.class, remap = false)
public abstract class IvPatternBufferCutToolMixin {
    @Inject(method = "onItemUseFirst", at = @At("HEAD"), cancellable = true)
    private void iv$storedCut(ItemStack stack, UseOnContext context,
                                 CallbackInfoReturnable<InteractionResult> cir) {
        var machine = MetaMachine.getMachine(context.getLevel(), context.getClickedPos());
        if (!(machine instanceof MEPatternBufferPartMachine) && !(machine instanceof WorkableMultiblockMachine)) return;
        if (context.getPlayer() instanceof ServerPlayer player)
            PatternCutService.use(player, context.getHand(), context.getClickedPos());
        cir.setReturnValue(InteractionResult.sidedSuccess(context.getLevel().isClientSide()));
    }

    @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
    private void iv$transferTip(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag,
                               CallbackInfo ci) {
        tips.add(Component.translatable("gtl_enhancedcore.pattern_cut.hint"));
        tips.add(Component.translatable("gtl_enhancedcore.pattern_cut.storage_hint"));
        tips.add(Component.translatable("tooltip.gtl_enhancedcore.pattern_buffer_cut.iv_idle_transfer"));
        ci.cancel();
    }
}
