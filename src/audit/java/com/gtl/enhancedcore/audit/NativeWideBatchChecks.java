package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler;
import com.gtladd.gtladditions.common.machine.multiblock.part.ThreadPartMachine;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

/** Real supplier, real Omega lifecycle and real isolated prepare; synthetic title-screen worlds only. */
public final class NativeWideBatchChecks {
    private static final long COUNT = 5_000_000_000L;
    private static boolean visualRequested;
    private static VisualFixture visualFixture;
    static record VisualFixture(StellarIvGuiChecks.Fixture fixture, CompoundTag summary, UUID owner,
                                BigInteger previousBalance, List<String> checks) implements AutoCloseable {
        @Override public void close() {
            WirelessEnergyManager.setUserEU(owner, previousBalance);
            fixture.holders().forEach(holder -> holder.setLevel(null));
        }
    }
    static VisualFixture visual(boolean stellar) throws Exception {
        if (visualRequested) throw new IllegalStateException("Nested visual fixture");
        visualRequested = true; visualFixture = null;
        try {
            probe(stellar, false, false, new ArrayList<>(), new ArrayList<>());
            return Objects.requireNonNull(visualFixture);
        } finally { visualRequested = false; visualFixture = null; }
    }
    private NativeWideBatchChecks() {}

    public static void run(List<String> checks) throws Exception {
        var mc = Minecraft.getInstance();
        if (mc.level != null || mc.getSingleplayerServer() != null)
            throw new IllegalStateException("Native wide audit requires no player world or live server");
        var rows = new ArrayList<Map<String, Object>>();
        var report = new LinkedHashMap<String, Object>();
        boolean baseline = Boolean.getBoolean("gtl.enhancedcore.nativeWideBaseline");
        report.put("baseline", baseline); report.put("rows", rows);
        var previousData = WirelessEnergySavaedData.INSTANCE;
        WirelessEnergySavaedData.INSTANCE = new WirelessEnergySavaedData(null);
        try {
            probe(true, false, baseline, rows, checks);
            probe(false, false, baseline, rows, checks);
            probe(false, true, baseline, rows, checks);
            report.put("status", "PASS");
        } catch (Throwable failure) {
            report.put("status", "FAIL"); report.put("error", failure.toString());
            throw failure;
        } finally {
            WirelessEnergySavaedData.INSTANCE = previousData;
            Files.writeString(mc.gameDirectory.toPath().resolve("native-wide-batch-checks.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }

    private static void probe(boolean stellar, boolean finite, boolean baseline, List<Map<String, Object>> rows,
                              List<String> checks) throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtceu:field_extruder_factory", false, false, stellar ? 64 : -1);
        UUID owner = UUID.randomUUID();
        BigInteger previous = WirelessEnergyManager.getUserEU(owner);
        try {
            var machine = fixture.machine();
            var logic = machine.getRecipeLogic();
            var row = new LinkedHashMap<String, Object>(); rows.add(row);
            row.put("scenario", stellar ? "64 arrays without Omega" : finite
                    ? "Omega without stellar source, finite lane 1" : "Omega without stellar source, MAX lane");
            row.put("machineClass", machine.getClass().getName());
            row.put("logicClass", logic.getClass().getName());
            set(machine, "tier", 14);
            if (machine instanceof IBatchMachine batch) batch.setBatchEnabled(false);
            if (stellar) {
                var station = GalaxyLinks.linkedStation(machine);
                require(station != null, "Wide native real stellar source resolves", checks);
                set(station, "userId", owner);
                row.put("sourceArrays", station.getAstralArrayCount());
            } else {
                ThreadPartMachine omega = null;
                for (var entry : GTRegistries.MACHINES.entries()) {
                    if (!entry.getKey().getNamespace().equals("gtladditions")) continue;
                    if (!entry.getKey().getPath().contains("thread")) continue;
                    var definition = entry.getValue();
                    var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(
                            new BlockPos(8, 80, 0), definition.defaultBlockState());
                    if (holder.getMetaMachine() instanceof ThreadPartMachine part) {
                        holder.setLevel(machine.getLevel()); fixture.holders().add(holder); omega = part; break;
                    }
                }
                require(omega != null, "Wide native registered Omega part exists", checks);
                var inventoryField = ThreadPartMachine.class.getDeclaredField("astralArrayInventory");
                inventoryField.setAccessible(true);
                ((com.lowdragmc.lowdraglib.misc.ItemStackTransfer) inventoryField.get(omega)).setStackInSlot(0,
                        com.gtladd.gtladditions.common.items.GTLAddItems.INSTANCE.getASTRAL_ARRAY().asStack(64));
                var parts = new ArrayList<IMultiPart>(machine.getParts()); parts.add(omega);
                set(machine, "parts", parts);
                set(machine, "partPositions", parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
                omega.addedToController(machine);
                require(machine instanceof IThreadModifierMachine modifier
                                && modifier.getThreadPartMachine() == omega && modifier.getAdditionalThread() == omega.getThreadCount()
                                && omega.getThreadCount() > 0,
                        "Wide native real Omega addedToController binds its populated thread inventory", checks);
                require(logic instanceof MultipleRecipesLogic
                                || logic instanceof MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode(),
                        "Wide native registered logic retains GTLCore batching or enables Mutable mode", checks);
                row.put("omegaArrays", 64); row.put("omegaThreads", omega.getThreadCount());
                row.put("sourceArrays", 0);
                // Use a real registered GTCEu hatch, then seed its supported numeric
                // fixture state. Some configurations do not register a MAX-tier hatch.
                ParallelHatchPartMachine parallel = null;
                MetaMachineBlockEntity parallelHolder = null;
                for (var entry : GTRegistries.MACHINES.entries()) {
                    if (!entry.getKey().getNamespace().equals("gtceu")
                            || !entry.getKey().getPath().endsWith("parallel_hatch")) continue;
                    var definition = entry.getValue();
                    var holder = (MetaMachineBlockEntity) definition.getBlockEntityType().create(
                            new BlockPos(9, 80, 0), definition.defaultBlockState());
                    if (holder.getMetaMachine() instanceof ParallelHatchPartMachine candidate
                            && (parallel == null || candidate.getTier() > parallel.getTier())) {
                        parallel = candidate; parallelHolder = holder;
                    }
                }
                require(parallel != null, "Wide native registered GTCEu parallel hatch exists", checks);
                parallelHolder.setLevel(machine.getLevel()); fixture.holders().add(parallelHolder);
                set(parallel, "maxParallel", Integer.MAX_VALUE);
                parallel.setCurrentParallel(finite ? 1 : Integer.MAX_VALUE);
                parts.add(parallel);
                set(machine, "parallelHatch", parallel);
                set(machine, "partPositions", parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
                row.put("parallelHatchId", parallel.getDefinition().getId().toString());
                row.put("parallelHatchTier", parallel.getTier());
                row.put("controlledHatchMaximum", Integer.MAX_VALUE);
                row.put("hatchCurrentParallel", parallel.getCurrentParallel());
                require(IvMachineScope.parallel(machine) == (finite ? 1 : Integer.MAX_VALUE),
                        "Wide native real getter reads seeded hatch state", checks);
                // Native wireless attachment uses its public API. The handler reads the actual
                // BigInteger grid, without introducing physical input inventories or a server.
                ((IWirelessElectricMultiblockMachine) machine).setWirelessNetworkEnergyHandler(new IWirelessNetworkEnergyHandler() {
                    @Override public boolean consumeEnergy(int delta) { return consumeEnergy(BigInteger.valueOf(delta)); }
                    @Override public boolean consumeEnergy(long delta) { return consumeEnergy(BigInteger.valueOf(delta)); }
                    @Override public boolean consumeEnergy(BigInteger delta) {
                        if (delta.signum() > 0 || getMaxAvailableEnergy().add(delta).signum() < 0) return false;
                        WirelessEnergyManager.setUserEU(owner, getMaxAvailableEnergy().add(delta)); return true;
                    }
                    @Override public BigInteger getMaxAvailableEnergy() { return WirelessEnergyManager.getUserEU(owner); }
                    @Override public boolean isOnline() { return true; }
                });
            }
            WirelessEnergyManager.setUserEU(owner, BigInteger.TEN.pow(72));
            logic.setWorkingEnabled(true);
            require(IvMachineScope.crossRecipeEnabled(machine), "Wide native real super buffer enables isolated orders", checks);
            boolean unlimited = IvMachineScope.unlimited(machine);
            require(unlimited == stellar, "Wide native capacity authority matches source and Omega state", checks);
            var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "native_wide_batch_probe"), machine.getRecipeType())
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(8).duration(200).buildRawRecipe();
            var apple = AEItemKey.of(Items.APPLE); var gold = AEItemKey.of(Items.GOLD_NUGGET);
            var job = new IvJob(0, recipe, new CompoundTag(), Map.of(apple, COUNT), Map.of(), COUNT);
            long perRecipe = IvMachineScope.parallel(machine, recipe);
            long granted = Math.min(COUNT, IvNativeCapacity.candidateCapacity(machine, recipe, IvMachineScope.budget(machine)));
            row.put("mutableMode", logic instanceof MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode());
            row.put("batching", IvMachineScope.batching(machine)); row.put("unlimited", unlimited);
            row.put("effectiveThreads", IvMachineScope.threads(machine));
            row.put("maxParallel", IvMachineScope.parallel(machine));
            row.put("perRecipeCapacity", perRecipe); row.put("budget", IvMachineScope.budget(machine)); row.put("granted", granted);
            var buffer = (MEPatternBufferPartMachine) fixture.holders().get(1).getMetaMachine();
            var engine = ((IvNativeAccess) logic).iv$engine();
            var refType = Class.forName(IvNativeEngine.class.getName() + "$Ref");
            var constructor = refType.getDeclaredConstructor(MEPatternBufferPartMachine.class, IvJob.class);
            constructor.setAccessible(true);
            var ref = constructor.newInstance(buffer, job);
            var prepare = IvNativeEngine.class.getDeclaredMethod("prepare", refType, long.class, long.class, String.class);
            prepare.setAccessible(true); prepare.invoke(engine, ref, granted, granted, "wide-audit");
            long consumed = COUNT - job.inventory.getOrDefault(apple, 0L);
            long completion = job.completion.getOrDefault(gold, 0L);
            row.put("actualParallel", job.parallel); row.put("consumed", consumed); row.put("completion", completion);
            row.put("remaining", job.remaining); row.put("nativeCapacity", job.nativeCapacity);
            row.put("halted", job.halted); row.put("error", job.error); row.put("duration", job.duration);
            row.put("wirelessEUt", job.nativeWirelessEUt.toString()); row.put("energyTotal", job.energyTotal.toString());
            System.out.println("[NATIVE_WIDE_BATCH] " + new GsonBuilder().create().toJson(row));
            require(job.active() && !job.halted, "Wide native actual prepare commits: " + row, checks);
            require(consumed == job.parallel && completion == job.parallel && job.remaining == COUNT - job.parallel,
                    "Wide native exact apple/gold/operation conservation: " + job.parallel, checks);
            require(WirelessEnergyManager.getUserEU(owner).equals(BigInteger.TEN.pow(72)),
                    "Wide native preparation defers network payment to working ticks", checks);
            require(job.energyTotal.signum() > 0 && job.energyTotal.equals(job.energyLeft),
                    "Wide native committed batch retains its complete unpaid energy debt", checks);
            if (!baseline && !finite) require(job.parallel > Integer.MAX_VALUE,
                    "Native shared long budget admits more than Integer.MAX_VALUE: " + row.get("scenario"), checks);
            if (baseline && !stellar && !finite) require(job.parallel <= Integer.MAX_VALUE,
                    "Baseline Omega exposes the former per-candidate int limit", checks);
            require(job.parallel <= IvMachineScope.budget(machine) && job.nativeCapacity <= granted,
                    "Native batch respects finite shared budget and reserved capacity", checks);
            if (finite && !baseline) require(job.parallel == Math.min(COUNT, IvMachineScope.budget(machine)),
                    "Lane-one Omega can use the finite complete shared thread budget", checks);
            if (stellar) {
                // Unlike the isolated prepare probe, this covers candidate sizing,
                // actual FairBudget allocation and all native admission checks.
                var jobs = new ArrayList<IvJob>();
                var refs = new ArrayList<Object>();
                for (int index = 0; index < 3; index++) {
                    var next = new IvJob(index, recipe.copy(), new CompoundTag(), Map.of(apple, COUNT), Map.of(), COUNT);
                    jobs.add(next); refs.add(constructor.newInstance(buffer, next));
                }
                var start = IvNativeEngine.class.getDeclaredMethod("start", List.class); start.setAccessible(true);
                start.invoke(engine, refs);
                var parallels = jobs.stream().map(next -> next.parallel).toList();
                row.put("threeScheduledParallels", parallels);
                row.put("threeScheduledErrors", jobs.stream().map(next -> next.error).toList());
                require(jobs.stream().allMatch(next -> next.active() && !next.halted && next.parallel == COUNT
                                && next.inventory.isEmpty() && next.completion.getOrDefault(gold, 0L) == COUNT),
                        "Actual stellar scheduler admits three independent 5-billion batches: " + parallels, checks);
                var publish = IvNativeEngine.class.getDeclaredMethod("publish", List.class); publish.setAccessible(true);
                publish.invoke(engine, refs);
                var summary = ((IvNativeAccess) logic).iv$summary();
                row.put("threeScheduledSummary", summary.toString());
                require(summary.getString("running").equals("15000000000") && summary.getBoolean("unlimited"),
                        "Actual stellar published batch total is 15 billion with unlimited authority", checks);
                // Installing a capacity grant must never mutate an already paid batch.
                // Reproduce the screenshot's exact total as a controlled existing state.
                var oldJobs = new ArrayList<IvJob>(); var oldRefs = new ArrayList<Object>();
                for (int index = 0; index < 3; index++) {
                    var old = new IvJob(index, recipe.copy(), new CompoundTag(), Map.of(apple, COUNT), Map.of(), COUNT);
                    var oldRef = constructor.newInstance(buffer, old);
                    prepare.invoke(engine, oldRef, (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE, "pre-link-batch");
                    oldJobs.add(old); oldRefs.add(oldRef);
                }
                start.invoke(engine, oldRefs); publish.invoke(engine, oldRefs);
                var oldSummary = ((IvNativeAccess) logic).iv$summary();
                row.put("existingIntBatchesSummary", oldSummary.toString());
                require(oldJobs.stream().allMatch(next -> next.parallel == Integer.MAX_VALUE)
                                && oldSummary.getString("running").equals("6442450941") && oldSummary.getBoolean("unlimited"),
                        "Existing committed int batches coexist with newly unlimited capacity without being rescaled", checks);
                System.out.println("[NATIVE_WIDE_SCHEDULER] " + new GsonBuilder().create().toJson(row));
                if (visualRequested) publish.invoke(engine, refs);
            }
            if (visualRequested) {
                if (!stellar) {
                    var publish = IvNativeEngine.class.getDeclaredMethod("publish", List.class); publish.setAccessible(true);
                    publish.invoke(engine, List.of(ref));
                }
                visualFixture = new VisualFixture(fixture, ((IvNativeAccess) logic).iv$summary().copy(), owner, previous, List.copyOf(checks));
            }
        } finally {
            if (visualFixture == null || visualFixture.fixture() != fixture) {
                WirelessEnergyManager.setUserEU(owner, previous);
                for (var holder : fixture.holders()) holder.setLevel(null);
            }
        }
    }

    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
}
