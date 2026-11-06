package com.gtl.enhancedcore;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.common.world.plot.CampusPresetRegistry;
import com.gtl.enhancedcore.common.world.plot.FileCampus;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

/** Separate registry bootstrap JVM; never invoked by the lightweight RegressionSuite or a game client. */
public final class CampusSceneMaterialRegistryRegression {
    private CampusSceneMaterialRegistryRegression() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Forge's normal GameData.BlockCallbacks.onBake initializes every state's fluid/tick/shape cache.
        // This standalone bootstrap has no registry bake event, so reproduce that exact state lifecycle here.
        int initializedStateCaches = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                state.initCache();
                initializedStateCaches++;
            }
        }
        Method sceneResolver = PlotChunkGenerator.class.getDeclaredMethod("resolveSceneMaterial", String.class);
        Method campusResolver = PlotChunkGenerator.class.getDeclaredMethod("resolveCampusMaterial", String.class);
        sceneResolver.setAccessible(true);
        campusResolver.setAccessible(true);
        List<Map<String, Object>> materials = new ArrayList<>();
        List<Map<String, Object>> generators = new ArrayList<>();
        List<Map<String, Object>> controls = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        var biome = Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(false).temperature(.8F).downfall(0)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0xC0D8FF).waterColor(0x3F76E4)
                        .waterFogColor(0x50533).skyColor(0x78A7FF).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build());
        int definitions = 0;
        for (var definition : CampusPresetRegistry.definitions()) {
            definitions++;
            try {
                Method resolver = definition.engine().equals("file_scene") ? sceneResolver : campusResolver;
                for (String material : new TreeSet<>(FileCampus.of(definition.id()).materials())) {
                    var row = inspect(resolver, material);
                    row.put("preset", definition.id());
                    row.put("engine", definition.engine());
                    materials.add(row);
                    if (!Boolean.TRUE.equals(row.get("accepted"))) failures.add(definition.id() + ": " + row);
                }
            } catch (Throwable failure) {
                failures.add("Cannot load file campus " + definition.id() + ": " + failure);
            }
            var construction = new LinkedHashMap<String, Object>();
            construction.put("preset", definition.id());
            try {
                var settings = PlotSettings.fileCampus(definition.id());
                var generator = new PlotChunkGenerator(biome, settings);
                var paletteField = PlotChunkGenerator.class.getDeclaredField("palette");
                paletteField.setAccessible(true);
                Map<?, ?> palette = (Map<?, ?>) paletteField.get(generator);
                if (!generator.settings().equals(settings)
                        || !palette.keySet().containsAll(FileCampus.of(definition.id()).materials())
                        || palette.values().stream().anyMatch(value -> !(value instanceof BlockState)))
                    throw new AssertionError("Constructed generator lost settings or a resolved authored palette entry");
                construction.put("status", "PASS");
                construction.put("resolvedPaletteEntries", palette.size());
            } catch (Throwable failure) {
                construction.put("status", "FAIL");
                construction.put("error", failure.toString());
                failures.add("Production generator construction failed for " + definition.id() + ": " + failure);
            }
            generators.add(construction);
        }
        positive(sceneResolver, "minecraft:pink_petals[flower_amount=4,facing=north]", controls, failures);
        positive(sceneResolver, "minecraft:water[level=0]", controls, failures);
        positive(sceneResolver, "minecraft:cherry_leaves[persistent=true,distance=1]", controls, failures);
        negative(sceneResolver, "minecraft:pink_petals[amount=4,facing=north]", "Invalid authored scenery material", controls, failures);
        negative(sceneResolver, "minecraft:pink_petals[flower_amount=5]", "Invalid authored scenery material", controls, failures);
        negative(sceneResolver, "minecraft:white_concrete_powder", "Unsafe authored scenery material", controls, failures);
        negative(sceneResolver, "minecraft:sand", "Unsafe authored scenery material", controls, failures);
        negative(sceneResolver, "minecraft:chest", "Unsafe authored scenery material", controls, failures);
        negative(sceneResolver, "minecraft:lava", "Unsafe authored scenery material", controls, failures);
        negative(sceneResolver, "minecraft:water[level=1]", "Unsafe authored scenery material", controls, failures);
        if (definitions == 0 || materials.isEmpty()) failures.add("No file-campus definitions or materials were validated");
        var report = new LinkedHashMap<String, Object>();
        report.put("status", failures.isEmpty() ? "PASS" : "FAIL");
        report.put("minecraftVersion", SharedConstants.getCurrentVersion().getName());
        report.put("registeredVanillaBlocks", BuiltInRegistries.BLOCK.size());
        report.put("initializedBlockStateCaches", initializedStateCaches);
        report.put("definitions", definitions);
        report.put("materialChecks", materials.size());
        report.put("controlChecks", controls.size());
        report.put("generatorConstructions", generators.stream().filter(row -> row.get("status").equals("PASS")).count());
        report.put("generators", generators);
        report.put("materials", materials);
        report.put("controls", controls);
        report.put("failures", failures);
        report.put("clientStarted", false);
        report.put("worldOpened", false);
        report.put("boundary", "Independent JVM Minecraft Bootstrap, real local Minecraft block-state definitions and the actual compiled PlotChunkGenerator material resolvers. All FileCampus definitions and their entire material sets are checked; every file preset constructs a real PlotChunkGenerator using a minimal direct biome holder and verifies its resolved palette. No Forge mod lifecycle, GUI, chunks, client or world is started.");
        Path output = Path.of(args.length == 0 ? "build/reports/campus-scene-material-registry.json" : args[0]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("Campus material registry: definitions=" + definitions + " materials=" + materials.size()
                + " controls=" + controls.size() + " failures=" + failures.size() + " report=" + output);
        failures.forEach(System.err::println);
        if (!failures.isEmpty()) throw new AssertionError("Campus material registry validation failed: " + failures.size());
    }

    private static Map<String, Object> inspect(Method resolver, String material) throws Exception {
        var row = new LinkedHashMap<String, Object>();
        row.put("material", material);
        row.put("productionResolver", resolver.getName());
        try {
            BlockState state = (BlockState) resolver.invoke(null, material);
            row.put("accepted", true);
            row.put("resolvedState", state.toString());
            row.put("blockEntity", state.getBlock() instanceof EntityBlock);
            row.put("fallingBlock", state.getBlock() instanceof FallingBlock);
            row.put("availableProperties", state.getProperties().stream().map(property -> property.getName()).sorted().toList());
        } catch (InvocationTargetException wrapped) {
            Throwable failure = wrapped.getCause();
            row.put("accepted", false);
            row.put("error", failure.toString());
            List<String> causes = new ArrayList<>();
            for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) causes.add(cause.toString());
            row.put("causes", causes);
        }
        return row;
    }

    private static void positive(Method resolver, String material, List<Map<String, Object>> controls,
                                 List<String> failures) throws Exception {
        var row = inspect(resolver, material);
        row.put("expected", "accepted");
        controls.add(row);
        if (!Boolean.TRUE.equals(row.get("accepted"))) failures.add("Valid control was rejected: " + row);
    }

    private static void negative(Method resolver, String material, String message, List<Map<String, Object>> controls,
                                 List<String> failures) throws Exception {
        var row = inspect(resolver, material);
        row.put("expected", message);
        controls.add(row);
        if (!Boolean.FALSE.equals(row.get("accepted")) || !String.valueOf(row.get("error")).contains(message))
            failures.add("Invalid control was not rejected by the expected production guard: " + row);
    }
}
