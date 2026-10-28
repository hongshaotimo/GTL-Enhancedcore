package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.lowdragmc.lowdraglib.side.fluid.IFluidTransfer;
import java.util.LinkedHashMap;
import java.util.Map;

/** A transient, read-only matching snapshot; never serialize it as child inventory. */
public final class CloudCatalystSource {
    private CloudCatalystSource() {}

    public static Map<AEKey, Long> snapshot(IItemTransfer items, IFluidTransfer fluids) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (int i = 0; i < items.getSlots(); i++) {
            var stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) add(result, AEItemKey.of(stack), stack.getCount());
        }
        for (int i = 0; i < fluids.getTanks(); i++) {
            var stack = fluids.getFluidInTank(i);
            if (!stack.isEmpty()) add(result, IvRecipeInputs.fluidKey(stack), stack.getAmount());
        }
        return Map.copyOf(result);
    }

    private static void add(Map<AEKey, Long> stock, AEKey key, long count) {
        if (count > 0) stock.merge(key, count, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
    }
}
