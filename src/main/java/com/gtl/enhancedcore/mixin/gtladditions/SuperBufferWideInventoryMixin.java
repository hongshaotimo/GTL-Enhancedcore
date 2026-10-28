package com.gtl.enhancedcore.mixin.gtladditions;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.gtl.enhancedcore.common.recipe.iv.SuperBufferLongBatch;
import com.gtladd.gtladditions.api.machine.IWidePatternSlot;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.utils.datastructure.Int128;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exposes long-sized batches while the ADD slot retains and consumes its full Int128 stock. */
@Mixin(targets = "com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine$SuperPatternBufferInternalSlot", remap = false)
public abstract class SuperBufferWideInventoryMixin {
    @Inject(method = "getItemInventory", at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$projectItemBatch(CallbackInfoReturnable<Object2LongOpenHashMap<AEItemKey>> cir) {
        Map<AEKey, Int128> stock = ((IWidePatternSlot) (Object) this).wideInputs();
        Object2LongOpenHashMap<AEItemKey> batch = new Object2LongOpenHashMap<>();
        for (var entry : stock.entrySet()) {
            if (entry.getKey() instanceof AEItemKey item) {
                long amount = SuperBufferLongBatch.available(entry.getValue());
                if (amount > 0) batch.put(item, amount);
            }
        }
        cir.setReturnValue(batch);
    }

    @Inject(method = "getFluidInventory", at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$projectFluidBatch(CallbackInfoReturnable<Object2LongOpenHashMap<AEFluidKey>> cir) {
        Map<AEKey, Int128> stock = ((IWidePatternSlot) (Object) this).wideInputs();
        Object2LongOpenHashMap<AEFluidKey> batch = new Object2LongOpenHashMap<>();
        for (var entry : stock.entrySet()) {
            if (entry.getKey() instanceof AEFluidKey fluid) {
                long amount = SuperBufferLongBatch.available(entry.getValue());
                if (amount > 0) batch.put(fluid, amount);
            }
        }
        cir.setReturnValue(batch);
    }

    @Inject(method = "getItemStackInputMap", at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$projectItemStackBatch(CallbackInfoReturnable<Object2LongMap<ItemStack>> cir) {
        Object2LongOpenHashMap<ItemStack> batch = new Object2LongOpenHashMap<>();
        for (var entry : ((IWidePatternSlot) (Object) this).wideInputs().entrySet()) {
            if (entry.getKey() instanceof AEItemKey item) {
                long amount = SuperBufferLongBatch.available(entry.getValue());
                if (amount > 0) batch.put(item.toStack((int)Math.min(Integer.MAX_VALUE, amount)), amount);
            }
        }
        cir.setReturnValue(batch);
    }

    @Inject(method = "getFluidStackInputMap", at = @At("RETURN"), cancellable = true, require = 1, allow = 1)
    private void enhancedcore$projectFluidStackBatch(CallbackInfoReturnable<Object2LongMap<FluidStack>> cir) {
        Object2LongOpenHashMap<FluidStack> batch = new Object2LongOpenHashMap<>();
        for (var entry : ((IWidePatternSlot) (Object) this).wideInputs().entrySet()) {
            if (entry.getKey() instanceof AEFluidKey fluid) {
                long amount = SuperBufferLongBatch.available(entry.getValue());
                if (amount > 0) batch.put(FluidStack.create(fluid.getFluid(), amount, fluid.getTag()), amount);
            }
        }
        cir.setReturnValue(batch);
    }

}
