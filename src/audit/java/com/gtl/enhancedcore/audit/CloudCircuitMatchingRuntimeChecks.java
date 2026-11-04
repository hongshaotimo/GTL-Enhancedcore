package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.cloud.CloudPatternMatching;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** Transformed production builders/admission on registered machines, without a saved player world. */
final class CloudCircuitMatchingRuntimeChecks {
    private CloudCircuitMatchingRuntimeChecks() {}

    static void run(List<String> checks) throws Exception {
        nativeSubtypeTransforms(checks);
        boolean circuitAwarePublication = Boolean.getBoolean("gtl.enhancedcore.cloudCircuitAudit");
        for (String id : List.of("gtceu:super_blast_smelter", "gtceu:electric_blast_furnace",
                "gtceu:circuit_assembly_line", "gtceu:pyrolyse_oven")) {
            var fixture = StellarIvGuiChecks.fixture(id, true, false, false);
            try {
                var machine = fixture.machine();
                var type = machine.getDefinition().getRecipeTypes()[0];
                List<GTRecipe> recipes = new ArrayList<>();
                for (int circuit : List.of(0, 1, 2, 32)) recipes.add(type.recipeBuilder("cloud_circuit_" + circuit)
                        .inputItems(Items.IRON_INGOT, 2).notConsumable(Items.APPLE)
                        .notConsumableFluid(com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient.of(
                                FluidStack.create(Fluids.WATER, 1000)))
                        .circuitMeta(circuit).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe());
                var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT);
                var original = PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[]{new GenericStack(iron, 2)}, new GenericStack[]{new GenericStack(gold, 1)});
                var decoded = PatternDetailsHelper.decodePattern(original, machine.getLevel());
                for (int circuit : List.of(0, 1, 2, 32)) {
                    var circuitStack = IntCircuitBehaviour.stack(circuit);
                    var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{
                            new GenericStack(iron, 2), new GenericStack(AEItemKey.of(circuitStack), 1)},
                            new GenericStack[]{new GenericStack(gold, 1)});
                    var buffer = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase)
                            fixture.holders().get(1).getMetaMachine();
                    var extracted = new net.minecraft.world.item.ItemStack[]{net.minecraft.world.item.ItemStack.EMPTY};
                    var processed = buffer.getRealPatternHelper().processPatternWithCircuit(encoded,
                            number -> extracted[0] = IntCircuitBehaviour.stack(number), machine.getLevel(), false);
                    check(!extracted[0].isEmpty() && IntCircuitBehaviour.getCircuitConfiguration(extracted[0]) == circuit,
                            id + " upstream pattern processing extracts exact circuit " + circuit, checks);
                    Map<AEKey, Long> selectors = CloudPatternMatching.virtual(new net.minecraft.nbt.CompoundTag(), extracted[0]);
                    var probe = CloudPatternMatching.probe(processed, selectors);
                    check(probe != null && probe.inputs().equals(Map.of(iron, 2L)),
                            id + " circuit remains a selector instead of consumed pattern stock " + circuit, checks);
                    var matches = recipes.stream().filter(recipe -> CloudPatternMatching.matches(recipe, machine, probe)).toList();
                    check(matches.size() == (circuitAwarePublication ? 1 : recipes.size()),
                            id + " circuit " + circuit + " allocation candidate count=" + matches.size(), checks);
                    Map<AEKey, Long> supplied = new LinkedHashMap<>(selectors);
                    supplied.put(AEItemKey.of(Items.APPLE), 1L); supplied.put(AEFluidKey.of(Fluids.WATER), 1000L);
                    var strictMatches = recipes.stream().filter(recipe ->
                            IvRecipeInputs.patternOperations(recipe, probe.inputs(), supplied, probe.outputs()) > 0).toList();
                    check(strictMatches.size() == 1 && strictMatches.getFirst().id.getPath().endsWith("_" + circuit),
                            id + " strict admission retains the physical circuit selector " + circuit, checks);
                    var selected = strictMatches.getFirst();
                    check(matches.stream().anyMatch(recipe -> recipe.id.equals(selected.id)),
                            id + " circuit-specific recipe remains in the lease candidate set " + circuit, checks);
                    check(IvRecipeInputs.patternOperations(selected, probe.inputs(), selectors, probe.outputs()) == 0,
                            id + " missing physical item/fluid catalysts still block execution " + circuit, checks);
                    long strict = IvRecipeInputs.patternOperations(selected, probe.inputs(), supplied, probe.outputs());
                    check(strict == 1, id + " physical catalysts permit strict execution " + circuit + " actual=" + strict
                            + " inputs=" + selected.inputs + " stock=" + supplied + " physical=" + probe.inputs(), checks);
                    check(IvRecipeInputs.patternOperationsIgnoringReusableInputs(selected, Map.of(iron, 8L), selectors, probe.outputs()) == 4,
                            id + " circuit is reusable across four operations " + circuit, checks);
                }
                var noCircuit = CloudPatternMatching.probe(decoded, Map.of());
                check(recipes.stream().filter(recipe -> CloudPatternMatching.matches(recipe, machine, noCircuit)).count()
                                == (circuitAwarePublication ? 0 : recipes.size()),
                        id + " absent circuit cannot publish a circuit recipe", checks);
                var incorrect = CloudPatternMatching.probe(decoded, Map.of(AEItemKey.of(IntCircuitBehaviour.stack(3)), 1L));
                check(recipes.stream().filter(recipe -> CloudPatternMatching.matches(recipe, machine, incorrect)).count()
                                == (circuitAwarePublication ? 0 : recipes.size()),
                        id + " different circuit cannot publish a circuit recipe", checks);
                var plain = type.recipeBuilder("cloud_without_circuit").inputItems(Items.IRON_INGOT, 2)
                        .notConsumable(Items.APPLE).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe();
                check(CloudPatternMatching.matches(plain, machine, noCircuit), id + " ordinary catalyst-only pattern remains publishable", checks);
                var inputFree = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "cloud_input_free"), type)
                        .notConsumable(Items.APPLE).circuitMeta(1).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe();
                check(IvRecipeInputs.patternOperationsIgnoringReusableInputs(inputFree, Map.of(),
                                Map.of(AEItemKey.of(IntCircuitBehaviour.stack(1)), 1L), Map.of(gold, 7L)) == 7,
                        id + " input-free circuit pattern retains finite output sizing", checks);
                var wrapped = type.recipeBuilder("cloud_wrapped_circuit").inputItems(Items.IRON_INGOT, 2)
                        .notConsumable(com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient.create(
                                com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient.circuitInput(2), 1))
                        .outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe();
                boolean wrongCircuit = CloudPatternMatching.matches(wrapped, machine, CloudPatternMatching.probe(decoded,
                        Map.of(AEItemKey.of(IntCircuitBehaviour.stack(1)), 1L)));
                boolean correctCircuit = CloudPatternMatching.matches(wrapped, machine, CloudPatternMatching.probe(decoded,
                        Map.of(AEItemKey.of(IntCircuitBehaviour.stack(2)), 1L)));
                check(circuitAwarePublication ? !wrongCircuit && correctCircuit : wrongCircuit && correctCircuit,
                        id + " sized circuit selector " + (circuitAwarePublication ? "matches exactly" : "is omitted")
                                + " wrong=" + wrongCircuit + " correct=" + correctCircuit, checks);
                check(recipes.stream().allMatch(recipe -> recipe.inputs.values().stream().mapToInt(List::size).sum() == 4),
                        id + " classification never mutates shared recipe inputs", checks);
            } finally { fixture.holders().forEach(holder -> holder.setLevel(null)); }
        }
        catalystVariants(checks);
    }
    private static void nativeSubtypeTransforms(List<String> checks) throws Exception {
        for (String name : List.of(
                "org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic",
                "com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic",
                "com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.BiologicalSimulationLaboratory$BiologicalSimulationLaboratoryLogic",
                "com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph$Companion$ArcanicAstrographRecipeLogic")) {
            var type = Class.forName(name, false, CloudCircuitMatchingRuntimeChecks.class.getClassLoader());
            check(com.gtl.enhancedcore.common.recipe.iv.IvNativeAccess.class.isAssignableFrom(type),
                    name + " inherits transformed native execution access", checks);
            var methods = Arrays.stream(type.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
            check(methods.stream().anyMatch(method -> method.contains("iv$yieldBeforeNextNativeBatch")),
                    name + " transforms its own next-batch lookup guard", checks);
            check(methods.stream().anyMatch(method -> method.contains("iv$nativeOutputAccepted")),
                    name + " transforms its own original-output completion marker", checks);
        }
    }
    private static void catalystVariants(List<String> checks) throws Exception {
        var fixture = StellarIvGuiChecks.fixture("gtceu:circuit_assembly_line", true, false, false);
        try {
            var machine = fixture.machine(); var type = machine.getDefinition().getRecipeTypes()[0];
            var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT);
            var circuit = AEItemKey.of(IntCircuitBehaviour.stack(2));
            var catalysts = List.of(Items.APPLE, Items.DIAMOND, Items.EMERALD, Items.NETHER_STAR, Items.HEART_OF_THE_SEA);
            List<GTRecipe> variants = new ArrayList<>();
            for (int i = 0; i < catalysts.size(); i++) variants.add(type.recipeBuilder("cloud_catalyst_variant_" + i)
                    .inputItems(Items.IRON_INGOT, 2).notConsumable(catalysts.get(i)).circuitMeta(2)
                    .outputItems(Items.GOLD_INGOT).duration(20 * (i + 1)).EUt(32L << i).buildRawRecipe());
            check(variants.stream().map(recipe -> recipe.data.getInt("euTier")).distinct().count() > 1,
                    "real Core EUt setters contribute different derived euTier data to catalyst variants", checks);
            var jsonVariants = variants.stream().map(recipe -> {
                var copy = recipe.copy();
                copy.data = net.minecraft.nbt.CompoundTag.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                        net.minecraft.nbt.CompoundTag.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,
                                recipe.data).result().orElseThrow()).result().orElseThrow();
                return copy;
            }).toList();
            check(jsonVariants.stream().allMatch(recipe -> recipe.data.contains("euTier", net.minecraft.nbt.Tag.TAG_BYTE)),
                    "actual recipe JSON data codec narrows derived euTier values to ByteTag", checks);
            var originalEu = variants.getFirst().getTickInputContents(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP).getFirst();
            var copiedEu = variants.getFirst().copy().getTickInputContents(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP).getFirst();
            check(originalEu != copiedEu && originalEu.content.equals(copiedEu.content),
                    "transformed GTRecipe.copy owns a separate EU Content before family normalization", checks);
            var before = variants.stream().map(recipe -> com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, recipe).result().orElseThrow().copy()).toList();
            var noCatalyst = new CloudPatternMatching.Probe(Map.of(iron, 2L), Map.of(circuit, 1L), Map.of(gold, 1L));
            check(select(variants, machine, noCatalyst).ambiguous(), "five catalyst variants remain ambiguous while no variant is selected", checks);
            for (int i = 0; i < catalysts.size(); i++) {
                var probe = new CloudPatternMatching.Probe(Map.of(iron, 2L),
                        Map.of(circuit, 1L, AEItemKey.of(catalysts.get(i)), 1L), Map.of(gold, 1L));
                var selected = select(variants, machine, probe);
                check(selected.unique() == variants.get(i), "physical catalyst selects exact variant " + i + " including its own EUt/duration", checks);
                var reversed = new ArrayList<>(variants); Collections.reverse(reversed);
                check(select(reversed, machine, probe).unique() == variants.get(i), "catalyst selection is independent of candidate order " + i, checks);
                check(select(jsonVariants, machine, probe).unique() == jsonVariants.get(i),
                        "JSON-decoded derived EU tier retains exact catalyst selection " + i, checks);
                check(IvRecipeInputs.patternOperations(variants.get(i), probe.inputs(), probe.virtual(), probe.outputs()) == 1,
                        "selected catalyst variant remains eligible for strict dispatch " + i, checks);
            }
            var apple = new CloudPatternMatching.Probe(Map.of(iron, 2L), Map.of(circuit, 1L, AEItemKey.of(Items.APPLE), 1L), Map.of(gold, 1L));
            var two = new CloudPatternMatching.Probe(Map.of(iron, 2L), Map.of(circuit, 1L,
                    AEItemKey.of(Items.APPLE), 1L, AEItemKey.of(Items.DIAMOND), 1L), Map.of(gold, 1L));
            check(select(variants, machine, two).ambiguous(), "two available catalysts still reject ambiguous costs", checks);
            check(select(List.of(variants.getFirst()), machine, noCatalyst).unique() == variants.getFirst(),
                    "one recipe can publish before its physical catalyst arrives", checks);
            var changedInput = type.recipeBuilder("cloud_catalyst_different_input").inputItems(Items.IRON_INGOT, 1)
                    .notConsumable(Items.DIAMOND).circuitMeta(2).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe();
            var changedOutput = type.recipeBuilder("cloud_catalyst_different_output").inputItems(Items.IRON_INGOT, 2)
                    .notConsumable(Items.DIAMOND).circuitMeta(2).outputItems(Items.GOLD_INGOT, 2).duration(20).EUt(32).buildRawRecipe();
            var changedCondition = type.recipeBuilder("cloud_catalyst_different_condition").inputItems(Items.IRON_INGOT, 2)
                    .notConsumable(Items.DIAMOND).circuitMeta(2).outputItems(Items.GOLD_INGOT)
                    .dimension(new ResourceLocation("minecraft", "the_nether")).duration(20).EUt(32).buildRawRecipe();
            var changedChance = variants.get(1).copy(); changedChance.id = new ResourceLocation("gtl_enhancedcore", "cloud_catalyst_different_chance");
            changedChance.outputs.values().forEach(contents -> contents.forEach(content -> content.chance = content.maxChance / 2));
            var changedData = variants.get(1).copy(); changedData.id = new ResourceLocation("gtl_enhancedcore", "cloud_catalyst_different_data");
            changedData.data = changedData.data.copy(); changedData.data.putInt("ebf_temp", 1800);
            var changedTier = variants.get(1).copy(); changedTier.id = new ResourceLocation("gtl_enhancedcore", "cloud_catalyst_explicit_tier");
            changedTier.data = changedTier.data.copy(); changedTier.data.putInt("euTier", 12);
            var fractionalTier = variants.get(1).copy(); fractionalTier.id = new ResourceLocation("gtl_enhancedcore", "cloud_catalyst_fractional_tier");
            fractionalTier.data = fractionalTier.data.copy();
            fractionalTier.data.putDouble("euTier", variants.get(1).data.getInt("euTier") + 0.5);
            var changedFusion = variants.get(1).copy(); changedFusion.id = new ResourceLocation("gtl_enhancedcore", "cloud_catalyst_fusion_start");
            changedFusion.data = changedFusion.data.copy(); changedFusion.data.putLong("eu_to_start", 160_000_000L);
            for (var different : List.of(changedInput, changedOutput, changedCondition, changedChance, changedData, changedTier, fractionalTier, changedFusion))
                check(select(List.of(variants.getFirst(), different), machine, apple).ambiguous(),
                        "catalysts cannot hide distinct recipe semantics " + different.id, checks);
            var duplicate = variants.getFirst().copy(); duplicate.id = new ResourceLocation("gtl_enhancedcore", "cloud_catalyst_duplicate");
            check(select(List.of(variants.getFirst(), duplicate), machine, apple).ambiguous(),
                    "identical recipe copies under separate IDs remain ambiguous until registration is corrected", checks);
            var noReusable = type.recipeBuilder("cloud_catalyst_without_reusable").inputItems(Items.IRON_INGOT, 2)
                    .circuitMeta(2).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe();
            check(select(List.of(noReusable, variants.getFirst()), machine, noCatalyst).ambiguous(),
                    "an absent catalyst cannot silently select the recipe without a catalyst", checks);
            var wrongCircuit = new CloudPatternMatching.Probe(Map.of(iron, 2L),
                    Map.of(AEItemKey.of(IntCircuitBehaviour.stack(1)), 1L, AEItemKey.of(Items.APPLE), 1L), Map.of(gold, 1L));
            var absent = select(variants, machine, wrongCircuit);
            check(absent.unique() == null && !absent.ambiguous(), "ordinary catalysts do not override the configured circuit", checks);
            var otherCircuit = type.recipeBuilder("cloud_catalyst_different_circuit").inputItems(Items.IRON_INGOT, 2)
                    .notConsumable(Items.DIAMOND).circuitMeta(1).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe();
            var twoCircuits = new CloudPatternMatching.Probe(Map.of(iron, 2L), Map.of(circuit, 1L,
                    AEItemKey.of(IntCircuitBehaviour.stack(1)), 1L, AEItemKey.of(Items.APPLE), 1L), Map.of(gold, 1L));
            check(select(List.of(variants.getFirst(), otherCircuit), machine, twoCircuits).ambiguous(),
                    "family comparison retains exact circuit definitions even when two circuit selectors are supplied", checks);
            var fluidVariants = List.of(Fluids.WATER, Fluids.LAVA).stream().map(fluid -> type.recipeBuilder("cloud_fluid_catalyst_"
                            + net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid).getPath())
                    .inputItems(Items.IRON_INGOT, 2).notConsumableFluid(com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient.of(
                            FluidStack.create(fluid, 1000))).circuitMeta(2).outputItems(Items.GOLD_INGOT).duration(20).EUt(32).buildRawRecipe()).toList();
            var water = new CloudPatternMatching.Probe(Map.of(iron, 2L),
                    Map.of(circuit, 1L, AEFluidKey.of(Fluids.WATER), 1000L), Map.of(gold, 1L));
            check(select(fluidVariants, machine, water).unique() == fluidVariants.getFirst(),
                    "physical fluid catalyst selects its own variant", checks);
            var lowWater = new CloudPatternMatching.Probe(Map.of(iron, 2L),
                    Map.of(circuit, 1L, AEFluidKey.of(Fluids.WATER), 999L), Map.of(gold, 1L));
            check(select(fluidVariants, machine, lowWater).ambiguous(), "insufficient fluid catalyst cannot select a recipe", checks);
            for (int i = 0; i < variants.size(); i++) check(before.get(i).equals(com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer.CODEC
                    .encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, variants.get(i)).result().orElseThrow()),
                    "family comparison preserves complete registered recipe " + i, checks);
        } finally { fixture.holders().forEach(holder -> holder.setLevel(null)); }
    }
    private static CloudPatternMatching.MatchSet select(List<GTRecipe> recipes,
            com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine machine, CloudPatternMatching.Probe probe) {
        var result = new CloudPatternMatching.MatchSet(); recipes.forEach(recipe -> result.add(recipe, machine, probe)); return result;
    }
    private static void check(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError(label); checks.add("circuit: " + label);
    }
}
