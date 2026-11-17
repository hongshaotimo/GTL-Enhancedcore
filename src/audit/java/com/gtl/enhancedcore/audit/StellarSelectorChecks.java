package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEItemKey;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.gtlcore.gtlcore.api.machine.trait.MEPatternRecipeHandlePart;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.integration.ae2.handler.SlotCacheManager;

/** Registered-runtime probe; detached real buffer slots, no world or production inventory edits. */
public final class StellarSelectorChecks {
    private StellarSelectorChecks() {}

    public record Result(int simulatedSlot, int paidSlot, long firstCopper, long secondCopper,
                         List<String> frozenOutputs) {}

    /** Call on a registered runtime thread with an existing holder; does not attach the buffer. */
    public static Result probe(IMachineBlockEntity holder) throws Exception {
        var buffer = new MEPatternBufferPartMachine(holder, 2, IO.IN);
        Object[] slots = (Object[]) field(buffer, "internalInventory");
        ((boolean[]) field(buffer, "hasPatternArray"))[0] = true;
        ((boolean[]) field(buffer, "hasPatternArray"))[1] = true;
        ((int[]) field(buffer, "patternCircuitConfigs"))[0] = 1;
        ((int[]) field(buffer, "patternCircuitConfigs"))[1] = 2;
        ((SlotCacheManager) field(slots[0], "cacheManager")).setCircuitCache(1);
        ((SlotCacheManager) field(slots[1], "cacheManager")).setCircuitCache(2);
        var firstItems = itemInventory(slots[0]);
        var secondItems = itemInventory(slots[1]);
        var copperStack = ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Copper);
        var copper = AEItemKey.of(copperStack);
        firstItems.put(copper, 8);
        secondItems.put(copper, 8);
        GTRecipe recipe = GTRecipeTypes.WIREMILL_RECIPES.recipeBuilder("stellar_selector_audit")
                // Title-screen registries exist before world recipe tags are synchronized.
                // Use the registered copper item, so this probes slot selectors rather than tags.
                .circuitMeta(2).inputItems(copperStack)
                .outputItems(TagPrefix.wireGtDouble, GTMaterials.Copper)
                .duration(20).EUt(8).buildRawRecipe();
        if (recipe.inputs.get(ItemRecipeCapability.CAP).stream().filter(content -> content.chance > 0)
                .anyMatch(content -> !ItemRecipeCapability.CAP.of(content.content).test(copperStack)))
            throw new AssertionError("Selector fixture copper ingredient does not match its real slot inventory");
        var part = MEPatternRecipeHandlePart.of(buffer);
        int simulated = part.handleRecipe(recipe, contents(recipe, true), true, false);
        int paid = part.handleRecipe(recipe, contents(recipe, false), false, false);
        var outputs = recipe.outputs.get(ItemRecipeCapability.CAP).stream()
                .flatMap(content -> java.util.Arrays.stream(ItemRecipeCapability.CAP.of(content.content).getItems()))
                .map(stack -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString())
                .toList();
        return new Result(simulated, paid, firstItems.getLong(copper), secondItems.getLong(copper), outputs);
    }

    /** Use after printing the Result so a pre-fix run retains the observed wrong-slot deduction. */
    public static void verify(Result result) {
        if (result.simulatedSlot() != 1 || result.paidSlot() != 1
                || result.firstCopper() != 8 || result.secondCopper() != 7
                || !result.frozenOutputs().equals(List.of("gtceu:copper_double_wire")))
            throw new AssertionError("Stellar selector payment changed source slot: " + result);
    }

    private static Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> contents(GTRecipe recipe,
                                                                                         boolean simulate) {
        var result = new Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>>();
        var items = new ArrayList<Object>();
        for (var content : recipe.inputs.get(ItemRecipeCapability.CAP))
            if (simulate || content.chance > 0) items.add(content.content);
        result.put(ItemRecipeCapability.CAP, items);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object2LongMap<AEItemKey> itemInventory(Object slot) throws Exception {
        return (Object2LongMap<AEItemKey>) field(slot, "itemInventory");
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
}
