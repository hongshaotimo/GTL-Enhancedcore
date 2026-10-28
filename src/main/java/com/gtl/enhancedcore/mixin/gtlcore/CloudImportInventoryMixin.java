package com.gtl.enhancedcore.mixin.gtlcore;

import com.gtl.enhancedcore.common.cloud.transfer.CloudImportInventoryGuard;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ItemStackTransfer.class, remap = false)
public abstract class CloudImportInventoryMixin {
    @Inject(method = "getStackInSlot", at = @At("HEAD"), cancellable = true)
    private void cloud$blockRead(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (CloudImportInventoryGuard.blocked(this)) cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void cloud$blockExtract(int slot, int count, boolean simulate, boolean notify, CallbackInfoReturnable<ItemStack> cir) {
        if (CloudImportInventoryGuard.blocked(this)) cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void cloud$blockInsert(int slot, ItemStack stack, boolean simulate, boolean notify, CallbackInfoReturnable<ItemStack> cir) {
        if (CloudImportInventoryGuard.blocked(this)) cir.setReturnValue(stack);
    }
    @Inject(method = "setStackInSlot", at = @At("HEAD"), cancellable = true)
    private void cloud$blockSet(int slot, ItemStack stack, CallbackInfo ci) { if (CloudImportInventoryGuard.blocked(this)) ci.cancel(); }
}
