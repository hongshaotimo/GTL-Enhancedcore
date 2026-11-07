package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.common.recipe.iv.ProcessingDispatchBatch;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

/** Real Forge/AE extraction and ADD inventory checks, without opening a world. */
@Mod("enhancedcore_audit")
public final class ProcessingDispatchChecks {
    private boolean done;
    private final List<String> checks = new ArrayList<>();
    public ProcessingDispatchChecks() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.processingDispatch.audit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        var directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        var report = new LinkedHashMap<String, Object>();
        try {
            require(directory.getParent().equals(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/super-buffer-overflow-20261107"))
                    && Files.isRegularFile(directory.resolve("processing-dispatch.marker")), "Explicit disposable fixture");
            require(mc.level == null && mc.getSingleplayerServer() == null, "No saved world opened");
            SuperBufferWideStockChecks.run(checks);
            if (Boolean.getBoolean("gtl.processingDispatch.native")) NativeWideBatchChecks.run(checks);
            ProcessingCpuHookChecks.run(checks);
            if (!Boolean.getBoolean("gtl.processingDispatch.baseline")) ProcessingInitialInputsChecks.run(checks);
            extraction();
            report.put("status", "PASS");
        } catch (Throwable failure) {
            failure.printStackTrace(); report.put("status", "FAIL"); report.put("failure", failure.toString());
        }
        report.put("checks", checks); report.put("worldOpened", false);
        try { Files.writeString(directory.resolve("processing-dispatch.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
    private void extraction() {
        treatedWoodPipeExtraction();
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var pattern = pattern(new GenericStack[]{new GenericStack(iron, 1), new GenericStack(water, 1000)},
                new GenericStack[]{new GenericStack(gold, 1)});
        long requested = 9_200_000_000_000_000_000L;
        var raw = inventory(); raw.insert(iron, requested, Actionable.MODULATE); raw.insert(water, Long.MAX_VALUE, Actionable.MODULATE);
        var expected = new KeyCounter();
        var extracted = AEUtils.extractForProcessingPattern(pattern, raw, expected, requested);
        require(extracted != null && extracted[0].get(iron) == requested && extracted[1].get(water) == Long.MAX_VALUE
                        && extracted[1].get(water) / 1000 != requested,
                "Reproduced upstream 9.2E dispatch: fluid multiplication saturates and breaks whole-pattern ratios");
        if (Boolean.getBoolean("gtl.processingDispatch.baseline")) return;
        var source = inventory(); source.insert(iron, requested, Actionable.MODULATE); source.insert(water, Long.MAX_VALUE, Actionable.MODULATE);
        var waiting = inventory();
        long batch = ProcessingDispatchBatch.limit(pattern, requested, source, waiting.list::get);
        require(batch == Long.MAX_VALUE / 1000, "9.2E request clips to exact available whole fluid batches");
        expected = new KeyCounter();
        extracted = ProcessingDispatchBatch.extract(pattern, source, expected, batch, waiting.list::get);
        require(extracted != null && extracted[0].get(iron) == batch && extracted[1].get(water) == batch * 1000
                && expected.get(gold) == batch && source.list.get(iron) == requested - batch
                && source.list.get(water) == Long.MAX_VALUE % 1000, "Exact extraction preserves input ratio, CPU remainder and expected output");
        var before = source.writeToNBT().copy();
        require(ProcessingDispatchBatch.extract(pattern, source, new KeyCounter(), 1, waiting.list::get) == null
                && source.writeToNBT().equals(before), "Insufficient next whole batch rejects without moving inventory");
        source.insert(water, 2000, Actionable.MODULATE);
        require(ProcessingDispatchBatch.limit(pattern, requested - batch, source, waiting.list::get) == 2,
                "Newly available material enables the remaining CPU task without resubmission");
        var repeated = pattern(new GenericStack[]{new GenericStack(iron, 1)},
                new GenericStack[]{new GenericStack(gold, 2), new GenericStack(gold, 3)});
        source = inventory(); source.insert(iron, Long.MAX_VALUE, Actionable.MODULATE);
        waiting.insert(gold, Long.MAX_VALUE - 15, Actionable.MODULATE);
        require(ProcessingDispatchBatch.limit(repeated, Long.MAX_VALUE, source, waiting.list::get) == 3,
                "Repeated output identity and prior CPU waiting reserve exact long headroom");
        expected = new KeyCounter();
        require(ProcessingDispatchBatch.extract(repeated, source, expected, 3, waiting.list::get) != null
                && expected.get(gold) == 15, "Output totals never wrap at the long boundary");
        waiting.insert(gold, 15, Actionable.MODULATE); before = source.writeToNBT().copy();
        require(ProcessingDispatchBatch.extract(repeated, source, new KeyCounter(), 1, waiting.list::get) == null
                && source.writeToNBT().equals(before), "Full waiting ledger blocks another dispatch before consuming inputs");
    }
    private void treatedWoodPipeExtraction() {
        var plateStack = ChemicalHelper.get(TagPrefix.plate, GTMaterials.TreatedWood);
        var pipeStack = ChemicalHelper.get(TagPrefix.pipeSmallFluid, GTMaterials.TreatedWood);
        require(!plateStack.isEmpty() && !pipeStack.isEmpty(),
                "Player pipe case resolves actual TreatedWood plate and small-fluid-pipe registration");
        var plate = AEItemKey.of(plateStack);
        var pipe = AEItemKey.of(pipeStack);
        var glue = AEFluidKey.of(GTMaterials.Glue.getFluid());
        var circuitStack = IntCircuitBehaviour.stack(12);
        var circuit = AEItemKey.of(circuitStack);
        require(plate.getId().toString().equals("gtceu:treated_wood_plate")
                        && pipe.getId().toString().equals("gtceu:treated_wood_small_fluid_pipe")
                        && glue.getId().toString().equals("gtceu:glue")
                        && IntCircuitBehaviour.getCircuitConfiguration(circuitStack) == 12
                        && AEUtils.isIntegratedCircuit(circuit),
                "Player case uses registered treated-wood plate, glue, gtceu:treated_wood_small_fluid_pipe and circuit 12");
        long requested = 9_200_000_000_000_000_000L;
        var configured = pattern(new GenericStack[] {new GenericStack(plate, 1),
                        new GenericStack(glue, 50), new GenericStack(circuit, 1)},
                new GenericStack[] {new GenericStack(pipe, 1)});
        var raw = inventory();
        raw.insert(plate, requested, Actionable.MODULATE);
        raw.insert(glue, Long.MAX_VALUE, Actionable.MODULATE);
        raw.insert(circuit, 1, Actionable.MODULATE);
        var expected = new KeyCounter();
        var extracted = AEUtils.extractForProcessingPattern(configured, raw, expected, requested);
        require(extracted != null && offered(extracted, plate) == requested
                        && offered(extracted, glue) == Long.MAX_VALUE
                        && offered(extracted, glue) / 50 != requested
                        && offered(extracted, circuit) == 1 && expected.get(pipe) == requested,
                "Player 9.2E pipe baseline reproduces saturated glue and false 1:50 ratio while circuit 12 stays fixed at one");
        if (Boolean.getBoolean("gtl.processingDispatch.baseline")) return;
        var configuredSource = inventory();
        configuredSource.insert(plate, requested, Actionable.MODULATE);
        configuredSource.insert(glue, Long.MAX_VALUE, Actionable.MODULATE);
        configuredSource.insert(circuit, 1, Actionable.MODULATE);
        var waiting = inventory();
        long first = ProcessingDispatchBatch.limit(configured, requested, configuredSource, waiting.list::get);
        expected = new KeyCounter();
        extracted = ProcessingDispatchBatch.extract(configured, configuredSource, expected, first, waiting.list::get);
        require(first == Long.MAX_VALUE / 50 && extracted != null
                        && offered(extracted, plate) == first && offered(extracted, glue) == first * 50
                        && offered(extracted, circuit) == 1 && expected.get(pipe) == first
                        && configuredSource.list.get(plate) == requested - first
                        && configuredSource.list.get(glue) == Long.MAX_VALUE % 50,
                "Player pipe fixed dispatch preserves exact 1 plate:50mB glue:1 pipe and one circuit-12 configuration");
        // The machine's circuitMeta(12) is non-consumable configuration. The continuation
        // below therefore tracks only the physical plate/glue materials across CPU windows.
        var materials = pattern(new GenericStack[] {new GenericStack(plate, 1), new GenericStack(glue, 50)},
                new GenericStack[] {new GenericStack(pipe, 1)});
        var source = inventory(); source.insert(plate, requested, Actionable.MODULATE);
        source.insert(glue, Long.MAX_VALUE, Actionable.MODULATE);
        waiting = inventory();
        long remaining = requested;
        int batches = 0;
        var glueSupplied = BigInteger.valueOf(Long.MAX_VALUE);
        var glueConsumed = BigInteger.ZERO;
        while (remaining > 0) {
            long operations = ProcessingDispatchBatch.limit(materials, remaining, source, waiting.list::get);
            if (operations == 0) {
                var before = source.writeToNBT().copy();
                require(ProcessingDispatchBatch.extract(materials, source, new KeyCounter(), 1, waiting.list::get) == null
                                && source.writeToNBT().equals(before),
                        "Player pipe exhausted glue window rejects without losing plates or the fluid remainder");
                long held = source.list.get(glue);
                long target = BigInteger.valueOf(remaining).multiply(BigInteger.valueOf(50))
                        .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
                long arriving = target - held;
                source.insert(glue, arriving, Actionable.MODULATE);
                glueSupplied = glueSupplied.add(BigInteger.valueOf(arriving));
                operations = ProcessingDispatchBatch.limit(materials, remaining, source, waiting.list::get);
            }
            require(operations > 0 && operations <= Long.MAX_VALUE / 50,
                    "Player pipe continuation chooses a positive exact long-sized glue batch");
            long heldGlue = source.list.get(glue);
            expected = new KeyCounter();
            extracted = ProcessingDispatchBatch.extract(materials, source, expected, operations, waiting.list::get);
            require(extracted != null && offered(extracted, plate) == operations
                            && offered(extracted, glue) == operations * 50 && expected.get(pipe) == operations,
                    "Player pipe continuation preserves full input/output ratios in batch " + (batches + 1));
            remaining -= operations;
            glueConsumed = glueConsumed.add(BigInteger.valueOf(operations).multiply(BigInteger.valueOf(50)));
            waiting.insert(pipe, operations, Actionable.MODULATE);
            require(source.list.get(plate) == remaining && source.list.get(glue) == heldGlue - operations * 50
                            && waiting.list.get(pipe) == requested - remaining,
                    "Player pipe continuation retains exact plates, glue and cumulative expected pipes");
            batches++;
        }
        require(batches == 50 && waiting.list.get(pipe) == requested && source.list.get(plate) == 0
                        && source.list.get(glue) == 0 && glueConsumed.equals(glueSupplied)
                        && glueConsumed.equals(BigInteger.valueOf(requested).multiply(BigInteger.valueOf(50))),
                "Player 9.2E pipe order completes 50 bounded extraction batches with exact total glue and no remainder");
    }
    private static long offered(KeyCounter[] inputs, AEKey key) {
        long amount = 0;
        for (var input : inputs) amount = Math.addExact(amount, input.get(key));
        return amount;
    }
    private static ListCraftingInventory inventory() { return new ListCraftingInventory(key -> {}); }
    private static IPatternDetails pattern(GenericStack[] inputs, GenericStack[] outputs) {
        return new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(PatternDetailsHelper.encodeProcessingPattern(inputs, outputs)));
    }
    private void require(boolean valid, String description) { if (!valid) throw new AssertionError(description); checks.add(description); }
}
