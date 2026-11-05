package com.gtl.enhancedcore.common.cloud;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.gtlcore.gtlcore.common.data.GTLItems;

/** Replenish a cloud CPU's physical inputs when one long-valued batch has exhausted them. */
public final class CloudCpuRefill {
    private CloudCpuRefill() {}

    public static void replenish(IPatternDetails pattern, long remaining, ListCraftingInventory inventory,
                                 CraftingService service, IGrid grid, IActionSource source, Runnable changed) {
        if (remaining < 1 || grid == null || source == null) return;
        Map<AEKey, Long> units = physicalUnits(pattern);
        if (units == null || units.isEmpty() || units.entrySet().stream()
                .noneMatch(input -> inventory.list.get(input.getKey()) < input.getValue())) return;

        var providers = new ArrayList<CloudPatternBufferMachine>();
        for (var provider : service.getProviders(pattern))
            if (provider instanceof CloudPatternBufferMachine cloud) providers.add(cloud);
        if (providers.isEmpty()) return;

        // Downstream tasks may wait for products absent from both CPU and ME.
        // Keep partial refills: any available physical input enables the full original path.
        var storage = grid.getStorageService().getInventory();
        boolean refillAvailable = false;
        for (var key : units.keySet()) if (storage.extract(key, 1, Actionable.SIMULATE, source) > 0) {
            refillAvailable = true;
            break;
        }
        if (!refillAvailable) return;

        long batch = 0;
        for (var candidate : providers) {
            if (candidate.getAvailablePatterns().contains(pattern)) {
                batch = Math.max(batch, candidate.cloudDispatchCapacity(pattern, remaining));
            }
        }
        if (batch < 1) return;

        for (var input : units.entrySet()) {
            AEKey key = input.getKey();
            long request = CloudOrderCapacity.refillAmount(inventory.list.get(key), input.getValue(), batch);
            if (request < 1) continue;
            long extracted = storage.extract(key, request, Actionable.MODULATE, source);
            if (extracted > 0) {
                inventory.insert(key, extracted, Actionable.MODULATE);
                changed.run();
            }
        }
    }

    public static long availableOperations(IPatternDetails pattern, long requested, ListCraftingInventory inventory) {
        Map<AEKey, Long> units = physicalUnits(pattern);
        if (units == null) return 0;
        long available = requested;
        for (var input : units.entrySet())
            available = Math.min(available, inventory.list.get(input.getKey()) / input.getValue());
        return available;
    }

    private static Map<AEKey, Long> physicalUnits(IPatternDetails pattern) {
        Map<AEKey, Long> units = new LinkedHashMap<>();
        for (var input : pattern.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1 || input.getMultiplier() < 1) return null;
            AEKey key = possible[0].what();
            if (IvBuffers.isCircuit(key) || key instanceof AEItemKey item
                    && GTLItems.VIRTUAL_INGREDIENT.isIn(item.toStack())) continue;
            units.merge(key, input.getMultiplier(), Math::addExact);
        }
        return units;
    }
}
