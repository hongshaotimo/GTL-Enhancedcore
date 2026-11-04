package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreRecipeTypes;
import com.gtl.enhancedcore.common.data.machine.EquipmentRecipeLoader;
import com.gtl.enhancedcore.common.data.machine.LanthanideRefiningRecipeLoader;
import com.gtl.enhancedcore.common.machine.GTLEnhancedcoreMachines;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.config.ConfigHolder;

/** Real production recipe emission and GTCEu serialization on a registered title-screen client. */
public final class LanthanideRecipeRuntimeChecks {
    private static final ResourceLocation TYPE = new ResourceLocation("gtl_enhancedcore:lanthanide_refining");
    private static final ResourceLocation PROCESSING = new ResourceLocation("gtl_enhancedcore:lanthanide_refining/monazite");
    private static final ResourceLocation ACQUISITION = new ResourceLocation("gtl_enhancedcore:assembly_line/equipment/lanthanide_refining_hub");

    private LanthanideRecipeRuntimeChecks() {}

    public static void run(List<String> checks) throws Exception {
        var type = GTLEnhancedcoreRecipeTypes.LANTHANIDE_REFINING;
        check(GTRegistries.RECIPE_TYPES.get(TYPE) == type && BuiltInRegistries.RECIPE_TYPE.get(TYPE) == type,
                "Recipe type is present in both real registries", checks);
        check(type.getMaxInputs(ItemRecipeCapability.CAP) == 2
                        && type.getMaxInputs(FluidRecipeCapability.CAP) == 2
                        && type.getMaxOutputs(ItemRecipeCapability.CAP) == 16
                        && type.getMaxOutputs(FluidRecipeCapability.CAP) == 0,
                "Recipe UI has 2 item inputs, 2 fluid inputs and all 16 output slots", checks);
        var definition = GTLEnhancedcoreMachines.getLanthanideRefiningHub();
        check(definition != null && definition.getTier() == 6 && definition.getRecipeTypes().length == 1
                        && definition.getRecipeTypes()[0] == type,
                "Actual LuV controller uses the registered lanthanide recipe type", checks);
        check(GTLEnhancedcoreMachines.all().contains(definition), "Actual controller is included in the creative-tab source", checks);

        List<FinishedRecipe> processing = new ArrayList<>();
        LanthanideRefiningRecipeLoader.registerRecipes(processing::add);
        // Capture both production loaders before checking values so a failure leaves complete evidence.
        List<FinishedRecipe> equipment = new ArrayList<>();
        EquipmentRecipeLoader.registerRecipes(equipment::add);
        var acquisition = equipment.stream().filter(value -> value.getId().equals(ACQUISITION)).toList();
        JsonObject receipt = new JsonObject();
        receipt.addProperty("LuV_VA", GTValues.VA[GTValues.LuV]);
        receipt.addProperty("LuV_V", GTValues.V[GTValues.LuV]);
        receipt.addProperty("gtlcoreDurationMultiplier", ConfigHolder.INSTANCE.durationMultiplier);
        receipt.addProperty("processingBaseDuration", 1200);
        receipt.addProperty("processingExpectedSerializedDuration", adjustedDuration(1200));
        receipt.addProperty("acquisitionBaseDuration", 2400);
        receipt.addProperty("acquisitionExpectedSerializedDuration", adjustedDuration(2400));
        JsonObject processingJson = new JsonObject();
        processing.forEach(value -> processingJson.add(value.getId().toString(), value.serializeRecipe()));
        receipt.add("productionProcessingJson", processingJson);
        JsonObject acquisitionJson = new JsonObject();
        equipment.stream().filter(value -> value.getId().getPath().contains("lanthanide_refining_hub"))
                .forEach(value -> acquisitionJson.add(value.getId().toString(), value.serializeRecipe()));
        receipt.add("productionAcquisitionJson", acquisitionJson);
        writeReceipt(receipt);
        check(processing.size() == 1 && processing.getFirst().getId().equals(PROCESSING),
                "Production loader emits exactly " + PROCESSING + "; actual="
                        + processing.stream().map(FinishedRecipe::getId).toList(), checks);
        GTRecipe recipe = GTRecipeSerializer.SERIALIZER.fromJson(PROCESSING, processing.getFirst().serializeRecipe());
        verifyProcessing(recipe, "emitted JSON", checks);
        var encoded = GTRecipeSerializer.CODEC.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow(false,
                message -> { throw new AssertionError("Lanthanide codec encode: " + message); });
        receipt.add("processingCodecRoundTripJson", encoded);
        writeReceipt(receipt);
        GTRecipe decoded = GTRecipeSerializer.SERIALIZER.fromJson(PROCESSING, encoded.getAsJsonObject());
        verifyProcessing(decoded, "JSON codec round trip", checks);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GTRecipeSerializer.SERIALIZER.toNetwork(buffer, decoded);
            GTRecipe network = GTRecipeSerializer.SERIALIZER.fromNetwork(PROCESSING, buffer);
            receipt.add("processingNetworkRoundTripJson", GTRecipeSerializer.CODEC.encodeStart(JsonOps.INSTANCE, network)
                    .getOrThrow(false, message -> { throw new AssertionError(message); }));
            writeReceipt(receipt);
            verifyProcessing(network, "network round trip", checks);
            check(buffer.readableBytes() == 0, "Network recipe consumed its exact serialized payload", checks);
        } finally { buffer.release(); }

        check(acquisition.size() == 1, "Production equipment loader resolves all registered acquisition ingredients; expected="
                + ACQUISITION + "; actual=" + equipment.stream().map(FinishedRecipe::getId)
                .filter(id -> id.getPath().contains("lanthanide_refining_hub")).toList(), checks);
        GTRecipe assembly = GTRecipeSerializer.SERIALIZER.fromJson(ACQUISITION, acquisition.getFirst().serializeRecipe());
        var input = IvRecipeInputs.outputs(assembly.inputs);
        checkRecipe(assembly.recipeType == GTRecipeTypes.ASSEMBLY_LINE_RECIPES && assembly.duration == adjustedDuration(2400),
                "Acquisition preserves its 2400-tick base with the active GTLCore multiplier", assembly, checks);
        check(input.getOrDefault(AEItemKey.of(item("gtceu:europium_foil")), 0L) == 64
                        && input.getOrDefault(AEFluidKey.of(fluid("gtceu:europium")), 0L) == 2304,
                "Actual acquisition consumes 64 europium foil and 2304 mB molten europium", checks);
        check(IvRecipeInputs.outputs(assembly.outputs).equals(Map.of(AEItemKey.of(definition.asStack()), 1L)),
                "Actual acquisition produces exactly one registered controller", checks);
        checkRecipe(eut(assembly) == GTValues.VA[GTValues.LuV],
                "Acquisition is gated at real LuV VA=" + GTValues.VA[GTValues.LuV], assembly, checks);
    }

    private static void verifyProcessing(GTRecipe recipe, String phase, List<String> checks) {
        Map<AEKey, Long> expectedInputs = Map.of(
                AEItemKey.of(item("gtceu:monazite_dust")), 128L,
                AEItemKey.of(item("gtceu:carbon_dust")), 128L,
                AEFluidKey.of(fluid("minecraft:water")), 1000L,
                AEFluidKey.of(fluid("gtceu:sulfuric_acid")), 1000L);
        Map<AEKey, Long> expectedOutputs = new LinkedHashMap<>();
        for (String element : List.of("lanthanum", "praseodymium", "neodymium", "cerium", "europium",
                "gadolinium", "samarium", "terbium", "dysprosium", "holmium", "erbium", "thulium",
                "ytterbium", "lutetium", "scandium", "yttrium")) {
            expectedOutputs.put(AEItemKey.of(item("gtceu:" + element + "_dust")), 128L);
        }
        check(recipe.recipeType == GTLEnhancedcoreRecipeTypes.LANTHANIDE_REFINING && PROCESSING.equals(recipe.id),
                phase + " retains its real type and recipe ID", checks);
        check(recipe.getInputContents(ItemRecipeCapability.CAP).size() == 2
                        && recipe.getInputContents(FluidRecipeCapability.CAP).size() == 2
                        && IvRecipeInputs.outputs(recipe.inputs).equals(expectedInputs),
                phase + " retains both 128-dust inputs and both 1000-mB fluid inputs", checks);
        check(recipe.getOutputContents(ItemRecipeCapability.CAP).size() == 16
                        && IvRecipeInputs.outputs(recipe.outputs).equals(expectedOutputs),
                phase + " retains all 16 distinct outputs at 128 each without stack-count truncation", checks);
        check(recipe.inputs.values().stream().flatMap(List::stream)
                        .allMatch(value -> value.chance == ChanceLogic.getMaxChancedValue())
                        && recipe.outputs.values().stream().flatMap(List::stream)
                        .allMatch(value -> value.chance == ChanceLogic.getMaxChancedValue()),
                phase + " consumes every input and guarantees every output", checks);
        checkRecipe(recipe.duration == adjustedDuration(1200),
                phase + " retains 1200 base ticks with the active GTLCore multiplier", recipe, checks);
        checkRecipe(eut(recipe) == GTValues.VA[GTValues.LuV],
                phase + " consumes real LuV VA=" + GTValues.VA[GTValues.LuV], recipe, checks);
        checkRecipe(recipe.tickOutputs.values().stream().allMatch(List::isEmpty),
                phase + " has no tick output contents", recipe, checks);
    }

    /** GTLCore fix4 GTRecipeBuilderMixin.toJson applies this multiplier to ordinary consuming recipes. */
    private static int adjustedDuration(int base) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1.0, Math.abs(base * ConfigHolder.INSTANCE.durationMultiplier)));
    }

    private static String diagnostics(GTRecipe recipe) {
        return "actual duration=" + recipe.duration + ", EUt=" + eut(recipe)
                + ", GTLCore durationMultiplier=" + ConfigHolder.INSTANCE.durationMultiplier
                + ", tickInputs=" + recipe.tickInputs + ", tickOutputs=" + recipe.tickOutputs
                + ", json=" + GTRecipeSerializer.CODEC.encodeStart(JsonOps.INSTANCE, recipe)
                .getOrThrow(false, message -> { throw new AssertionError(message); });
    }

    private static void checkRecipe(boolean valid, String label, GTRecipe recipe, List<String> checks) {
        if (!valid) throw new AssertionError("lanthanide: " + label + "; " + diagnostics(recipe));
        checks.add("lanthanide: " + label);
    }

    private static void writeReceipt(JsonObject receipt) throws Exception {
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("lanthanide-recipes.json");
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(receipt) + "\n");
    }

    private static long eut(GTRecipe recipe) {
        return recipe.getTickInputContents(EURecipeCapability.CAP).stream()
                .mapToLong(value -> EURecipeCapability.CAP.of(value.content)).sum();
    }

    private static Item item(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null || item == Items.AIR) throw new AssertionError("Missing registered audit item " + id);
        return item;
    }

    private static Fluid fluid(String id) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        if (fluid == null || fluid == Fluids.EMPTY) throw new AssertionError("Missing registered audit fluid " + id);
        return fluid;
    }

    private static void check(boolean valid, String label, List<String> checks) {
        if (!valid) throw new AssertionError("lanthanide: " + label);
        checks.add("lanthanide: " + label);
    }
}
