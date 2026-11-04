package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Real transformed suppliers/callbacks in synthetic levels; never opens a saved player world. */
public final class CloudLifecycleBoundaryRuntimeChecks {
    private CloudLifecycleBoundaryRuntimeChecks() {}

    public static List<String> run() throws Exception {
        var checks = new ArrayList<String>();
        fusion(checks);
        clientBoundary(checks);
        return checks;
    }

    private static void fusion(List<String> checks) throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtceu:luv_fusion_reactor", false, false, false);
        try {
            var machine = fixture.machine(); var logic = machine.getRecipeLogic();
            var access = (IvNativeAccess) logic;
            var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore:cloud_pattern_buffer"));
            var cloud = (MetaMachineBlockEntity) definition.getBlockEntityType().create(
                    new BlockPos(8, 80, 0), definition.defaultBlockState());
            cloud.setLevel(machine.getLevel()); fixture.holders().add(cloud);
            var world = (com.lowdragmc.lowdraglib.utils.TrackedDummyWorld) machine.getLevel();
            world.blockEntities.put(cloud.getBlockPos(), cloud);
            world.renderedBlocks.put(cloud.getBlockPos(), new com.lowdragmc.lowdraglib.utils.BlockInfo(definition.defaultBlockState()));
            var parts = new ArrayList<>(machine.getParts());
            parts.add((IMultiPart) cloud.getMetaMachine());
            field(machine, "parts").set(machine, parts);
            field(machine, "partPositions").set(machine, parts.stream().map(part -> part.self().getPos()).toArray(BlockPos[]::new));
            var buffer = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine) cloud.getMetaMachine();
            check(!machine.isRemote() && machine.getParts().contains(buffer),
                    "server lifecycle fixture retains matching loaded part membership and positions", checks);
            check(IvMachineScope.crossRecipeEnabled(machine), "real fusion controller opts in through actual cloud endpoint", checks);
            GTRecipe recipe = machine.getRecipeType().recipeBuilder("cloud_fusion_lifecycle_boundary")
                    .outputFluids(GTMaterials.Water.getFluid(1)).EUt(0).duration(2).buildRawRecipe();
            recipe.data.putLong("eu_to_start", 0);
            var originalRecipe = logic.getLastRecipe(); var originalOrigin = logic.getLastOriginRecipe();
            int originalProgress = logic.getProgress(), originalDuration = logic.getDuration();
            try (var scope = access.iv$recipeContext(recipe, recipe, 1, 2)) {
                check(logic.getLastRecipe() == recipe && logic.getLastOriginRecipe() == recipe
                        && logic.getProgress() == 1 && logic.getDuration() == 2,
                        "callback scope exposes current committed recipe/origin/progress/duration", checks);
                try (var nested = access.iv$recipeContext(recipe.copy(), recipe, 0, 9)) {
                    check(logic.getDuration() == 9, "nested callback scope takes its own duration", checks);
                }
                check(logic.getLastRecipe() == recipe && logic.getDuration() == 2,
                        "nested callback restores enclosing recipe", checks);
                check(machine.beforeWorking(recipe), "actual fusion beforeWorking executes normally", checks);
                check(access.iv$onWorking(), "actual fusion onWorking reads non-null order recipe", checks);
                check((int) field(machine, "color").get(machine) != -1,
                        "fusion callback still derives output fluid color", checks);
                machine.afterWorking();
                check((int) field(machine, "color").get(machine) == -1,
                        "actual fusion afterWorking retains color cleanup", checks);
            }
            check(logic.getLastRecipe() == originalRecipe && logic.getLastOriginRecipe() == originalOrigin
                    && logic.getProgress() == originalProgress && logic.getDuration() == originalDuration,
                    "callback scope restores prior native/display state", checks);
            var injected = new IllegalStateException("expected audit failure");
            try {
                try (var scope = access.iv$recipeContext(recipe, recipe, 1, 2)) { throw injected; }
            } catch (IllegalStateException expected) { check(expected == injected, "callback exceptions propagate unchanged", checks); }
            check(logic.getLastRecipe() == originalRecipe && logic.getDuration() == originalDuration,
                    "exception path restores callback recipe state", checks);

            var tick = IvNativeRecipeContext.tickRecipe(recipe);
            check(tick.inputs.isEmpty() && tick.outputs.isEmpty() && !tick.tickInputs.containsKey(EURecipeCapability.CAP)
                    && !recipe.outputs.isEmpty() && recipe.data.getLong("eu_to_start") == 0,
                    "tick projection removes ledger-owned IO while retaining full callback snapshot", checks);
            var job = new IvJob(0, recipe, new CompoundTag(), Map.<AEKey, Long>of(), Map.of(), 1);
            job.remaining = 0; job.parallel = 1; job.nativeCapacity = 1;
            job.duration = 2; job.nativeRecipe = recipe; job.cycle = "cloud-fusion-lifecycle";
            job.energyLeft = BigInteger.ZERO; job.energyTotal = BigInteger.ZERO;
            access.iv$managed(true);
            Class<?> refType = Class.forName(IvNativeEngine.class.getName() + "$Ref");
            var constructor = refType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
            Object ref = constructor.newInstance(buffer, job);
            var advance = IvNativeEngine.class.getDeclaredMethod("advance", List.class); advance.setAccessible(true);
            var engine = access.iv$engine();
            advance.invoke(engine, List.of(ref));
            check(job.elapsed == 1 && job.active() && logic.isWorkingEnabled(),
                    "real engine advances first fusion tick from null native lastRecipe without self-suspending", checks);
            check(logic.getLastRecipe() == originalRecipe && logic.getDuration() == originalDuration,
                    "engine working callback restores prior display state", checks);
            job = IvJob.load(job.save());
            check(job.elapsed == 1 && job.active() && job.nativeRecipe != null && !job.nativeRecipe.outputs.isEmpty()
                    && job.nativeRecipe.data.contains("eu_to_start"),
                    "persisted committed batch retains callback recipe outputs and fusion data", checks);
            ref = constructor.newInstance(buffer, job);
            advance.invoke(engine, List.of(ref));
            check(!job.active() && job.completedOperations == 1 && job.nativeRecipe == null && logic.isWorkingEnabled(),
                    "real engine completes fusion batch and invokes afterWorking once", checks);
            check((int) field(machine, "color").get(machine) == -1,
                    "engine finish callback executes actual fusion cleanup", checks);
        } finally { for (var holder : fixture.holders()) holder.setLevel(null); }
    }

    private static void clientBoundary(List<String> checks) throws Exception {
        for (String id : List.of("gtceu:uev_compressed_fusion_reactor", "gtceu:luv_fusion_reactor")) {
            var fixture = StellarIvGuiChecks.fixture(id, true, false, false);
            try {
                var machine = fixture.machine(); var logic = machine.getRecipeLogic(); var access = (IvNativeAccess) logic;
                // A deliberately unreadable live list makes any accidental client
                // structure query fail deterministically, including in render workers.
                field(machine, "parts").set(machine, new ForbiddenParts());
                access.iv$syncMode(true, true);
                check(IvMachineScope.crossRecipeEnabled(machine) && IvBuffers.genericCloudTarget(machine)
                        && IvBuffers.collect(machine).isEmpty(), id + " mode/collect read snapshots without client parts", checks);
                var workers = Executors.newFixedThreadPool(4);
                try {
                    var futures = new ArrayList<Future<?>>();
                    for (int worker = 0; worker < 4; worker++) futures.add(workers.submit(() -> {
                        for (int iteration = 0; iteration < 10000; iteration++) {
                            logic.isWorkingEnabled();
                            IvMachineScope.crossRecipeEnabled(machine);
                            IvBuffers.genericCloudTarget(machine);
                            IvBuffers.collect(machine);
                        }
                    }));
                    for (int iteration = 0; iteration < 10000; iteration++) access.iv$syncMode((iteration & 1) == 0, true);
                    for (var future : futures) future.get(30, TimeUnit.SECONDS);
                } finally { workers.shutdownNow(); }
                check(true, id + " 40,000 concurrent renderer queries never read live part membership", checks);
                access.iv$syncMode(false, false);
                check(!IvMachineScope.crossRecipeEnabled(machine) && !IvBuffers.genericCloudTarget(machine),
                        id + " synchronized structure removal clears client mode", checks);
            } finally { for (var holder : fixture.holders()) holder.setLevel(null); }
        }
    }

    private static final class ForbiddenParts extends AbstractList<IMultiPart> {
        @Override public IMultiPart get(int index) { throw new AssertionError("client renderer read live parts"); }
        @Override public int size() { throw new AssertionError("client renderer read live parts"); }
    }
    private static Field field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void check(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
}
