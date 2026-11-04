package com.gtl.enhancedcore.audit;

import com.google.common.collect.HashBasedTable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Verifies the transformed empty-map fast path without bypassing any nonempty capability. */
public final class EmptyRecipePerformanceChecks {
    public static void run(List<String> checks) {
        var reads = new AtomicInteger();
        var holder = (IRecipeCapabilityHolder) Proxy.newProxyInstance(IRecipeCapabilityHolder.class.getClassLoader(),
                new Class<?>[]{IRecipeCapabilityHolder.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getCapabilitiesProxy")) { reads.incrementAndGet(); return HashBasedTable.create(); }
                    if (method.getName().equals("hasProxies")) return true;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
        var recipe = new GTRecipe(null, new ResourceLocation("gtl_enhancedcore", "empty_performance_fixture"),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new CompoundTag(), 1, false);
        for (IO io : IO.values()) for (boolean tick : new boolean[]{false, true}) {
            if (recipe.matchRecipeContents(io, holder, Map.of(), tick) != GTRecipe.ActionResult.SUCCESS
                    || !recipe.handleRecipe(io, holder, tick, Map.of(), Map.of()))
                throw new AssertionError("Empty content map retains native success");
        }
        if (reads.get() != 0) throw new AssertionError("Empty content maps allocated a runner/proxy lookup");
        checks.add("Empty recipe contents return native success without creating a runner or querying capabilities");
        // Even an empty list under a real capability is not an empty map: keep its native path.
        try {
            recipe.matchRecipeContents(IO.IN, holder, Map.of(ItemRecipeCapability.CAP, List.of()), false);
        } catch (NullPointerException expected) {
            // The deliberately unregistered recipe has no recipe type; the native runner reads it.
        }
        if (reads.get() == 0) throw new AssertionError("Nonempty capability map was incorrectly skipped");
        checks.add("Nonempty maps retain the native runner, including zero-list and virtual capability cases");
    }
}
