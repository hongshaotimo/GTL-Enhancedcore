package com.gtl.enhancedcore.mixin.jade;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.integration.jade.provider.RecipeLogicProvider;
import com.gtl.enhancedcore.common.machine.WeatherAnchorMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** The anchor has no recipes by design; the dedicated provider reports its field state. */
@Mixin(value = RecipeLogicProvider.class, remap = false)
public abstract class WeatherAnchorRecipeLogicProviderMixin {
    @Inject(method = "addTooltip(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/ITooltip;"
            + "Lnet/minecraft/world/entity/player/Player;Lsnownee/jade/api/BlockAccessor;"
            + "Lnet/minecraft/world/level/block/entity/BlockEntity;Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void gtlEnhancedcore$hideEmptyRecipeFailure(CompoundTag data, ITooltip tooltip, Player player,
                                                        BlockAccessor accessor, BlockEntity blockEntity,
                                                        IPluginConfig config, CallbackInfo ci) {
        if (blockEntity instanceof MetaMachineBlockEntity machine
                && machine.getMetaMachine() instanceof WeatherAnchorMachine) {
            ci.cancel();
        }
    }
}
