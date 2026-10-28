package com.gtl.enhancedcore.mixin.gtladditions;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferLongBatch;
import com.gtladd.gtladditions.api.machine.IWidePatternSlot;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.utils.datastructure.Int128;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps ADD wide-buffer limit displays positive and includes inherited catalyst/virtual inputs. */
@Pseudo
@Mixin(targets = "org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase$InternalSlot", remap = false)
public abstract class SuperBufferWideLimitInventoryMixin {
    @Shadow protected abstract void appendLimitItemCatalystInputs(ObjectList<ItemStack> target);
    @Shadow protected abstract void appendLimitFluidCatalystInputs(ObjectList<FluidStack> target);
    @Shadow protected abstract void appendVirtualItemInputs(ObjectList<ItemStack> target);
    @Shadow protected abstract void appendVirtualFluidInputs(ObjectList<FluidStack> target);

    @Inject(method = "getLimitItemStackInput", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$projectLimitItemBatch(CallbackInfoReturnable<ObjectList<ItemStack>> cir) {
        if (!((Object) this instanceof IWidePatternSlot slot)) return;
        ObjectArrayList<ItemStack> batch = new ObjectArrayList<>();
        for (Map.Entry<appeng.api.stacks.AEKey, Int128> entry : slot.wideInputs().entrySet()) {
            if (entry.getKey() instanceof AEItemKey item) {
                long amount = SuperBufferLongBatch.available(entry.getValue());
                if (amount > 0) batch.add(item.toStack((int) Math.min(Integer.MAX_VALUE, amount)));
            }
        }
        appendLimitItemCatalystInputs(batch);
        appendVirtualItemInputs(batch);
        cir.setReturnValue(batch);
    }

    @Inject(method = "getLimitFluidStackInput", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$projectLimitFluidBatch(CallbackInfoReturnable<ObjectList<FluidStack>> cir) {
        if (!((Object) this instanceof IWidePatternSlot slot)) return;
        ObjectArrayList<FluidStack> batch = new ObjectArrayList<>();
        for (Map.Entry<appeng.api.stacks.AEKey, Int128> entry : slot.wideInputs().entrySet()) {
            if (entry.getKey() instanceof AEFluidKey fluid) {
                long amount = SuperBufferLongBatch.available(entry.getValue());
                if (amount > 0) batch.add(FluidStack.create(fluid.getFluid(), amount, fluid.getTag()));
            }
        }
        appendLimitFluidCatalystInputs(batch);
        appendVirtualFluidInputs(batch);
        cir.setReturnValue(batch);
    }
}
