package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtl.enhancedcore.common.recipe.iv.ProcessingInitialInputs;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.gtlcore.gtlcore.common.data.GTLItems;

/** Real CPU inventory with a counted in-memory AE storage boundary; no world or network. */
public final class ProcessingInitialInputsChecks {
    private ProcessingInitialInputsChecks() {}

    public static void run(List<String> checks) throws Exception {
        var water = AEFluidKey.of(Fluids.WATER);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var circuit = AEItemKey.of(IntCircuitBehaviour.stack(1));
        var virtual = AEItemKey.of(GTLItems.VIRTUAL_INGREDIENT.asStack());
        var used = new KeyCounter(); used.add(water, Long.MAX_VALUE); used.add(iron, 17);
        used.add(circuit, Long.MAX_VALUE); used.add(virtual, Long.MAX_VALUE);
        var state = new ProcessingInitialInputs(); state.submitted(plan(used, gold));
        var pattern = pattern(new GenericStack(water, 1000), new GenericStack(iron, 1),
                new GenericStack(gold, 1), new GenericStack(circuit, 1), new GenericStack(virtual, 1));
        var inventory = new ListCraftingInventory(key -> {});
        var stock = new HashMap<AEKey, Long>();
        for (var key : List.<AEKey>of(water, iron, gold, circuit, virtual)) stock.put(key, Long.MAX_VALUE);
        var calls = new AtomicInteger(); var changes = new AtomicInteger();
        var source = IActionSource.empty();
        var storage = (MEStorage) Proxy.newProxyInstance(MEStorage.class.getClassLoader(),
                new Class<?>[] {MEStorage.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("extract"))
                        throw new AssertionError("Unexpected storage method " + method.getName());
                    require(arguments[2] == Actionable.MODULATE && arguments[3] == source,
                            "Initial refill uses MODULATE and the original CPU action source", checks);
                    var key = (AEKey) arguments[0]; long requested = (Long) arguments[1];
                    require(key.equals(water) && requested > 0,
                            "Only saturated initial physical input reaches ME extraction", checks);
                    calls.incrementAndGet();
                    long taken = Math.min(requested, stock.getOrDefault(key, 0L));
                    stock.put(key, stock.getOrDefault(key, 0L) - taken);
                    return taken;
                });
        var service = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[] {IStorageService.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getInventory")) return storage;
                    throw new AssertionError("Unexpected storage service method " + method.getName());
                });
        var grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(),
                new Class<?>[] {IGrid.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getStorageService") || method.getName().equals("getService"))
                        return service;
                    throw new AssertionError("Unexpected grid method " + method.getName());
                });
        inventory.insert(water, 807, Actionable.MODULATE);
        state.replenish(pattern, Long.MAX_VALUE, inventory, grid, source, changes::incrementAndGet);
        require(inventory.list.get(water) == Long.MAX_VALUE && calls.get() == 1 && changes.get() == 1
                        && stock.get(water) == 807,
                "Initial MAX input fills only remaining long-window space with exact conservation", checks);
        state.replenish(pattern, Long.MAX_VALUE, inventory, grid, source, changes::incrementAndGet);
        require(calls.get() == 1, "Refill does not prefetch while a complete operation is available", checks);
        inventory.extract(water, Long.MAX_VALUE, Actionable.MODULATE);
        stock.put(water, Long.MAX_VALUE);
        var tag = new CompoundTag(); state.write(tag);
        require(tag.getList("gtlEnhancedcoreWideInitialInputs", 10).size() == 3,
                "Only initial keys whose plan count is exactly MAX are persisted", checks);
        var restored = new ProcessingInitialInputs(); restored.read(tag);
        restored.replenish(pattern, Long.MAX_VALUE, inventory, grid, source, changes::incrementAndGet);
        require(inventory.list.get(water) == Long.MAX_VALUE && calls.get() == 2 && changes.get() == 2,
                "NBT-restored initial key admits a second independently accounted long window", checks);
        inventory.clear(); stock.put(water, 500L);
        inventory.insert(water, 807, Actionable.MODULATE);
        restored.replenish(pattern, 2, inventory, grid, source, changes::incrementAndGet);
        require(inventory.list.get(water) == 1307 && stock.get(water) == 0
                        && calls.get() == 3 && changes.get() == 3,
                "Partial ME extraction credits only the actual returned amount", checks);
        require(inventory.list.get(iron) == 0 && inventory.list.get(gold) == 0
                        && inventory.list.get(circuit) == 0 && inventory.list.get(virtual) == 0,
                "Ordinary initial material, intermediate output, circuit and virtual token are not replenished", checks);
        inventory.clear(); stock.put(water, Long.MAX_VALUE);
        restored.replenish(pattern(new GenericStack(water, Long.MAX_VALUE), new GenericStack(water, 1)),
                1, inventory, grid, source, changes::incrementAndGet);
        require(calls.get() == 3, "Repeated same-key unit exceeding MAX cannot trigger a partial operation refill", checks);
        restored.replenish(pattern, 0, inventory, grid, source, changes::incrementAndGet);
        require(calls.get() == 3, "Zero requested operations do not access storage", checks);
        var mixedInitial = new KeyCounter(); mixedInitial.add(water, Long.MAX_VALUE);
        var producer = new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(
                appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[] {new GenericStack(iron, 1)},
                        new GenericStack[] {new GenericStack(water, 1000)})));
        state.submitted(plan(mixedInitial, gold, Map.of(producer, 1L)));
        state.replenish(pattern, Long.MAX_VALUE, inventory, grid, source, changes::incrementAndGet);
        require(calls.get() == 3 && inventory.list.get(water) == 0,
                "Initial MAX key also produced by the plan is never replenished as an initial-only material", checks);
        var mixedTag = new CompoundTag(); state.write(mixedTag);
        require(mixedTag.getList("gtlEnhancedcoreWideInitialInputs", 10).isEmpty(),
                "Plan-produced initial key is excluded from the persisted refill whitelist", checks);
        restored.read(new CompoundTag());
        restored.replenish(pattern, Long.MAX_VALUE, inventory, grid, source, changes::incrementAndGet);
        require(calls.get() == 3, "Missing persisted whitelist does not infer initial keys from a pattern", checks);
        var ordinary = new KeyCounter(); ordinary.add(water, 10);
        state.submitted(plan(ordinary, gold));
        state.replenish(pattern, Long.MAX_VALUE, inventory, grid, source, changes::incrementAndGet);
        require(calls.get() == 3, "Submitting a normal plan clears the previous saturated-input whitelist", checks);
    }

    private static ICraftingPlan plan(KeyCounter used, AEKey output) {
        return plan(used, output, Map.of());
    }

    private static ICraftingPlan plan(KeyCounter used, AEKey output, Map<IPatternDetails, Long> patterns) {
        return new ICraftingPlan() {
            public GenericStack finalOutput() { return new GenericStack(output, 1); }
            public long bytes() { return 1; }
            public boolean simulation() { return false; }
            public boolean multiplePaths() { return false; }
            public KeyCounter usedItems() { return used; }
            public KeyCounter emittedItems() { return new KeyCounter(); }
            public KeyCounter missingItems() { return new KeyCounter(); }
            public Map<IPatternDetails, Long> patternTimes() { return patterns; }
        };
    }

    private static IPatternDetails pattern(GenericStack... inputs) {
        return new IPatternDetails() {
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            public GenericStack[] getOutputs() { return new GenericStack[] {new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)}; }
            public boolean supportsPushInputsToExternalInventory() { return true; }
            public IInput[] getInputs() {
                return Arrays.stream(inputs).map(stack -> new IInput() {
                    public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(stack.what(), 1)}; }
                    public long getMultiplier() { return stack.amount(); }
                    public boolean isValid(AEKey key, Level level) { return stack.what().equals(key); }
                    public AEKey getRemainingKey(AEKey key) { return null; }
                }).toArray(IInput[]::new);
            }
        };
    }

    private static void require(boolean value, String label, List<String> checks) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }
}
