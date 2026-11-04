package com.gtl.enhancedcore.audit;

import appeng.api.stacks.*;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gtl.enhancedcore.common.cloud.CloudPatternMatching;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Actual loaded KJS recipes in a marked disposable server; no player-world source. */
@Mod("enhancedcore_audit")
public final class CloudFeedbackServerAudit {
    private boolean done;
    private int ticks;
    public CloudFeedbackServerAudit() { MinecraftForge.EVENT_BUS.addListener(this::tick); }
    private void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || ++ticks < 4
                || !Boolean.getBoolean("gtl.enhancedcore.cloudFeedbackAudit")) return;
        done = true;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        var directory = server.getServerDirectory().toPath().toAbsolutePath().normalize();
        var report = new LinkedHashMap<String, Object>(); var checks = new ArrayList<String>();
        try {
            var root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
            if (!directory.toRealPath().startsWith(root)
                    || !Files.isRegularFile(directory.resolve("cloud-feedback.marker"))
                    || !server.getWorldPath(LevelResource.ROOT).toRealPath().startsWith(root))
                throw new AssertionError("Marked disposable server required");
            check(Files.isRegularFile(directory.resolve("echo-shard-audit.json"))
                    && Files.isRegularFile(directory.resolve("kjs-runtime-audit.json")),
                    "both KJS loaded callbacks produced evidence", checks);
            var recipes = server.getRecipeManager().getRecipes().stream().filter(GTRecipe.class::isInstance)
                    .map(GTRecipe.class::cast).toList();
            var circuits = recipes.stream().filter(recipe -> recipe.recipeType.registryName.toString()
                    .equals("gtceu:circuit_assembly_line")).toList();
            check(circuits.size() >= 45, "actual circuit assembly recipe count=" + circuits.size(), checks);
            var before = circuits.stream().map(recipe -> GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, recipe)
                    .result().orElseThrow().copy()).toList();
            int robotRecipes = 0, variantRecipes = 0, uniqueRecipes = 0;
            var variantFamilies = new LinkedHashSet<List<String>>();
            var uniqueFamilies = new LinkedHashSet<List<String>>();
            for (var recipe : circuits) {
                boolean robot = recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, List.of()).stream()
                        .filter(content -> content.chance == 0).flatMap(content -> Arrays.stream(
                                ItemRecipeCapability.CAP.of(content.content).getItems()))
                        .anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                                .startsWith("precision_circuit_assembly_robot_mk"));
                if (!robot) continue;
                robotRecipes++;
                check(recipe.tickInputs.keySet().stream().allMatch(cap -> cap == EURecipeCapability.CAP),
                        recipe.id + " has ordinary EU tick requirements", checks);
                var probe = probe(recipe, true);
                var set = new CloudPatternMatching.MatchSet();
                for (var candidate : circuits) set.add(candidate, null, probe);
                if (set.unique() != recipe) report.put("matchingFailure", matchingEvidence(circuits, recipe, probe));
                check(set.unique() == recipe, recipe.id + " exact physical robot selects original EUt/duration/data", checks);
                var missingProbe = probe(recipe, false);
                var missingCandidates = circuits.stream().filter(candidate -> CloudPatternMatching.matches(candidate, null, missingProbe))
                        .map(candidate -> candidate.id.toString()).sorted().toList();
                var missing = new CloudPatternMatching.MatchSet();
                for (var candidate : circuits) missing.add(candidate, null, missingProbe);
                if (missingCandidates.size() == 1) {
                    check(missing.unique() == recipe && !missing.ambiguous(),
                            recipe.id + " sole loose candidate remains publishable before its robot arrives", checks);
                    uniqueRecipes++; uniqueFamilies.add(missingCandidates);
                } else {
                    check(missingCandidates.size() > 1 && missing.unique() == null && missing.ambiguous(),
                            recipe.id + " no robot leaves " + missingCandidates.size() + " loose candidates ambiguous", checks);
                    variantRecipes++; variantFamilies.add(missingCandidates);
                }
            }
            report.put("circuitMatching", Map.of("recipes", circuits.size(), "robotRecipes", robotRecipes,
                    "variantRecipes", variantRecipes, "uniqueRecipes", uniqueRecipes,
                    "variantFamilies", variantFamilies.size(), "uniqueFamilies", uniqueFamilies.size(),
                    "variantFamilyCandidates", variantFamilies, "uniqueRecipeCandidates", uniqueFamilies));
            check(robotRecipes >= 40, "actual robot recipes checked=" + robotRecipes, checks);
            for (int index = 0; index < circuits.size(); index++)
                check(before.get(index).equals(GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, circuits.get(index))
                        .result().orElseThrow()), circuits.get(index).id + " remains completely unchanged after classification", checks);
            var dust = BuiltInRegistries.ITEM.get(new ResourceLocation("gtceu:echo_shard_dust"));
            var echo = new CloudPatternMatching.Probe(Map.of(AEItemKey.of(dust), 4L), Map.of(),
                    Map.of(AEItemKey.of(net.minecraft.world.item.Items.ECHO_SHARD), 3L));
            var echoSet = new CloudPatternMatching.MatchSet();
            for (var recipe : recipes) if (recipe.recipeType.registryName.toString().equals("gtceu:electric_implosion_compressor"))
                echoSet.add(recipe, null, echo);
            check(echoSet.unique() != null && !echoSet.ambiguous(),
                    "loaded echo shard pattern has exactly one electric implosion route", checks);
            report.put("status", "PASS"); report.put("registeredRecipes", recipes.size());
        } catch (Throwable failure) {
            failure.printStackTrace(); report.put("status", "FAIL"); report.put("failure", failure.toString());
        }
        report.put("checks", checks);
        try { Files.writeString(directory.resolve("cloud-feedback-server.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception failure) { throw new RuntimeException(failure); }
        server.halt(false);
    }
    private static CloudPatternMatching.Probe probe(GTRecipe recipe, boolean includeCatalysts) {
        var input = new LinkedHashMap<AEKey, Long>(); var virtual = new LinkedHashMap<AEKey, Long>();
        recipe.inputs.forEach((cap, contents) -> {
            for (var content : contents) {
                boolean consume = content.chance > 0;
                if (cap == ItemRecipeCapability.CAP) {
                    Ingredient ingredient = ItemRecipeCapability.CAP.of(content.content);
                    var choices = ingredient.getItems();
                    if (choices.length == 0) throw new AssertionError("Empty ingredient in " + recipe.id);
                    boolean circuit = Arrays.stream(choices).allMatch(IntCircuitBehaviour::isIntegratedCircuit);
                    if (consume || includeCatalysts || circuit) (consume ? input : virtual).merge(
                            AEItemKey.of(choices[0]), IvRecipeInputs.itemAmount(ingredient), Math::addExact);
                } else if (cap == FluidRecipeCapability.CAP) {
                    var ingredient = FluidRecipeCapability.CAP.of(content.content);
                    var choices = ingredient.getStacks();
                    if (choices.length == 0) throw new AssertionError("Empty fluid in " + recipe.id);
                    if (consume || includeCatalysts) (consume ? input : virtual).merge(
                            IvRecipeInputs.fluidKey(choices[0]), ingredient.getAmount(), Math::addExact);
                }
            }
        });
        return new CloudPatternMatching.Probe(input, virtual, IvRecipeInputs.outputs(recipe.outputs));
    }
    private static Map<String, Object> matchingEvidence(List<GTRecipe> recipes, GTRecipe expected,
            CloudPatternMatching.Probe probe) throws Exception {
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("expected", expected.id.toString()); evidence.put("probe", probe.toString());
        var family = CloudPatternMatching.class.getDeclaredMethod("family", GTRecipe.class); family.setAccessible(true);
        var candidates = new ArrayList<Map<String, Object>>();
        for (var recipe : recipes) if (CloudPatternMatching.matches(recipe, null, probe)) {
            var row = new LinkedHashMap<String, Object>(); row.put("id", recipe.id.toString());
            row.put("class", recipe.getClass().getName()); row.put("eut", RecipeHelper.getInputEUt(recipe));
            row.put("duration", recipe.duration); row.put("data", recipe.data.toString());
            row.put("euTierTagType", recipe.data.get("euTier") == null ? null : recipe.data.get("euTier").getId());
            row.put("strictOperations", IvRecipeInputs.patternOperations(recipe, probe.inputs(), probe.virtual(), probe.outputs()));
            row.put("family", family.invoke(null, recipe).toString()); candidates.add(row);
        }
        evidence.put("looseCandidates", candidates); return evidence;
    }
    private static void check(boolean ok, String message, List<String> checks) {
        if (!ok) throw new AssertionError(message); checks.add(message);
    }
}
