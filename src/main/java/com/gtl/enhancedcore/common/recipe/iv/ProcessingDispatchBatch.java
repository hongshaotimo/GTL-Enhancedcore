package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToLongFunction;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

/** Sizes whole processing operations before the CPU extracts any long-valued material. */
public final class ProcessingDispatchBatch {
    private static final BigInteger LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private ProcessingDispatchBatch() {}

    public static long limit(IPatternDetails pattern, long requested, ICraftingInventory inventory,
                             ToLongFunction<AEKey> waiting) {
        if (requested <= 0) return 0;
        Map<AEKey, BigInteger> inputs = new LinkedHashMap<>();
        Map<AEKey, BigInteger> fixed = new LinkedHashMap<>();
        Map<AEKey, BigInteger> outputs = new LinkedHashMap<>();
        for (var input : pattern.getInputs()) {
            AEKey key = input.getPossibleInputs()[0].what();
            (AEUtils.isIntegratedCircuit(key) ? fixed : inputs)
                    .merge(key, BigInteger.valueOf(input.getMultiplier()), BigInteger::add);
        }
        for (var output : pattern.getOutputs())
            outputs.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
        long operations = requested;
        for (var input : fixed.entrySet())
            if (input.getValue().compareTo(BigInteger.valueOf(inventory.extract(input.getKey(), Long.MAX_VALUE, Actionable.SIMULATE))) > 0)
                return 0;
        for (var input : inputs.entrySet()) {
            long available = inventory.extract(input.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
            operations = Math.min(operations, BigInteger.valueOf(available).divide(input.getValue()).longValueExact());
        }
        for (var output : outputs.entrySet()) {
            long held = waiting.applyAsLong(output.getKey());
            if (held < 0) return 0;
            operations = Math.min(operations, LONG.subtract(BigInteger.valueOf(held)).divide(output.getValue()).longValueExact());
        }
        return operations;
    }

    /** Upstream forces at least one operation; reject a zero-capacity dispatch before any extraction. */
    public static KeyCounter[] extract(IPatternDetails pattern, ICraftingInventory inventory,
                                       KeyCounter expected, long operations, ToLongFunction<AEKey> waiting) {
        if (operations <= 0 || limit(pattern, operations, inventory, waiting) < operations) return null;
        return AEUtils.extractForProcessingPattern(pattern, inventory, expected, operations);
    }
}
