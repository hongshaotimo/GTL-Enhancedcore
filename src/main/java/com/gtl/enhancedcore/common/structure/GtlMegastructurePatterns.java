package com.gtl.enhancedcore.common.structure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.common.base.Suppliers;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

/** Replace geometry while retaining the installed GTL version's ability and tier predicates. */
public final class GtlMegastructurePatterns {
    private static final String ROOT = "/data/gtl_enhancedcore/structures/gtl/";
    private static final JsonObject MANIFEST = readManifest();
    private static final Map<BlockPattern, ResourceLocation> REPLACEMENTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Integer> FACTORY_DEPTH = ThreadLocal.withInitial(() -> 0);

    private GtlMegastructurePatterns() {}

    private static JsonObject readManifest() {
        var manifest = new JsonObject();
        for (String name : new String[]{"manifest.json", "upgrades.json", "upgrades_20260927.json",
                "lucid_etchdreamer.json"}) {
            try (var input = GtlMegastructurePatterns.class.getResourceAsStream(ROOT + name)) {
                if (input == null) throw new IOException("Missing GTL structure manifest: " + name);
                var entries = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : entries.entrySet()) {
                    if (manifest.has(entry.getKey())) throw new IOException("Duplicate structure: " + entry.getKey());
                    manifest.add(entry.getKey(), entry.getValue());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return manifest;
    }

    public static boolean targets(ResourceLocation id) {
        if (id == null || !MANIFEST.has(id.getPath())) return false;
        var spec = MANIFEST.getAsJsonObject(id.getPath());
        return id.getNamespace().equals(spec.has("namespace") ? spec.get("namespace").getAsString() : "gtceu");
    }

    public static boolean needsLucidPreview(BlockPattern pattern) {
        return new ResourceLocation("gtladditions", "lucid_etchdreamer").equals(REPLACEMENTS.get(pattern));
    }

    public static BlockInfo lucidPreviewCoil() {
        var binding = MANIFEST.getAsJsonObject("lucid_etchdreamer")
                .getAsJsonObject("bindings").getAsJsonObject("P");
        return BlockInfo.fromBlockState(block(binding, "preview").defaultBlockState());
    }

    public static Supplier<BlockPattern> wrapFactory(ResourceLocation id, Supplier<BlockPattern> source) {
        // Upstream addons wrap the previous factory to patch its original predicates.
        // Only the outermost consumer may replace geometry; raw and replaced caches
        // remain separate even if a factory was queried before another addon wrapped it.
        Supplier<BlockPattern> raw = Suppliers.memoize(() -> {
            int depth = FACTORY_DEPTH.get();
            FACTORY_DEPTH.set(depth + 1);
            try {
                return source.get();
            } finally {
                if (depth == 0) FACTORY_DEPTH.remove();
                else FACTORY_DEPTH.set(depth);
            }
        });
        Supplier<BlockPattern> replaced = Suppliers.memoize(() -> replace(id, raw.get()));
        return () -> FACTORY_DEPTH.get() == 0 ? replaced.get() : raw.get();
    }

    public static BlockPattern replace(ResourceLocation id, BlockPattern original) {
        if (id.equals(REPLACEMENTS.get(original))) return original;
        var spec = MANIFEST.getAsJsonObject(id.getPath());
        var expected = spec.getAsJsonArray("originalSize");
        var matches = ((BlockPatternAccessor) original).gtlEnhancedcore$getBlockMatches();
        if (matches.length != expected.get(2).getAsInt()
                || matches[0].length != expected.get(1).getAsInt()
                || matches[0][0].length != expected.get(0).getAsInt()) {
            throw new IllegalStateException("GTL structure changed upstream; review predicate bindings for " + id
                    + ": actual=" + matches[0][0].length + "x" + matches[0].length + "x" + matches.length
                    + ", expected=" + expected);
        }
        for (var repetition : original.aisleRepetitions) {
            if (repetition[0] != 1 || repetition[1] != 1) {
                throw new IllegalStateException("Unexpected repeatable GTL structure: " + id);
            }
        }
        // Imported schematics are already rotated to LEFT/UP/FRONT, independently
        // of the old machine's aisle axes (some originals were built vertically).
        FactoryBlockPattern builder = FactoryBlockPattern.start();
        String resource = ROOT + id.getPath() + ".pattern.gz";
        StructurePatterns.data(resource).forEachAisle(builder::aisle);
        var isolatedHatches = com.gtl.enhancedcore.common.recipe.iv.IvMachineScope.nativeTarget(id)
                ? new com.gtl.enhancedcore.common.recipe.iv.IvNativeHatches() : null;
        for (var entry : spec.getAsJsonObject("bindings").entrySet()) {
            var binding = entry.getValue().getAsJsonObject();
            String mode = binding.get("mode").getAsString();
            TraceabilityPredicate predicate;
            if (mode.equals("any")) {
                predicate = Predicates.any();
            } else if (mode.equals("heating_coils")) {
                // The original machine reads CoilType from this predicate's match context.
                // A literal coil block would form without updating its parallel limit.
                predicate = Predicates.heatingCoils();
            } else if (mode.equals("literal")) {
                predicate = literal(binding);
            } else {
                var point = binding.getAsJsonArray("sample");
                var source = matches[point.get(0).getAsInt()][point.get(1).getAsInt()][point.get(2).getAsInt()];
                predicate = switch (mode) {
                    case "original" -> source;
                    case "casing" -> new TraceabilityPredicate(casing(source, block(binding, "block")));
                    case "hatch" -> hatch(source, block(binding, "block"), block(binding, "fallback"),
                            binding.has("retainCasing") && binding.get("retainCasing").getAsBoolean());
                    default -> throw new IllegalStateException("Unknown GTL binding mode " + mode + " for " + id);
                };
                if (mode.equals("hatch") && binding.has("additionalSources")) {
                    predicate = new TraceabilityPredicate(predicate);
                    for (var extra : binding.getAsJsonArray("additionalSources")) {
                        var part = extra.getAsJsonObject();
                        var sample = part.getAsJsonArray("sample");
                        var rule = matches[sample.get(0).getAsInt()][sample.get(1).getAsInt()][sample.get(2).getAsInt()];
                        var withoutCasing = new TraceabilityPredicate(rule);
                        var solid = casing(rule, block(part, "block"));
                        withoutCasing.common.remove(solid);
                        withoutCasing.limited.remove(solid);
                        predicate = predicate.or(withoutCasing);
                    }
                }
                if (mode.equals("hatch") && binding.has("requiredParts")) {
                    predicate = new TraceabilityPredicate(predicate);
                    for (var extra : binding.getAsJsonArray("requiredParts")) {
                        var part = extra.getAsJsonObject();
                        var sample = part.getAsJsonArray("sample");
                        var rule = matches[sample.get(0).getAsInt()][sample.get(1).getAsInt()][sample.get(2).getAsInt()];
                        if (rule.common.size() != 1 || !rule.limited.isEmpty())
                            throw new IllegalStateException("Required part rule changed for " + id);
                        var simple = rule.common.getFirst();
                        // Copy the predicate, not its counters: fixed original slots become a panel quota.
                        var copy = new SimplePredicate(simple.type, simple.predicate, simple.candidates);
                        predicate = predicate.or(new TraceabilityPredicate(copy)
                                .setExactLimit(part.get("count").getAsInt())
                                .setPreviewCount(part.get("count").getAsInt()));
                    }
                }
            }
            if (mode.equals("hatch") && MegastructureMaintenancePolicy.matches(id.getNamespace(), id.getPath())) {
                predicate = new TraceabilityPredicate(predicate).or(Predicates.abilities(
                        com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE)
                        .setMaxGlobalLimited(1).setPreviewCount(1));
            }
            builder.where(entry.getKey().charAt(0), isolatedHatches == null ? predicate : isolatedHatches.restrict(predicate));
        }
        BlockPattern replacement = builder.build();
        REPLACEMENTS.put(replacement, id);
        return replacement;
    }

    private static Block block(JsonObject binding, String key) {
        ResourceLocation id = new ResourceLocation(binding.get(key).getAsString());
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            throw new IllegalStateException("Missing GTL structure block " + id);
        }
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    private static TraceabilityPredicate literal(JsonObject binding) {
        Block block = block(binding, "block");
        BlockState state = block.defaultBlockState();
        for (var entry : binding.getAsJsonObject("properties").entrySet()) {
            var property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IllegalStateException("Unknown structure property: " + binding);
            state = withProperty(state, property, entry.getValue().getAsString());
        }
        BlockState preview = state;
        // Active/redstone state may change after formation. Candidates still preserve lamp item NBT.
        return new TraceabilityPredicate(world -> world.getBlockState().is(block),
                org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates.mark(
                        () -> new BlockInfo[]{LampPlacement.info(preview)}));
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow(
                () -> new IllegalStateException("Invalid structure property " + property.getName() + "=" + value)));
    }

    private static SimplePredicate casing(TraceabilityPredicate source, Block block) {
        var candidates = Stream.concat(source.common.stream(), source.limited.stream())
                .filter(simple -> {
                    if (simple.candidates == null) return false;
                    var infos = simple.candidates.get();
                    if (infos == null || infos.length == 0) return false;
                    for (var info : infos) {
                        if (info == null || !info.getBlockState().is(block)) return false;
                    }
                    return true;
                }).distinct().toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException("GTL casing predicate changed for " + ForgeRegistries.BLOCKS.getKey(block));
        }
        return candidates.getFirst();
    }

    private static TraceabilityPredicate hatch(TraceabilityPredicate source, Block casingBlock, Block fallback,
            boolean retainCasing) {
        // Keep each SimplePredicate identity: global ability limits and minimum
        // casing counts must be shared between all markers and the solid body.
        SimplePredicate casing = casing(source, casingBlock);
        if (casingBlock == fallback) return source;
        var result = new TraceabilityPredicate(source);
        if (!retainCasing) {
            result.common.remove(casing);
            result.limited.remove(casing);
        }
        return result.or(Predicates.blocks(fallback));
    }
}
