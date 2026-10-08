package com.gtl.enhancedcore.common.recipe.iv;

import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;

/** A lexical simulation scope only. Removed in finally; never used by normal recipe execution. */
public final class IvNativeMatchContext {
    private final WorkableElectricMultiblockMachine machine;
    private final Map<AEKey, Long> input, virtual;
    private final long operations, capacity;
    private long reserved = 1;
    public IvNativeMatchContext(WorkableElectricMultiblockMachine machine, Map<AEKey, Long> input,
                                Map<AEKey, Long> virtual, long operations, long capacity) {
        if (operations < 1 || capacity < 1) throw new IllegalArgumentException("Invalid native simulation limits");
        this.machine=Objects.requireNonNull(machine); this.input=Objects.requireNonNull(input);
        this.virtual=Objects.requireNonNull(virtual); this.operations=operations; this.capacity=capacity;
    }
    public int capacity(int requested) { return (int)Math.min(Math.max(0, requested), capacity); }
    public long reserved() { return reserved; }
    public void reserved(long value) {
        if (value < 0 || value > capacity) throw new IllegalStateException("Native modifier exceeded simulation capacity");
        reserved = Math.max(1, value);
    }
    private static final ThreadLocal<IvNativeMatchContext> CURRENT = new ThreadLocal<>();
    public static IvNativeMatchContext forMachine(Object machine) {
        var current = CURRENT.get();
        return current != null && current.machine == machine ? current : null;
    }
    public <T> T simulate(Supplier<T> action) {
        var previous = CURRENT.get();
        CURRENT.set(this);
        try { return action.get(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public long limit(GTRecipe recipe, long requested) {
        long existing = Math.max(1, IGTRecipe.of(recipe).getRealParallels());
        return IvRecipeInputs.maxParallel(recipe, input, virtual, Math.min(Math.max(0, requested), operations / existing));
    }
}
