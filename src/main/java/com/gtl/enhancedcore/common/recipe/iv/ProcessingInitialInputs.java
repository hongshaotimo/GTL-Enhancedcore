package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

/** Remembers only saturated initial plan inputs eligible for another physical long window. */
public final class ProcessingInitialInputs {
    private static final String KEY = "gtlEnhancedcoreWideInitialInputs";
    private static final BigInteger LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private final Set<AEKey> initial = new LinkedHashSet<>();

    public boolean isEmpty() { return initial.isEmpty(); }

    public void submitted(ICraftingPlan plan) {
        initial.clear();
        for (var entry : plan.usedItems())
            if (entry.getLongValue() == Long.MAX_VALUE) initial.add(entry.getKey());
        for (var pattern : plan.patternTimes().keySet())
            for (var output : pattern.getOutputs()) initial.remove(output.what());
    }

    public void write(CompoundTag tag) {
        var keys = new ListTag();
        for (var key : initial) keys.add(key.toTagGeneric());
        tag.put(KEY, keys);
    }

    public void read(CompoundTag tag) {
        initial.clear();
        var keys = tag.getList(KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < keys.size(); index++) {
            var key = AEKey.fromTagGeneric(keys.getCompound(index));
            if (key != null) initial.add(key);
        }
    }

    public void replenish(IPatternDetails pattern, long requested, ListCraftingInventory inventory,
                          IGrid grid, IActionSource source, Runnable changed) {
        if (initial.isEmpty() || requested <= 0) return;
        var units = new LinkedHashMap<AEKey, BigInteger>();
        for (var input : pattern.getInputs()) {
            var key = input.getPossibleInputs()[0].what();
            if (!initial.contains(key) || AEUtils.isIntegratedCircuit(key)
                    || key instanceof AEItemKey item && GTLItems.VIRTUAL_INGREDIENT.isIn(item.toStack())) continue;
            units.merge(key, BigInteger.valueOf(input.getMultiplier()), BigInteger::add);
        }
        for (var entry : units.entrySet()) {
            var unit = entry.getValue();
            if (unit.compareTo(LONG) > 0) continue;
            var key = entry.getKey();
            long held = inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
            if (held >= unit.longValueExact()) continue;
            long target = unit.multiply(BigInteger.valueOf(requested)).min(LONG).longValueExact();
            long extracted = grid.getStorageService().getInventory().extract(
                    key, target - held, Actionable.MODULATE, source);
            if (extracted > 0) {
                inventory.insert(key, extracted, Actionable.MODULATE);
                changed.run();
            }
        }
    }
}
