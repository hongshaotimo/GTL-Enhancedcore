package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import com.gtl.enhancedcore.common.recipe.iv.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.recipe.BatchProcessing;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.config.ConfigHolder;

/** Calls real registered suppliers, modifier chains and GTLCore batch code with task-local stock. */
public final class StellarIvBatchChecks {
    private StellarIvBatchChecks() {}
    public static List<String> run() throws Exception {
        var checks = new ArrayList<String>();
        int previousWindow = ConfigHolder.INSTANCE.batchProcessingTimeLimitTicks;
        try {
            for (String id : List.of("gtceu:suprachronal_assembly_line", "gtl_enhancedcore:platinum_refining_matrix")) {
                var fixture = StellarIvGuiChecks.fixture(id, false, true, true);
                var machine = fixture.machine();
                var input = new NotifiableItemStackHandler(machine, 64, IO.IN);
                var output = new NotifiableItemStackHandler(machine, 64, IO.OUT);
                for (int i = 0; i < 64; i++) input.setStackInSlot(i, new ItemStack(Items.APPLE, 64));
                var energy = NotifiableEnergyContainer.receiverContainer(machine, 1_000_000_000L, 8, 1);
                energy.setEnergyStored(1_000_000_000L);
                set(machine, "energyContainer", new EnergyContainerList(List.of(energy)));
                set(machine, "tier", 0);
                machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input));
                machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output));
                machine.getCapabilitiesProxy().put(IO.IN, EURecipeCapability.CAP, List.of(energy));
                // GTLCore uses a second collection of RecipeHandlePart objects, normally
                // populated at structure formation. Proxy insertion alone is not formation.
                set(machine, "normalCapabilities", new EnumMap<IO,List<org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart>>(Map.of(
                        IO.IN, List.of(org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart.of(IO.IN, List.of(input))),
                        IO.OUT, List.of(org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart.of(IO.OUT, List.of(output))))));
                require(!id.startsWith("gtceu:") || IvMachineScope.crossRecipeEnabled(machine), id + " real isolation buffer active", checks);
                require(!id.startsWith("gtceu:") || !IvMachineScope.batching(machine), id + " galaxy does not force the IV engine into ADD 20-tick mode", checks);
                var batchMachine = (IBatchMachine) machine;
                require(batchMachine.supportsBatchProcessing(), id + " native batch switch stays supported with 64 arrays", checks);
                require(batchMachine.canConfigureBatchProcessing(), id + " native batch switch remains configurable", checks);
                Map<AEKey,Long> stock = Map.of(AEItemKey.of(Items.APPLE), 4096L);
                for (int window : List.of(32, 64)) {
                    for (int i = 0; i < 64; i++) input.setStackInSlot(i, new ItemStack(Items.APPLE, 64));
                    ConfigHolder.INSTANCE.batchProcessingTimeLimitTicks = window;
                    var origin = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "stellar_iv_batch_probe"), machine.getRecipeType())
                            .inputItems(Items.APPLE).chancedOutput(new ItemStack(Items.GOLD_NUGGET), 3000, 0)
                            .EUt(8).duration(2).buildRawRecipe();
                    origin.data.putString("audit", "unchanged");
                    var rawData = origin.data.copy();
                    batchMachine.setBatchEnabled(false);
                    var single = new IvNativeMatchContext(machine, stock, Map.of(), 4096, 1).prepare(origin);
                    require(single != null, id + " native modifier accepts synthetic recipe", checks);
                    batchMachine.setBatchEnabled(true);
                    var context = new IvNativeMatchContext(machine, stock, Map.of(), 4096, 1);
                    var prepared = context.prepare(origin);
                    long baseParallel = IGTRecipe.of(single).getRealParallels();
                    int expectedCycles = window / single.duration;
                    require(prepared != null && prepared.duration == single.duration * expectedCycles,
                            id + " batch on/off uses real time window " + window + ": " + (prepared == null ? "null" : prepared.duration), checks);
                    long operations = IGTRecipe.of(prepared).getRealParallels();
                    require(operations == baseParallel * expectedCycles && context.reserved() == 1,
                            id + " sequential cycles do not spend additional parallel capacity", checks);
                    require(RecipeHelper.getInputEUt(prepared) == RecipeHelper.getInputEUt(single)
                                    && RecipeHelper.getInputEUt(prepared) * prepared.duration
                                            == RecipeHelper.getInputEUt(single) * single.duration * expectedCycles,
                            id + " native batch conserves EU", checks);
                    require(IvRecipeInputs.itemAmount(ItemRecipeCapability.CAP.of(prepared.inputs.get(ItemRecipeCapability.CAP).getFirst().content)) == operations
                                    && IvRecipeInputs.itemAmount(ItemRecipeCapability.CAP.of(prepared.outputs.get(ItemRecipeCapability.CAP).getFirst().content)) == operations,
                            id + " native batch input/output amounts scale together", checks);
                    require(prepared.outputs.get(ItemRecipeCapability.CAP).getFirst().chance == 3000,
                            id + " admission keeps chance unresolved", checks);
                    var scale = GalaxyRecipeBatch.class.getDeclaredMethod("scale", GTRecipe.class, long.class);
                    scale.setAccessible(true);
                    var doubled = (GTRecipe) scale.invoke(null, prepared, 2L);
                    require(IGTRecipe.of(doubled).getRealParallels() == operations * 2
                                    && IvRecipeInputs.itemAmount(ItemRecipeCapability.CAP.of(doubled.outputs.get(ItemRecipeCapability.CAP).getFirst().content)) == operations * 2
                                    && doubled.outputs.get(ItemRecipeCapability.CAP).getFirst().chance == 3000,
                            id + " Galaxy.scale preserves native cycles and deferred chance denominator", checks);
                    require(origin.duration == 2 && origin.data.equals(rawData) && IGTRecipe.of(origin).getRealParallels() == 1,
                            id + " shared origin remains unchanged", checks);
                    require(stock.get(AEItemKey.of(Items.APPLE)) == 4096 && input.getStackInSlot(0).getCount() == 64,
                            id + " preparation does not withdraw items", checks);
                    if (id.endsWith("platinum_refining_matrix")) {
                        // Keep exactly one time window of material. Finite 8 EU/t input
                        // would otherwise extend both on/off union batches to 3072 ticks,
                        // hiding the observable native minimum-duration difference.
                        for (int i = 0; i < 64; i++) input.setStackInSlot(i, ItemStack.EMPTY);
                        input.setStackInSlot(0, new ItemStack(Items.APPLE, expectedCycles));
                        var guaranteed = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "stellar_galaxy_find_probe"), machine.getRecipeType())
                                .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(8).duration(2).buildRawRecipe();
                        var lock = (org.gtlcore.gtlcore.api.machine.trait.ILockRecipe) machine.getRecipeLogic();
                        // GTLCore setLock clears the previous lock recipe before enabling it.
                        lock.setLock(true); lock.setLockRecipe(guaranteed);
                        require(lock.isLock() && lock.getLockRecipe() == guaranteed,
                                id + " real lock holds the synthetic origin", checks);
                        boolean matched = org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipe(machine, guaranteed);
                        System.out.println("[STELLAR_BATCH] ordinary physical proxy match=" + matched
                                + " outputLimits=" + machine.getOutputLimits());
                        require(matched, id + " synthetic formed RecipeHandleParts match one physical recipe", checks);
                        var nativeModified = machine.fullModifyRecipe(guaranteed.copy(),
                                new com.gregtechceu.gtceu.api.recipe.logic.OCParams(),
                                new com.gregtechceu.gtceu.api.recipe.logic.OCResult());
                        var found = GalaxyRecipeBatch.find(machine, machine.getRecipeLogic(), 0);
                        System.out.println("[STELLAR_BATCH] actual find " + id + " window=" + window
                                + " found=" + describe(found) + " prepared=" + describe(prepared)
                                + " nativeFull=" + describe(nativeModified) + " power=" + machine.getOverclockVoltage()
                                + " available=" + machine.isRecipeLogicAvailable() + " proxies=" + machine.hasProxies());
                        if (found == null && nativeModified != null) {
                            var copy = nativeModified.copy(); copy.data = nativeModified.data.copy();
                            var candidate = com.gtl.enhancedcore.common.recipe.RecipeBatchPreparation.apply(machine, guaranteed, copy);
                            var build = GalaxyRecipeBatch.class.getDeclaredMethod("build", WorkableElectricMultiblockMachine.class, List.class, boolean.class);
                            build.setAccessible(true);
                            var built = candidate == null ? null : (GTRecipe) build.invoke(null, machine, List.of(candidate), false);
                            System.out.println("[STELLAR_BATCH] candidate=" + describe(candidate) + " built=" + describe(built)
                                    + " conditions=" + (built == null ? "null" : built.checkConditions(machine.getRecipeLogic()))
                                    + " tick=" + (built == null ? "null" : built.matchTickRecipe(machine))
                                    + " in=" + (built != null && org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipeInputNocache(machine, built))
                                    + " out=" + (built != null && org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipeOutput(machine, built))
                                    + " valid=" + (built != null && com.gtl.enhancedcore.common.recipe.FactoryRecipeSafety.valid(built)));
                        }
                        require(found != null && found.duration == prepared.duration && found.data.getBoolean(GalaxyRecipeBatch.MARKER),
                                id + " actual Galaxy.find applies time-window batch before final admission " + window
                                        + "; found=" + describe(found) + "; expectedCycles=" + expectedCycles
                                        + "; prepared=" + describe(prepared), checks);
                        batchMachine.setBatchEnabled(false);
                        var foundOff = GalaxyRecipeBatch.find(machine, machine.getRecipeLogic(), 0);
                        require(foundOff != null && foundOff.duration < found.duration,
                                id + " actual Galaxy.find preserves observable batch on/off timing: "
                                        + describe(found) + " / " + describe(foundOff), checks);
                        for (var union : List.of(found, foundOff)) {
                            long acceptedInput = IvRecipeInputs.itemAmount(ItemRecipeCapability.CAP.of(union.inputs.get(ItemRecipeCapability.CAP).getFirst().content));
                            long deliveredOutput = IvRecipeInputs.itemAmount(ItemRecipeCapability.CAP.of(union.outputs.get(ItemRecipeCapability.CAP).getFirst().content));
                            require(acceptedInput == expectedCycles && deliveredOutput == expectedCycles,
                                    id + " actual Galaxy on/off input/output conservation " + expectedCycles, checks);
                            require(RecipeHelper.getInputEUt(union) * union.duration
                                            == RecipeHelper.getInputEUt(single) * single.duration * expectedCycles,
                                    id + " actual Galaxy on/off exact total EU conservation", checks);
                        }
                        batchMachine.setBatchEnabled(true);
                        // A custom setup may hand us an already batch-processed recipe.
                        // Preserve that decision and prove the null-origin API is idempotent.
                        var custom = prepared.copy(); custom.data = prepared.data.copy();
                        org.gtlcore.gtlcore.api.recipe.RecipeExtensionCopier.copy(prepared, custom);
                        var customAgain = com.gtl.enhancedcore.common.recipe.RecipeBatchPreparation.apply(machine, null, custom);
                        require(customAgain != null && customAgain.duration == prepared.duration
                                        && IGTRecipe.of(customAgain).getRealParallels() == operations,
                                id + " null-origin native batch API does not batch a paid candidate twice; "
                                        + describe(customAgain), checks);
                        var expanded = GalaxyRecipeBatch.expand(machine.getRecipeLogic(), customAgain);
                        require(expanded != null && expanded.data.getBoolean(GalaxyRecipeBatch.MARKER)
                                        && expanded.duration >= prepared.duration,
                                id + " custom setup expands a native-prepared candidate once; "
                                        + describe(expanded) + "; custom=" + describe(customAgain), checks);
                        require(input.getStackInSlot(0).getCount() == expectedCycles && output.getStackInSlot(0).isEmpty(),
                                id + " actual Galaxy.find/expand simulate without consuming or delivering", checks);
                    }
                }
                for (var holder : fixture.holders()) holder.setLevel(null);
            }
        } catch (Throwable failure) {
            var report = new LinkedHashMap<String,Object>(); report.put("checks", checks); report.put("error", failure.toString());
            try { java.nio.file.Files.writeString(net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("stellar-batch-checks.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            throw failure;
        } finally { ConfigHolder.INSTANCE.batchProcessingTimeLimitTicks = previousWindow; }
        return checks;
    }
    private static String describe(GTRecipe recipe) {
        return recipe == null ? "null" : "duration=" + recipe.duration + ",eut=" + RecipeHelper.getInputEUt(recipe)
                + ",real=" + IGTRecipe.of(recipe).getRealParallels() + ",batchSize=" + IGTRecipe.of(recipe).getBatchSize()
                + ",marker=" + recipe.data.getBoolean(GalaxyRecipeBatch.MARKER);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(object, value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add(label);
    }
}
