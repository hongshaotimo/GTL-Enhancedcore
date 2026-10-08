package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.server.MinecraftServer;

public final class UpgradeProgressionChecks {
    private static final java.util.List<com.gtl.enhancedcore.common.machine.TieredParallelMachine> IV = new ArrayList<>();
    private UpgradeProgressionChecks() {}

    public static void placeMaintenance(MinecraftServer server) {
        var definitions = java.util.List.of(
                com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getPlasmaMachineTool(),
                com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getHadronRefinery(),
                com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getQuantumMassArray(),
                com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines.getFusionAssembler());
        var world = server.overworld();
        var hatch = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(
                new net.minecraft.resources.ResourceLocation("gtceu:auto_configuration_maintenance_hatch"));
        int index = 0;
        for (var definition : definitions) {
            var shape = definition.getMatchingShapes().getFirst().getBlocks();
            for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
                for (int z = 0; z < shape[x][y].length; z++) {
                    var info = shape[x][y][z];
                    if (info == null || info.getBlockState().isAir()) continue;
                    var state = info.getBlockState();
                    if (com.gregtechceu.gtceu.api.machine.multiblock.PartAbility.MAINTENANCE.isApplicable(state.getBlock()))
                        state = hatch.defaultBlockState();
                    var pos = new net.minecraft.core.BlockPos(index * 48 + x, 80 + y, z);
                    world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                    world.setBlock(pos, state, 2 | 16);
                    if (state.getBlock() == definition.get())
                        IV.add((com.gtl.enhancedcore.common.machine.TieredParallelMachine)
                                com.gregtechceu.gtceu.api.machine.MetaMachine.getMachine(world, pos));
                }
            index++;
        }
        require(IV.size() == 4, "Missing IV maintenance controller");
    }

    public static boolean maintenanceReady() {
        return IV.size() == 4 && IV.stream().allMatch(machine -> machine.isFormed()
                && ((org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine)machine).getMaintenanceMachine() != null);
    }

    public static void checkAllMaintenance() {
        IV.forEach(UpgradeProgressionChecks::maintenance);
        com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[STRUCTURE_UPGRADE] MAINTENANCE full_structures=4 multiplier_cases=24");
    }

    public static void maintenance(com.gtl.enhancedcore.common.machine.TieredParallelMachine machine) {
        require(machine.isFormed(), "IV maintenance fixture not formed");
        var hatch = ((org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine)machine).getMaintenanceMachine();
        require(hatch instanceof com.gtl.enhancedcore.mixin.gtlcore.AutoConfigMaintenanceHatchAccessor,
                "IV maintenance hatch not bound");
        var configurable = (com.gtl.enhancedcore.mixin.gtlcore.AutoConfigMaintenanceHatchAccessor)hatch;
        var logic = machine.getRecipeLogic();
        var access = (com.gtl.enhancedcore.mixin.gtlcore.IvNativeMultipleAccessor)logic;
        double base = logic.getReductionEUt() * logic.getReductionDuration();
        float original = configurable.gtlEnhancedcore$getDurationMultiplierRaw();
        try {
            for (float value : new float[]{0.2f, 0.4f, 0.8f, 1, 1.5f, 2}) {
                configurable.gtlEnhancedcore$setDurationMultiplierRaw(value);
                require(Math.abs(hatch.getDurationMultiplier() - value) < 0.00001, "Fixture hatch multiplier");
                require(Math.abs(access.iv$euMultiplier() - base * Math.max(1, value)) < 0.00001,
                        machine.getDefinition().getId() + " maintenance discount/slowdown at " + value);
            }
        } finally {
            configurable.gtlEnhancedcore$setDurationMultiplierRaw(original);
        }
    }

    public static void check(MinecraftServer server) throws Exception {
        var baseline = com.google.gson.JsonParser.parseString(Files.readString(
                Path.of("kubejs/upgrade-20260927-baseline-recipes.json"))).getAsJsonObject();
        var report = snapshot(server);
        Files.writeString(Path.of("kubejs/upgrade-20260927-recipes.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        var normalized = report.deepCopy();
        for (String name : java.util.List.of("light_hunter_space_station", "nexus_satellite_factory_mk1",
                "nexus_satellite_factory_mk2", "nexus_satellite_factory_mk3", "nexus_satellite_factory_mk4")) {
            var matches = new ArrayList<GTRecipe>();
            for (var candidate : server.getRecipeManager().getRecipes()) {
                if (!(candidate instanceof GTRecipe recipe)) continue;
                if (IvRecipeInputs.outputs(recipe.outputs).keySet().stream()
                        .anyMatch(key -> key.getId().toString().equals("gtladditions:" + name))) matches.add(recipe);
            }
            require(matches.size() == 1, name + " acquisition alternatives=" + matches.size());
            var recipe = matches.getFirst();
            require(recipe.recipeType == GTRecipeTypes.ASSEMBLY_LINE_RECIPES, name + " assembly type");
            require(RecipeHelper.getInputEUt(recipe) == GTValues.VA[GTValues.UXV], name + " original voltage");
            require(IvRecipeInputs.outputs(recipe.outputs).values().stream().mapToLong(Long::longValue).sum() == 1,
                    name + " output amount");
            if (!name.equals("light_hunter_space_station")) continue;
            var stock = IvRecipeInputs.outputs(recipe.inputs);
            var hypercube = appeng.api.stacks.AEItemKey.of(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new net.minecraft.resources.ResourceLocation("kubejs:hypercube")));
            require(stock.getOrDefault(hypercube, 0L) == 1, "Station requires exactly one hypercube");
            var items = normalized.getAsJsonObject(recipe.id.toString()).getAsJsonObject("inputs").getAsJsonArray("item");
            var oldItems = baseline.getAsJsonObject(recipe.id.toString()).getAsJsonObject("inputs").getAsJsonArray("item");
            require(items.size() == oldItems.size() + 1, "Station ingredient count");
            require(hasPair(items.get(items.size() - 1), "item", "kubejs:hypercube"), "Hypercube was not appended");
            items.remove(items.size() - 1);
            int replaced = 0;
            for (int i = 0; i < items.size(); i++) {
                if (!hasPair(items.get(i), "tag", "gtceu:circuits/max")) continue;
                require(hasPair(oldItems.get(i), "tag", "gtceu:circuits/uxv"), "Wrong circuit slot");
                replaceTag(items.get(i), "gtceu:circuits/max", "gtceu:circuits/uxv");
                replaced++;
            }
            require(replaced == 1, "Station MAX circuit entry count=" + replaced);
        }
        require(normalized.equals(baseline), "Recipe baseline changed beyond station hypercube/MAX circuits");
        com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info(
                "[STRUCTURE_UPGRADE] COST_ONLY baseline_recipes={} hypercube=1 circuits=MAX original_count_preserved",
                baseline.size());
    }

    public static void captureBaseline(MinecraftServer server) throws Exception {
        Files.writeString(Path.of("kubejs/upgrade-20260927-baseline-recipes.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(snapshot(server)));
    }

    private static com.google.gson.JsonObject snapshot(MinecraftServer server) {
        var result = new com.google.gson.JsonObject();
        for (var candidate : server.getRecipeManager().getRecipes()) {
            if (!(candidate instanceof GTRecipe recipe)) continue;
            boolean selected = recipe.recipeType == GTRecipeTypes.RESEARCH_STATION_RECIPES
                    || recipe.recipeType == com.gtladd.gtladditions.common.recipe.GTLAddRecipesTypes.INTER_STELLAR;
            if (!selected) selected = IvRecipeInputs.outputs(recipe.outputs).keySet().stream().anyMatch(key -> {
                String id = key.getId().toString();
                return id.equals("gtladditions:light_hunter_space_station")
                        || id.startsWith("gtladditions:nexus_satellite_factory_mk");
            });
            if (selected) result.add(recipe.id.toString(),
                    GTRecipeSerializer.CODEC.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow(false, ignored -> {}));
        }
        return result;
    }

    private static boolean hasPair(com.google.gson.JsonElement json, String key, String value) {
        if (json.isJsonObject()) return json.getAsJsonObject().entrySet().stream().anyMatch(entry ->
                entry.getKey().equals(key) && entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().equals(value)
                        || hasPair(entry.getValue(), key, value));
        if (json.isJsonArray()) for (var child : json.getAsJsonArray()) if (hasPair(child, key, value)) return true;
        return false;
    }

    private static void replaceTag(com.google.gson.JsonElement json, String from, String to) {
        if (json.isJsonObject()) json.getAsJsonObject().entrySet().forEach(entry -> {
            if (entry.getKey().equals("tag") && entry.getValue().isJsonPrimitive() && entry.getValue().getAsString().equals(from))
                entry.setValue(new com.google.gson.JsonPrimitive(to));
            else replaceTag(entry.getValue(), from, to);
        });
        if (json.isJsonArray()) json.getAsJsonArray().forEach(child -> replaceTag(child, from, to));
    }

    private static void require(boolean ok, String reason) {
        if (!ok) throw new IllegalStateException(reason);
    }
}
