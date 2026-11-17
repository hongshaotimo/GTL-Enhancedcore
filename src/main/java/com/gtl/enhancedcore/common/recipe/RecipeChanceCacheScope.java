package com.gtl.enhancedcore.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Keeps a candidate's chance remainder changes private until its inputs have been paid. */
public final class RecipeChanceCacheScope implements AutoCloseable {
    private final Map<RecipeCapability<?>, Object2IntMap<?>> live;
    private final Map<RecipeCapability<?>, Object2IntMap<?>> originals;
    private final Map<RecipeCapability<?>, Object2IntMap<?>> scratch;
    private boolean committed;

    public RecipeChanceCacheScope(RecipeLogic logic) {
        this(logic, null);
    }

    public RecipeChanceCacheScope(RecipeLogic logic, GTRecipe source) {
        boolean chancedInput = source == null || source.inputs.entrySet().stream().anyMatch(entry -> entry.getKey().doMatchInRecipe()
                && entry.getValue().stream().anyMatch(content -> content.chance > 0 && content.chance < content.maxChance));
        boolean chancedOutput = source == null || source.outputs.values().stream().anyMatch(contents ->
                contents.stream().anyMatch(content -> content.chance < content.maxChance));
        if (!chancedInput && !chancedOutput) {
            live = originals = scratch = null;
            return;
        }
        live = logic.getChanceCaches();
        originals = new IdentityHashMap<>(live);
        scratch = new IdentityHashMap<>();
        originals.forEach((capability, cache) -> scratch.put(capability, copy(capability, cache)));
        live.clear();
        live.putAll(scratch);
    }

    public void commit() { committed = true; }

    @Override public void close() {
        if (live == null) return;
        var accepted = committed ? new IdentityHashMap<>(live) : null;
        live.clear();
        if (!committed) {
            live.putAll(originals);
            return;
        }
        // Native setup may intentionally clear cache entries when changing recipes.
        accepted.forEach((capability, cache) -> {
            var original = originals.get(capability);
            if (original != null) {
                replace(original, cache);
                live.put(capability, original);
            } else live.put(capability, cache);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> Object2IntMap<T> copy(RecipeCapability<T> capability, Object2IntMap<?> source) {
        var result = capability.makeChanceCache();
        result.defaultReturnValue(source.defaultReturnValue());
        result.putAll((Object2IntMap<T>) source);
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replace(Object2IntMap target, Object2IntMap source) {
        target.clear();
        target.defaultReturnValue(source.defaultReturnValue());
        target.putAll(source);
    }
}
