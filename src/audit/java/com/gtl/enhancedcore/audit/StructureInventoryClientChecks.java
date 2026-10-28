package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.structure.NativeStructureHollowing;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Exports the live registered multiblock patterns from an isolated title-screen audit. */
@Mod("enhancedcore_audit")
public final class StructureInventoryClientChecks {
    private static final char[] SYMBOLS = symbols();
    private boolean done;

    public StructureInventoryClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END
                || !Boolean.getBoolean("gtl.enhancedcore.structureInventoryAudit")) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || minecraft.screen == null) return;
        done = true;
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> machines = new ArrayList<>();
        List<Map<String, String>> failures = new ArrayList<>();
        Path gameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            check(gameDirectory.startsWith(Path.of("C:/IDEA/GTL-Enhancedcore/_audit/structure-inventory-20261021"))
                    && minecraft.level == null && minecraft.player == null && minecraft.getSingleplayerServer() == null,
                    "Isolated title screen; no world opened");
            check(Files.isRegularFile(gameDirectory.resolve("structure-inventory.marker")),
                    "Explicit task-owned structure inventory marker");
            Path output = gameDirectory.resolve("structure-patterns");
            Files.createDirectories(output);
            int definitions = 0;
            for (var machine : GTRegistries.MACHINES.values()) {
                if (!(machine instanceof MultiblockMachineDefinition definition)) continue;
                definitions++;
                try {
                    BlockPattern pattern = definition.getPatternFactory().get();
                    if (pattern == null) throw new IllegalStateException("null pattern");
                    var grid = ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches();
                    var entry = export(definition, pattern, grid, output);
                    machines.add(entry);
                } catch (Throwable error) {
                    failures.add(Map.of("id", definition.getId().toString(), "error", error.toString()));
                }
            }
            result.put("status", failures.isEmpty() && definitions > 0 ? "PASS" : "FAIL");
            result.put("scope", "All registered MultiblockMachineDefinition patterns after the startup structure switch; title screen only");
            result.put("registeredMultiblockDefinitions", definitions);
            result.put("patternsExported", machines.size());
            result.put("patternFailures", failures.size());
            result.put("removedFixedHiddenCells", NativeStructureHollowing.removedCellsByMachine());
            result.put("machines", machines);
            result.put("failures", failures);
        } catch (Throwable error) {
            result.put("status", "FAIL");
            result.put("error", error.toString());
            error.printStackTrace();
        }
        try {
            Files.writeString(gameDirectory.resolve("structure-inventory.json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(result),
                    StandardCharsets.UTF_8);
        } catch (Exception error) { error.printStackTrace(); }
        minecraft.stop();
    }

    private static Map<String, Object> export(MultiblockMachineDefinition definition, BlockPattern pattern,
                                               TraceabilityPredicate[][][] grid, Path output) throws Exception {
        int depth = grid.length, height = depth == 0 ? 0 : grid[0].length;
        int width = height == 0 ? 0 : grid[0][0].length;
        if (width == 0 || height == 0 || depth == 0) throw new IllegalStateException("empty dimensions");
        IdentityHashMap<TraceabilityPredicate, Integer> symbols = new IdentityHashMap<>();
        Map<String, Object> predicates = new LinkedHashMap<>();
        Map<String, Integer> predicateTypes = new HashMap<>();
        StringBuilder payload = new StringBuilder().append(width).append(' ').append(height).append(' ')
                .append(depth).append('\n');
        for (int z = 0; z < depth; z++) {
            if (grid[z].length != height) throw new IllegalStateException("ragged height");
            for (int y = 0; y < height; y++) {
                if (grid[z][y].length != width) throw new IllegalStateException("ragged width");
                for (TraceabilityPredicate predicate : grid[z][y]) {
                    int key = symbols.computeIfAbsent(predicate, ignored -> symbols.size());
                    if (key >= SYMBOLS.length) continue;
                    predicateTypes.merge(predicate == null ? "null" : predicate.getClass().getName(), 1, Integer::sum);
                }
                for (TraceabilityPredicate predicate : grid[z][y]) {
                    int key = symbols.get(predicate);
                    payload.append(key < SYMBOLS.length ? SYMBOLS[key] : '?');
                }
                payload.append('\n');
            }
        }
        for (var entry : symbols.entrySet()) predicates.put(Character.toString(
                entry.getValue() < SYMBOLS.length ? SYMBOLS[entry.getValue()] : '?'), predicateInfo(entry.getKey()));

        var size = new LinkedHashMap<String, Object>();
        size.put("width", width); size.put("height", height); size.put("depth", depth);
        size.put("cells", (long) width * height * depth);
        size.put("predicateKinds", symbols.size());
        size.put("predicateTypeCountsByPosition", predicateTypes);
        size.put("repetitions", pattern.aisleRepetitions);
        size.put("predicates", predicates);
        int removed = NativeStructureHollowing.removedCellsByMachine().getOrDefault(definition.getId(), 0);
        size.put("removedFixedHiddenCells", removed);
        String file = definition.getId().toString().replace(':', '_').replace('/', '_') + ".pattern.gz";
        if (symbols.size() <= SYMBOLS.length) {
            try (var gzip = new GZIPOutputStream(Files.newOutputStream(output.resolve(file)))) {
                gzip.write(payload.toString().getBytes(StandardCharsets.US_ASCII));
            }
            size.put("geometrySnapshot", "structure-patterns/" + file);
        } else {
            size.put("geometrySnapshot", "omitted-more-than-" + SYMBOLS.length + "-predicate-symbols");
        }
        size.put("id", definition.getId().toString());
        return size;
    }

    private static Map<String, Object> predicateInfo(TraceabilityPredicate predicate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("class", predicate == null ? "null" : predicate.getClass().getName());
        if (predicate == null) return result;
        result.put("controller", predicate.isController);
        result.put("any", predicate.isAny());
        result.put("air", predicate.isAir());
        result.put("common", simplePredicates(predicate.common));
        result.put("limited", simplePredicates(predicate.limited));
        return result;
    }

    private static List<Map<String, Object>> simplePredicates(List<SimplePredicate> values) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SimplePredicate simple : values) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("class", simple.getClass().getName()); item.put("type", simple.type);
            item.put("minimum", simple.minCount); item.put("maximum", simple.maxCount);
            item.put("minimumPerLayer", simple.minLayerCount); item.put("maximumPerLayer", simple.maxLayerCount);
            item.put("previewCount", simple.previewCount); item.put("io", simple.io == null ? null : simple.io.name());
            item.put("slotName", simple.slotName); item.put("nbtParser", simple.nbtParser);
            List<String> candidates = new ArrayList<>();
            try {
                BlockInfo[] infos = simple.candidates == null ? null : simple.candidates.get();
                if (infos != null) for (BlockInfo info : infos) {
                    if (info == null) { candidates.add("<null>"); continue; }
                    BlockState state = info.getBlockState();
                    var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    candidates.add((id == null ? "<unregistered>" : id.toString()) + state.getValues());
                }
            } catch (Throwable error) { candidates.add("<error:" + error.getClass().getSimpleName() + ">"); }
            item.put("candidates", candidates);
            result.add(item);
        }
        return result;
    }

    private static char[] symbols() {
        String printable = "!#$%&'()*+,-./0123456789:;=?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[]^_`abcdefghijklmnopqrstuvwxyz{}~";
        return printable.toCharArray();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
