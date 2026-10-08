package com.gtl.enhancedcore.mixin.gtlcore;

import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferTransfer;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
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
    private void iv$explainGuard(ItemStack stack, UseOnContext context,
                                 CallbackInfoReturnable<InteractionResult> cir) {
        if (!(context.getPlayer() instanceof ServerPlayer player)
                || !(MetaMachine.getMachine(context.getLevel(), context.getClickedPos())
                    instanceof MEPatternBufferPartMachine buffer)
                || !IvBufferTransfer.guarded(buffer)) return;
        boolean hasPayload = MEPatternBufferCutBehavior.hasCutData(stack);
        boolean allowed;
        if (player.isShiftKeyDown()) {
            if (hasPayload) return; // Keep the upstream "tool already holds patterns" message.
            allowed = IvBufferTransfer.allowCut(buffer);
        } else {
            if (!hasPayload) return;
            allowed = IvBufferTransfer.allowPaste(buffer, ToolHelper.getBehaviorsTag(stack).getCompound("cut"));
        }
        if (!allowed) {
            player.displayClientMessage(Component.translatable(IvBuffers.state(buffer).message), true);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    @Inject(method = "appendHoverText", at = @At("RETURN"))
    private void iv$transferTip(ItemStack stack, Level level, List<Component> tips, TooltipFlag flag,
                               CallbackInfo ci) {
        tips.add(Component.translatable("tooltip.gtl_enhancedcore.pattern_buffer_cut.iv_idle_transfer"));
    }
}
