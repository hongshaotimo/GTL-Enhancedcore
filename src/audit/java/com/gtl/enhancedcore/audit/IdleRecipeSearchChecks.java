package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;

/** Calls the transformed base serverTick with virtual search overrides, in synthetic levels only. */
@Mod("enhancedcore_audit")
public final class IdleRecipeSearchChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/idle-recipe-20261107").toAbsolutePath().normalize();
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public IdleRecipeSearchChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.idleRecipeAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, Object>();
        try {
            check(directory.startsWith(ROOT) && !directory.equals(ROOT), "Task-owned isolated directory");
            check(Files.isRegularFile(directory.resolve("idle-recipe.marker")), "Explicit isolated audit marker");
            check(mc.level == null && mc.getSingleplayerServer() == null, "No player or test save opened");
            check(Boolean.getBoolean("gtl.enhancedcore.memoryAuditProductionTicks"),
                    "Environment agent retains production recipe ticks and their merged hooks");
            idleAndNoise();
            invalidation();
            fuelAndPaidWork();
            mutableSubclass();
            isolatedOrderEngines();
            result.put("status", "PASS");
        } catch (Throwable error) {
            result.put("status", "FAIL"); result.put("error", error.toString()); error.printStackTrace();
        }
        result.put("checks", checks);
        result.put("scope", "Real Forge-transformed RecipeLogic.serverTick on synthetic server-side TrackedDummyWorld; virtual ordinary and ADD MutableRecipesLogic search dispatch; real registered IV controllers, buffers, IvNativeEngine.tick and IvRecipeLogic.serverTick with empty queues and halted queued/active/output-shaped jobs. Polling fields and published summary identity observed after real engine calls. No actual material/EU order settlement, player world, production throughput or FPS measurement. Missing-state branch is not injected: real compatible buffers lazily create their state.");
        try { Files.writeString(directory.resolve("idle-recipe-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    private void idleAndNoise() throws Exception {
        try (var f = new Fixture()) {
            for (int tick = 0; tick < 1200; tick++) { f.logic.updateTickSubscription(); f.tick(tick); }
            check(f.logic.searches == 60, "1200 idle ticks and 1200 subscription notifications perform exactly 60 searches: " + f.logic.searches);
            check(f.logic.failedChecks == 60, "Failed-match fallback runs only on the 60 real search ticks: " + f.logic.failedChecks);
        }
        try (var f = new Fixture()) {
            f.logic.cacheFailures = false;
            f.logic.updateTickSubscription(); f.tick(0);
            check(f.logic.subscriptionMissing(), "Non-keepSubscribing ordinary failed search retains upstream unsubscribe");
            f.logic.updateTickSubscription(); f.tick(1);
            check(!f.logic.subscriptionMissing(), "Inventory re-subscription within negative cache stays subscribed for bounded retry");
            f.logic.available = true;
            for (int tick = 2; tick < 20; tick++) f.tick(tick);
            check(f.logic.searches == 1 && f.logic.getLastRecipe() == null, "Unsignaled newly available recipe cannot bypass the 20 tick bound");
            f.tick(20);
            check(f.logic.searches == 2 && f.logic.getLastRecipe() != null, "Unsignaled recipe is found exactly at tick 20");
        }
    }

    private void invalidation() throws Exception {
        try (var f = new Fixture()) {
            f.tick(0); f.logic.markLastRecipeDirty(); f.tick(1);
            check(f.logic.searches == 2, "markLastRecipeDirty wakes search on next tick");
            f.logic.resetRecipeLogic(); f.tick(2);
            check(f.logic.searches == 3, "resetRecipeLogic invalidates negative result");
            f.logic.onMachineLoad(); f.tick(3);
            check(f.logic.searches == 4, "onMachineLoad invalidates negative result");
            f.logic.loadCustomPersistedData(new CompoundTag()); f.tick(4);
            check(f.logic.searches == 5, "Persisted-data load invalidates negative result");
            f.logic.setWorkingEnabled(false); f.tick(5);
            check(f.logic.searches == 5, "Disabled machine does not search");
            f.logic.setWorkingEnabled(true); f.tick(6);
            check(f.logic.searches == 6, "Re-enable invalidates negative result");
            f.type = com.gregtechceu.gtceu.common.data.GTRecipeTypes.MACERATOR_RECIPES; f.tick(7);
            check(f.logic.searches == 7, "Recipe-type identity change wakes search");
            var lock = (ILockRecipe)(Object) f.logic;
            lock.setLock(true); f.tick(8);
            check(f.logic.searches == 8, "Lock state change wakes search");
            lock.setLockRecipe(f.logic.sample); f.tick(9);
            check(f.logic.searches == 9, "Locked-recipe identity change wakes search");
        }
    }

    private void fuelAndPaidWork() throws Exception {
        try (var f = new Fixture()) {
            f.logic.fuel(100);
            for (int tick = 0; tick < 20; tick++) f.tick(tick);
            check(f.logic.getFuelTime() == 80 && f.logic.searches == 1, "Idle fuel decrements 100 to 80 while only one search executes");
            f.logic.paid(RecipeLogic.Status.WORKING);
            int searches = f.logic.searches;
            for (int tick = 20; tick < 40; tick++) f.tick(tick);
            check(f.logic.getProgress() == 20 && f.logic.searches == searches && f.logic.tickPayments == 20,
                    "Existing paid-state WORKING recipe progresses and invokes its tick handler all 20 ticks without searching");
            f.logic.paid(RecipeLogic.Status.WAITING);
            f.tick(40);
            check(f.logic.getProgress() == 1 && f.logic.isWorking() && f.logic.tickPayments == 21,
                    "Paid-state WAITING recipe resumes native working path on the next tick");
            check(f.logic.getLastRecipe() == f.logic.sample, "Existing paid recipe identity is preserved");
        }
        try (var f = new Fixture()) {
            f.logic.paid(RecipeLogic.Status.IDLE);
            for (int tick = 0; tick < 20; tick++) f.tick(tick);
            check(f.logic.searches == 20, "IDLE with an existing lastRecipe retains every-tick cached-recipe retry");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mutableSubclass() throws Exception {
        var f = StellarIvGuiChecks.fixture("gtceu:mage_assembler", false, false, -1);
        try {
            detachParts(f.machine());
            check(f.machine().getParts().isEmpty()
                            && !com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.crossRecipeEnabled(f.machine()),
                    "ADD subclass fixture has no warehouse membership and executes ordinary serverTick");
            var logic = new MutableProbe(f.machine());
            for (boolean mode : List.of(false, true)) {
                logic.setUseMultipleRecipes(mode); logic.resetRecipeLogic(); logic.searches = 0;
                for (int tick = 0; tick < 1200; tick++) {
                    clock(f.machine(), tick);
                    logic.updateTickSubscription(); logic.serverTick();
                }
                check(logic.searches == 60, "ADD MutableRecipesLogic virtual search override mode=" + mode + " receives exactly 60/1200 calls: " + logic.searches);
            }
        } finally { for (var holder : f.holders()) holder.setLevel(null); }
    }

    private void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); checks.add(message); }

    private void isolatedOrderEngines() throws Exception {
        for (String id : List.of("gtceu:mage_assembler", "gtl_enhancedcore:hydrothermal_leaching_complex")) {
            var f = StellarIvGuiChecks.fixture(id, false, false, -1);
            try {
                var machine = f.machine();
                var logic = machine.getRecipeLogic();
                var buffer = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine)
                        f.holders().get(1).getMetaMachine();
                var state = com.gtl.enhancedcore.common.recipe.iv.IvBuffers.state(buffer);
                // An explicit proxy entry proves availability without providing any transferable stock.
                machine.getCapabilitiesProxy().put(com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                        com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP, List.of());
                state.owner = com.gtl.enhancedcore.common.recipe.iv.IvBuffers.ownerKey(machine);
                ((com.gtl.enhancedcore.mixin.gtceu.MultiblockPartControllerPositionsAccessor) buffer)
                        .enhancedcore$getControllerPositions().add(machine.getPos());
                boolean custom = logic instanceof com.gtl.enhancedcore.common.recipe.iv.IvRecipeLogic;
                Object engine = custom ? logic : ((com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess) logic).iv$engine();
                Object polling = read(engine, "idlePolling");
                int fullTicks = 0, published = 0;
                Object previous = summary(engine, logic, custom);
                for (int tick = 0; tick < 1200; tick++) {
                    ivTick(machine, engine, custom, tick);
                    if ((boolean) read(polling, "initialized") && (long) read(polling, "lastFullTick") == tick) fullTicks++;
                    Object next = summary(engine, logic, custom);
                    if (previous != next) published++;
                    previous = next;
                }
                check(fullTicks == 60 && published == 60, id + " real IV engine empty queues: 1200 ticks -> 60 full passes and summary replacements, actual=" + fullTicks + "/" + published);

                var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "idle_iv_probe"), machine.getRecipeType()).duration(10000).buildRawRecipe();
                var key = appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.APPLE);
                for (int shape = 0; shape < 3; shape++) {
                    var job = new com.gtl.enhancedcore.common.recipe.iv.IvJob(0, recipe, new CompoundTag(), java.util.Map.of(), java.util.Map.of(), 1);
                    job.halted = true;
                    if (shape == 1) {
                        job.remaining = 0; job.parallel = 1; job.duration = 10000;
                        job.nativeRecipe = recipe; job.nativeCapacity = 1;
                    } else if (shape == 2) {
                        job.remaining = 0; job.completedOperations = 1; job.pendingOperations = 1;
                        job.pending.put(key, 1L);
                    }
                    job.validateAccounting();
                    state.jobs.add(job);
                    for (int tick = 1200 + shape * 5; tick < 1205 + shape * 5; tick++) {
                        previous = summary(engine, logic, custom);
                        ivTick(machine, engine, custom, tick);
                        check(!(boolean) read(polling, "initialized") && previous != summary(engine, logic, custom),
                                id + " nonempty halted shape=" + shape + " bypasses empty polling and publishes at tick=" + tick);
                        check(((CompoundTag) summary(engine, logic, custom)).getInt("count") == 1,
                                id + " actual engine binds warehouse and includes the real queued fixture at tick=" + tick);
                    }
                    check(state.jobs.contains(job) && job.halted && job.elapsed == 0,
                            id + " halted fixture shape=" + shape + " remains unprocessed with no transaction");
                    state.jobs.clear();
                }

                ivTick(machine, engine, custom, 1220);
                check((boolean) read(polling, "initialized"), id + " empty engine re-enters polling after orders removed");
                logic.setWorkingEnabled(false); ivTick(machine, engine, custom, 1221);
                check((long) read(polling, "lastFullTick") == 1221, id + " disabling empty engine refreshes immediately");
                logic.setWorkingEnabled(true); ivTick(machine, engine, custom, 1222);
                check((long) read(polling, "lastFullTick") == 1222, id + " enabling empty engine refreshes immediately");
                logic.loadCustomPersistedData(new CompoundTag());
                check(!(boolean) read(polling, "initialized"), id + " real persisted-data load resets polling");
                ivTick(machine, engine, custom, 1223);
                write(machine, "isFormed", false); ivTick(machine, engine, custom, 1224);
                check(!(boolean) read(polling, "initialized"), id + " unavailable structure clears idle polling");
                write(machine, "isFormed", true); ivTick(machine, engine, custom, 1225);
                check((long) read(polling, "lastFullTick") == 1225, id + " structure availability restores immediate full pass");
                detachParts(machine); ivTick(machine, engine, custom, 1226);
                check(!(boolean) read(polling, "initialized"), id + " missing warehouse cannot remain in proven-empty polling");
            } finally { for (var holder : f.holders()) holder.setLevel(null); }
        }
    }

    private static void ivTick(com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine machine,
            Object engine, boolean custom, long tick) throws Exception {
        clock(machine, tick);
        if (custom) ((RecipeLogic) engine).serverTick();
        else ((com.gtl.enhancedcore.common.recipe.iv.IvNativeEngine) engine).tick();
    }

    private static Object summary(Object engine, RecipeLogic logic, boolean custom) throws Exception {
        // The public native accessor deliberately returns a copy; observe the owned snapshot itself.
        return custom ? read(engine, "orderSummary") : read(logic, "iv$summary");
    }

    private static Object read(Object object, String name) throws Exception { return field(object, name).get(object); }
    private static void write(Object object, String name, Object value) throws Exception { field(object, name).set(object, value); }
    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static void clock(com.gregtechceu.gtceu.api.machine.MetaMachine machine, long tick) throws Exception {
        Field offset = machine.holder.getClass().getDeclaredField("offset"); offset.setAccessible(true);
        // Fixture's synthetic world's getGameTime is fixed. Mutate only its private holder offset.
        offset.setLong(machine.holder, tick - machine.getLevel().getGameTime());
    }

    private static void detachParts(com.gregtechceu.gtceu.api.machine.MetaMachine machine) throws Exception {
        // This suite exercises ordinary serverTick; dedicated isolated-order engines have their own audit.
        // GTCEu getParts rebuilds from partPositions whenever the two sizes differ.
        // Clear both sides of synthetic membership so a later accessor cannot restore the warehouse.
        write(machine, "parts", new ArrayList<>());
        write(machine, "partPositions", new net.minecraft.core.BlockPos[0]);
    }

    private static final class Fixture implements AutoCloseable {
        final StellarIvGuiChecks.Fixture real;
        GTRecipeType type;
        SearchProbe logic;
        Fixture() throws Exception {
            real = StellarIvGuiChecks.fixture("gtl_enhancedcore:platinum_refining_matrix", false, false, -1);
            detachParts(real.machine());
            type = real.machine().getRecipeType();
            IRecipeLogicMachine proxy = (IRecipeLogicMachine) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{IRecipeLogicMachine.class}, (ignored, method, args) -> {
                        return switch (method.getName()) {
                            case "self" -> real.machine();
                            case "getRecipeType" -> type;
                            case "getRecipeLogic" -> logic;
                            case "keepSubscribing", "shouldWorkingPlaySound", "dampingWhenWaiting" -> false;
                            case "isRecipeLogicAvailable", "onWorking", "beforeWorking" -> true;
                            case "notifyStatusChanged", "onWaiting", "afterWorking" -> null;
                            default -> method.invoke(real.machine(), args);
                        };
                    });
            logic = new SearchProbe(proxy);
        }
        void tick(long tick) throws Exception { clock(real.machine(), tick); logic.serverTick(); }
        @Override public void close() { for (var holder : real.holders()) holder.setLevel(null); }
    }

    private static final class SearchProbe extends RecipeLogic {
        int searches, failedChecks, tickPayments;
        boolean cacheFailures = true, available;
        final GTRecipe sample;
        SearchProbe(IRecipeLogicMachine machine) {
            super(machine);
            sample = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "idle_runtime_probe"), machine.getRecipeType()).duration(10000).buildRawRecipe();
        }
        @Override public void findAndHandleRecipe() {
            searches++; recipeDirty = false;
            lastFailedMatches = cacheFailures ? List.of(sample) : null;
            if (available) { lastRecipe = sample; duration = 10000; setStatus(Status.WORKING); lastFailedMatches = null; }
        }
        @Override public boolean checkMatchedRecipeAvailable(GTRecipe recipe) { failedChecks++; return false; }
        @Override public boolean handleFuelRecipe() { return true; }
        @Override public GTRecipe.ActionResult handleTickRecipe(GTRecipe recipe) { tickPayments++; return GTRecipe.ActionResult.SUCCESS; }
        void fuel(int value) { fuelTime = value; }
        boolean subscriptionMissing() { return subscription == null; }
        void paid(Status state) { lastRecipe = sample; duration = 10000; progress = 0; setStatus(state); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class MutableProbe extends MutableRecipesLogic {
        int searches;
        MutableProbe(com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine machine) { super(machine); }
        @Override public void findAndHandleRecipe() { searches++; recipeDirty = false; lastFailedMatches = null; }
    }
}
