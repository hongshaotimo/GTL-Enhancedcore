package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.me.service.CraftingService;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudCpuRefill;
import com.gtl.enhancedcore.common.recipe.iv.IvDispatchContext;
import com.gtl.enhancedcore.common.recipe.iv.IvJob;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphDispatchContext;

/** Actual transformed AE CPU extraction and real cloud push; only the submitted plan/inventory are deterministic. */
public final class CloudLongDispatchRuntimeChecks {
    private CloudLongDispatchRuntimeChecks() {}
    public static void run(ServerLevel level, CloudPatternBufferMachine buffer, AEKey input, AEKey output,
                           List<String> checks) throws Exception {
        IPatternDetails pattern = buffer.getAvailablePatterns().getFirst();
        long max = Long.MAX_VALUE, batch = max / 2;
        require(pattern.getInputs().length == 1 && pattern.getInputs()[0].getMultiplier() == 2,
                "Actual routed audit pattern consumes two physical inputs per pattern", checks);
        require(buffer.iv$getState().jobs.isEmpty(), "Long dispatch begins with the real cloud ledger empty", checks);
        var oldInventory = new ListCraftingInventory(ignored -> {}); oldInventory.insert(input, max, Actionable.MODULATE);
        var oldHolders = AEUtils.extractForProcessingPattern(pattern, oldInventory, new KeyCounter(), max);
        require(oldHolders != null && oldHolders[0].get(input) == max && max % 2 == 1,
                "Real GTLCore AEUtils reproduces saturated Long.MAX input rather than a complete pattern multiple", checks);
        IvDispatchContext.set(buffer, pattern, max);
        try {
            require(!buffer.pushPattern(pattern, oldHolders) && buffer.iv$getState().jobs.isEmpty(),
                    "Unclipped real AEUtils dispatch reproduces the player's exact cloud receive rejection without taking ownership", checks);
        } finally { IvDispatchContext.clear(); }
        CraftingCpuHelper.reinjectPatternInputs(oldInventory, oldHolders);
        require(oldInventory.extract(input, max, Actionable.SIMULATE) == max,
                "Rejected saturated dispatch returns every physical input to its CPU inventory", checks);
        require(buffer.cloudDispatchCapacity(pattern, max) == batch,
                "Production cloud capacity clips before the ordinary AE CPU saturated multiply", checks);

        // A detached AE storage block provides the CPU's real markDirty host; no second CPU ticks in the grid.
        BlockPos pos = buffer.getPos().above(6);
        var state = AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState();
        var core = (CraftingBlockEntity)AEBlocks.CRAFTING_STORAGE_64K.block().newBlockEntity(pos, state);
        core.setLevel(level);
        var cluster = new CraftingCPUCluster(pos, pos);
        field(CraftingCPUCluster.class, "machineSrc").set(cluster, new MachineSource(core));
        var cpu = cluster.craftingLogic;
        cpu.getInventory().insert(input, max, Actionable.MODULATE);
        var empty = new KeyCounter();
        ICraftingPlan plan = new ICraftingPlan() {
            public GenericStack finalOutput() { return new GenericStack(output, max); }
            public long bytes() { return 1; }
            public boolean simulation() { return false; }
            public boolean multiplePaths() { return false; }
            public KeyCounter usedItems() { return empty; }
            public KeyCounter emittedItems() { return empty; }
            public KeyCounter missingItems() { return empty; }
            public Map<IPatternDetails,Long> patternTimes() { return Map.of(pattern, max); }
        };
        Class<?> jobClass = Class.forName("appeng.crafting.execution.ExecutingCraftingJob");
        Class<?> listenerClass = Class.forName("appeng.crafting.execution.ExecutingCraftingJob$CraftingDifferenceListener");
        Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass},
                (proxy, method, arguments) -> null);
        var constructor = jobClass.getDeclaredConstructor(ICraftingPlan.class, listenerClass, CraftingLink.class, Integer.class);
        constructor.setAccessible(true);
        var link = new CraftingLink(CraftingCpuHelper.generateLinkData(UUID.randomUUID(), true, false), cluster);
        Object job = constructor.newInstance(plan, listener, link, null);
        field(CraftingCpuLogic.class, "job").set(cpu, job);
        try {
            int dispatched = cpu.executeCrafting(1, (CraftingService)buffer.getGrid().getCraftingService(),
                    buffer.getGrid().getEnergyService(), level);
            require(dispatched == 1 && buffer.iv$getState().jobs.size() == 1,
                    "Actual transformed ordinary CraftingCpuLogic.executeCrafting accepts the first Long.MAX plan chunk", checks);
            IvJob accepted = buffer.iv$getState().jobs.getFirst();
            require(accepted.totalOperations == max - 1 && accepted.inventory.get(input) == max - 1
                            && cpu.getInventory().extract(input, max, Actionable.SIMULATE) == 1,
                    "Real CPU and accepted cloud ledger conserve Long.MAX inputs with one exact material remainder", checks);
            var tasks = (Map<?,?>)field(jobClass, "tasks").get(job);
            Object progress = tasks.get(pattern);
            require(progress != null && field(progress.getClass(), "value").getLong(progress) == max - batch,
                    "AE CPU decrements only the accepted whole-pattern batch and retains the unfinished plan", checks);
            var reloaded = IvJob.load(accepted.save());
            require(reloaded.totalOperations == max - 1 && reloaded.inventory.equals(accepted.inventory),
                    "Accepted near-long-max operations and input stock survive the actual job NBT codec", checks);
            buffer.iv$getState().jobs.clear();
            // Finish the CPU plan in safe complete chunks, without simulating billions of crafting outputs.
            cpu.getInventory().insert(input, max - 1, Actionable.MODULATE);
            int second = cpu.executeCrafting(1, (CraftingService)buffer.getGrid().getCraftingService(),
                    buffer.getGrid().getEnergyService(), level);
            require(second == 1 && buffer.iv$getState().jobs.getFirst().totalOperations == max - 1,
                    "The same actual CPU resumes and accepts the second safe chunk of its Long.MAX pattern plan", checks);
            buffer.iv$getState().jobs.clear();
            cpu.getInventory().insert(input, 1, Actionable.MODULATE);
            int last = cpu.executeCrafting(1, (CraftingService)buffer.getGrid().getCraftingService(),
                    buffer.getGrid().getEnergyService(), level);
            require(last == 1 && buffer.iv$getState().jobs.getFirst().totalOperations == 2 && tasks.isEmpty()
                            && cpu.getInventory().extract(input, max, Actionable.SIMULATE) == 0,
                    "Long.MAX pattern repetitions spanning more than long materials drain as two safe chunks plus one exact tail", checks);
            require(IvDispatchContext.operations(buffer, pattern) == -1,
                    "Ordinary CPU dispatch context is cleared after each real push", checks);
            buffer.iv$getState().jobs.clear();
            var graphInputs = Map.of(input, 2L);
            long graphCapacity = GraphDispatchContext.call(graphInputs, max,
                    () -> GraphDispatchContext.capacity(buffer, pattern, max));
            require(graphCapacity == batch, "Actual GraphDispatchContext uses the same safe cloud capacity", checks);
            var graphStock = new ListCraftingInventory(ignored -> {}); graphStock.insert(input, max - 1, Actionable.MODULATE);
            var graphHolders = AEUtils.extractForProcessingPattern(pattern, graphStock, new KeyCounter(), batch);
            require(GraphDispatchContext.<Boolean>call(graphInputs, batch, () -> buffer.pushPattern(pattern, graphHolders))
                            && buffer.iv$getState().jobs.getFirst().totalOperations == max - 1,
                    "Actual graph batch context and real cloud push retain the exact large dispatch multiplier", checks);
            buffer.iv$getState().jobs.clear(); buffer.markDirty();
            refillFromMe(buffer, pattern, input, checks);
            virtualGraph(level, buffer, output, checks);
        } finally { IvDispatchContext.clear(); core.setLevel(null); }
    }
    private static void refillFromMe(CloudPatternBufferMachine buffer, IPatternDetails pattern, AEKey input,
                                     List<String> checks) {
        var source = appeng.api.networking.security.IActionSource.empty();
        var storage = buffer.getGrid().getStorageService().getInventory();
        require(storage.insert(input, 19, Actionable.MODULATE, source) == 19,
                "Real AE drive can hold a later cloud batch's physical input", checks);
        var cpuStock = new ListCraftingInventory(ignored -> {});
        cpuStock.insert(input, 1, Actionable.MODULATE);
        CloudCpuRefill.replenish(pattern, 10, cpuStock, (CraftingService)buffer.getGrid().getCraftingService(),
                buffer.getGrid(), source, () -> {});
        require(cpuStock.extract(input, 20, Actionable.SIMULATE) == 20,
                "A cloud CPU with one unusable input pulls exactly the next ten patterns from real ME storage", checks);
        require(CloudCpuRefill.availableOperations(pattern, 10, cpuStock) == 10,
                "Refilled stock supports the next complete dispatch", checks);
        var holders = AEUtils.extractForProcessingPattern(pattern, cpuStock, new KeyCounter(), 10);
        IvDispatchContext.set(buffer, pattern, 10);
        try {
            require(holders != null && buffer.pushPattern(pattern, holders)
                            && buffer.iv$getState().jobs.getFirst().totalOperations == 20,
                    "The real cloud endpoint accepts the replenished ME batch", checks);
        } finally { IvDispatchContext.clear(); }
        require(cpuStock.extract(input, 20, Actionable.SIMULATE) == 0,
                "Refilled material is owned once by the accepted cloud job", checks);
        buffer.iv$getState().jobs.clear(); buffer.markDirty();
    }
    private static void virtualGraph(ServerLevel level, CloudPatternBufferMachine buffer, AEKey output,
                                     List<String> checks) {
        var machine = com.gtl.enhancedcore.common.recipe.iv.IvBuffers.controller(buffer);
        var type = machine.getDefinition().getRecipeTypes()[0];
        var recipe = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.of(
                new net.minecraft.resources.ResourceLocation("gtl_enhancedcore", "audit_cloud_virtual_long_dispatch"), type)
                .notConsumable(net.minecraft.world.item.Items.DIAMOND).outputItems(net.minecraft.world.item.Items.GOLD_INGOT)
                .duration(20000).EUt(8).buildRawRecipe();
        var recipes = new ArrayList<net.minecraft.world.item.crafting.Recipe<?>>(level.getRecipeManager().getRecipes());
        recipes.add(recipe); level.getRecipeManager().replaceRecipes(recipes);
        var token = org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior.wrap(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        var tokenKey = AEItemKey.of(token);
        var original = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(tokenKey, 1)}, new GenericStack[]{new GenericStack(output, 1)});
        org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata.writeRecipeTypeId(original, type.registryName);
        var node = buffer.cloudNode(); var store = buffer.cloudStore(); var operation = UUID.randomUUID();
        var entry = store.upload(buffer.cloudLibraryId(), node.owner(), original, new CompoundTag(), operation);
        store.checkpoint(); store.acknowledge(buffer.cloudLibraryId(), node.owner(), operation); store.checkpoint();
        buffer.cloudLeased(entry);
        IPatternDetails pattern = buffer.cloudRealPattern(entry.lease().stableSlot(), entry.original());
        var holders = new KeyCounter[]{new KeyCounter()}; holders[0].add(tokenKey, 1);
        require(GraphDispatchContext.<Boolean>call(Map.of(), Long.MAX_VALUE, () -> buffer.pushPattern(pattern, holders)),
                "Graph selector-only pattern accepts a single configuration token with its trusted Long.MAX batch context", checks);
        var accepted = buffer.iv$getState().jobs.getFirst();
        require(accepted.totalOperations == Long.MAX_VALUE && accepted.inventory.isEmpty()
                        && accepted.virtual.get(AEItemKey.of(net.minecraft.world.item.Items.DIAMOND)) == Long.MAX_VALUE,
                "Pure virtual graph dispatch stores exact finite operations without inventing physical token stock", checks);
        buffer.iv$getState().jobs.clear(); buffer.markDirty();
    }
    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static void require(boolean condition, String text, List<String> checks) {
        if (!condition) throw new AssertionError(text); checks.add(text);
    }
}
