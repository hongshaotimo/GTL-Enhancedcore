package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingCPU;

/** Invokes the hooks on actual transformed CPU logic; no world, provider push or AE grid. */
public final class ProcessingCpuHookChecks {
    private static final long REQUESTED = 9_200_000_000_000_000_000L;
    private ProcessingCpuHookChecks() {}

    public static void run(List<String> checks) throws Exception {
        var definition = GTRegistries.MACHINES.get(new ResourceLocation(
                "gtladditions:me_super_pattern_buffer"));
        require(definition != null, "CPU hook audit finds real registered super buffer", checks);
        var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(
                BlockPos.ZERO, definition.defaultBlockState());
        require(holder != null && holder.getLevel() == null,
                "CPU hook provider holder has no world", checks);
        var provider = (ICraftingProvider) holder.getMetaMachine();
        var pattern = new Pattern();
        var ordinary = new CraftingCPUCluster(BlockPos.ZERO, BlockPos.ZERO).craftingLogic;
        installOrdinaryJob(ordinary, pattern);
        audit(ordinary, "iv$rememberOrder", "Ordinary CPU", provider, pattern, checks);
        var transfinite = new TransfiniteCraftingCPU(null,
                UUID.fromString("eae81b8a-b5d4-40e8-8449-e4d129f26345"), Long.MAX_VALUE).getCraftingLogic();
        audit(transfinite, "iv$remember", "Transfinite CPU", provider, pattern, checks);
    }

    private static void audit(Object logic, String rememberName, String label, ICraftingProvider provider,
                              Pattern pattern, List<String> checks) throws Exception {
        var inventory = (ListCraftingInventory) find(logic.getClass(), "inventory").get(logic);
        var remember = hook(logic.getClass(), rememberName, 4);
        boolean baseline = Boolean.getBoolean("gtl.processingDispatch.baseline");
        Method extract = baseline ? null : hook(logic.getClass(), "iv$extractBatch", 4);
        for (long available : new long[] {1009L, Long.MAX_VALUE / 1000L}) {
            inventory.clear();
            long water = available == 1009L ? 1_009_999L : Long.MAX_VALUE;
            long iron = available == 1009L ? 1009L : Long.MAX_VALUE;
            inventory.insert(pattern.iron, iron, Actionable.MODULATE);
            inventory.insert(pattern.water, water, Actionable.MODULATE);
            long operations = (Long) remember.invoke(logic, true, provider, pattern, REQUESTED);
            require(operations == (baseline ? REQUESTED : available),
                    label + " actual remember hook " + (baseline ? "retains oversized request"
                    : "sizes whole operations to available non-unit fluid input") + " = " + operations, checks);
            require(inventory.extract(pattern.iron, Long.MAX_VALUE, Actionable.SIMULATE) == iron
                            && inventory.extract(pattern.water, Long.MAX_VALUE, Actionable.SIMULATE) == water,
                    label + " remembering a batch does not consume CPU stock", checks);
            var expected = new KeyCounter();
            var inputs = baseline ? AEUtils.extractForProcessingPattern(pattern, inventory, expected, operations)
                    : (KeyCounter[]) extract.invoke(logic, pattern, inventory, expected, operations);
            if (baseline && available == 1009L) {
                require(inputs == null && expected.isEmpty(),
                        label + " baseline oversized extraction rejects available smaller batch", checks);
                require(inventory.extract(pattern.iron, Long.MAX_VALUE, Actionable.SIMULATE) == iron
                                && inventory.extract(pattern.water, Long.MAX_VALUE, Actionable.SIMULATE) == water,
                        label + " baseline failed extraction restores all input", checks);
            } else if (baseline) {
                require(inputs != null && inputs.length == 2
                                && inputs[0].get(pattern.iron) == REQUESTED
                                && inputs[1].get(pattern.water) == Long.MAX_VALUE
                                && expected.get(pattern.gold) == REQUESTED,
                        label + " baseline saturation extracts unequal item/fluid operation ratios", checks);
                require(inputs[0].get(pattern.iron) != inputs[1].get(pattern.water) / 1000L,
                        label + " baseline material ratio cannot represent the claimed operations", checks);
            } else {
                require(inputs != null && inputs.length == 2
                                && inputs[0].get(pattern.iron) == available
                                && inputs[1].get(pattern.water) == available * 1000L
                                && expected.get(pattern.gold) == available,
                        label + " actual extraction hook preserves item/fluid/output ratios", checks);
                require(inventory.extract(pattern.iron, Long.MAX_VALUE, Actionable.SIMULATE) == iron - available
                                && inventory.extract(pattern.water, Long.MAX_VALUE, Actionable.SIMULATE)
                                == water - available * 1000L,
                        label + " actual extraction consumes exactly the selected batch", checks);
            }
        }
    }

    private static void installOrdinaryJob(Object logic, Pattern pattern) throws Exception {
        ICraftingPlan plan = new ICraftingPlan() {
            public GenericStack finalOutput() { return new GenericStack(pattern.gold, REQUESTED); }
            public long bytes() { return 1; }
            public boolean simulation() { return false; }
            public boolean multiplePaths() { return false; }
            public KeyCounter usedItems() { return new KeyCounter(); }
            public KeyCounter emittedItems() { return new KeyCounter(); }
            public KeyCounter missingItems() { return new KeyCounter(); }
            public Map<IPatternDetails, Long> patternTimes() { return Map.of(); }
        };
        var jobType = Class.forName("appeng.crafting.execution.ExecutingCraftingJob");
        var listenerType = Class.forName(
                "appeng.crafting.execution.ExecutingCraftingJob$CraftingDifferenceListener");
        var listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType},
                (proxy, method, arguments) -> null);
        var constructor = jobType.getDeclaredConstructor(ICraftingPlan.class, listenerType,
                appeng.crafting.CraftingLink.class, Integer.class);
        constructor.setAccessible(true);
        find(logic.getClass(), "job").set(logic, constructor.newInstance(plan, listener, null, null));
    }

    private static Method hook(Class<?> type, String name, int arity) throws NoSuchMethodException {
        for (; type != null; type = type.getSuperclass())
            for (var method : type.getDeclaredMethods())
                if (method.getName().contains(name) && method.getParameterCount() == arity) {
                    method.setAccessible(true); return method;
                }
        throw new NoSuchMethodException(name);
    }

    private static Field find(Class<?> type, String name) throws NoSuchFieldException {
        for (; type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class Pattern implements IPatternDetails {
        final AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        final AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        final AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        public IInput[] getInputs() { return new IInput[] {input(iron, 1), input(water, 1000)}; }
        public GenericStack[] getOutputs() { return new GenericStack[] {new GenericStack(gold, 1)}; }
        public boolean supportsPushInputsToExternalInventory() { return true; }
        private static IInput input(AEKey key, long amount) {
            return new IInput() {
                public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(key, 1)}; }
                public long getMultiplier() { return amount; }
                public boolean isValid(AEKey candidate, Level level) { return key.equals(candidate); }
                public AEKey getRemainingKey(AEKey candidate) { return null; }
            };
        }
    }

    private static void require(boolean value, String label, List<String> checks) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }
}
