package com.gtl.enhancedcore.common.cloud;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.recipe.iv.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;

/** Same recipe admission as IV, evaluated incrementally before publishing a lease. */
public final class CloudPatternMatching {
    private CloudPatternMatching() {}
    private static final Map<net.minecraft.server.MinecraftServer, Map<net.minecraft.resources.ResourceLocation, RecipeIndex>> INDEXES = new WeakHashMap<>();
    static RecipeIndex index(net.minecraft.world.level.Level level, com.gregtechceu.gtceu.api.recipe.GTRecipeType type) {
        var server = Objects.requireNonNull(level.getServer(), "Server recipe index required");
        return INDEXES.computeIfAbsent(server, ignored -> new HashMap<>()).computeIfAbsent(type.registryName,
                ignored -> new RecipeIndex(recipes(level, type)));
    }
    static void clear(net.minecraft.server.MinecraftServer server) { INDEXES.remove(server); }
    /** Shared candidates only; callers still validate live catalysts, inputs and controller capabilities. */
    public static List<GTRecipe> candidates(net.minecraft.world.level.Level level,
            com.gregtechceu.gtceu.api.recipe.GTRecipeType type, Map<AEKey, Long> outputs) {
        return index(level, type).candidates(outputs);
    }
    static final class RecipeIndex {
        final List<GTRecipe> all;
        final Map<AEKey, List<GTRecipe>> byOutput = new HashMap<>();
        int cursor;
        RecipeIndex(List<GTRecipe> recipes) { all = recipes; }
        boolean buildStep() {
            if (cursor >= all.size()) return false;
            var recipe = all.get(cursor++);
            try { IvRecipeInputs.outputs(recipe.outputs).keySet().forEach(key -> byOutput.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe)); }
            catch (IllegalArgumentException | ArithmeticException unsupported) { /* Unsupported capabilities fail admission too. */ }
            return true;
        }
        List<GTRecipe> candidates(CloudPatternMatching.Probe probe) { return candidates(probe.outputs()); }
        List<GTRecipe> candidates(Map<AEKey, Long> outputs) {
            // A partially built index cannot prove a recipe is absent.
            if (cursor < all.size()) return all;
            List<GTRecipe> smallest = all;
            for (AEKey output : outputs.keySet()) {
                List<GTRecipe> group = byOutput.getOrDefault(output, List.of());
                if (group.size() < smallest.size()) smallest = group;
            }
            return smallest;
        }
    }
    public static Map<AEKey, Long> virtual(CompoundTag config, ItemStack circuit) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        for (String key : List.of("configuredVirtualItems", "configuredVirtualFluids"))
            for (Tag value : config.getList(key, Tag.TAG_COMPOUND)) {
                AEKey decoded = key.endsWith("Items") ? AEItemKey.fromTag((CompoundTag)value) : AEFluidKey.fromTag((CompoundTag)value);
                if (decoded != null && !IvBuffers.isCircuit(decoded)) result.put(decoded, Long.MAX_VALUE);
            }
        if (!circuit.isEmpty()) result.put(AEItemKey.of(circuit), 1L);
        return result;
    }
    /** Includes GTCEu's converted vanilla recipes, absent from the GT-only RecipeManager list. */
    public static List<GTRecipe> recipes(net.minecraft.world.level.Level level, com.gregtechceu.gtceu.api.recipe.GTRecipeType type) {
        var recipes = new LinkedHashMap<net.minecraft.resources.ResourceLocation, GTRecipe>();
        level.getRecipeManager().getAllRecipesFor(type).forEach(recipe -> recipes.put(recipe.id, recipe));
        type.getProxyRecipes().values().forEach(proxy -> proxy.forEach(recipe -> recipes.putIfAbsent(recipe.id, recipe)));
        return List.copyOf(recipes.values());
    }
    public record Probe(Map<AEKey, Long> inputs, Map<AEKey, Long> virtual, Map<AEKey, Long> outputs) {
        public Probe {
            inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
            virtual = Collections.unmodifiableMap(new LinkedHashMap<>(virtual));
            outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        }
    }
    public static Probe probe(IPatternDetails processed, Map<AEKey, Long> virtual) {
        if (processed == null) return null;
        Map<AEKey, Long> inputs = new LinkedHashMap<>(), selectors = new LinkedHashMap<>(virtual), outputs = new LinkedHashMap<>();
        for (var input : processed.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length != 1 || input.getMultiplier() < 1) return null;
            AEKey key = possible[0].what();
            if (key instanceof AEItemKey item && GTLItems.VIRTUAL_INGREDIENT.isIn(item.toStack())) {
                var token = item.toStack(); var i = VirtualIngredientBehavior.payloadItemKey(token); var f = VirtualIngredientBehavior.payloadFluidKey(token);
                if (i != null) selectors.put(i, IvBuffers.isCircuit(i) ? 1L : Long.MAX_VALUE);
                if (f != null) selectors.put(f, Long.MAX_VALUE);
            } else inputs.merge(key, input.getMultiplier(), Math::addExact);
        }
        for (var output : processed.getOutputs()) outputs.merge(output.what(), output.amount(), Math::addExact);
        return new Probe(inputs, selectors, outputs);
    }
    public static boolean supported(GTRecipe recipe, WorkableElectricMultiblockMachine machine) {
        return recipe.ingredientActions.isEmpty() && recipe.tickOutputs.isEmpty()
                && recipe.inputs.keySet().stream().allMatch(cap -> cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP)
                && recipe.outputs.keySet().stream().allMatch(cap -> cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP)
                && recipe.tickInputs.keySet().stream().allMatch(cap -> cap == EURecipeCapability.CAP
                || (IvMachineScope.nativeTarget(machine) || IvBuffers.genericCloudTarget(machine)) && cap == CWURecipeCapability.CAP
                || (cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP)
                    && machine.getRecipeLogic() instanceof IvNativeAccess
                    && machine.getParts().stream().anyMatch(CloudPatternBufferMachine.class::isInstance));
    }
    public static boolean matches(GTRecipe recipe, WorkableElectricMultiblockMachine machine, Probe probe) {
        return probe != null && supported(recipe, machine)
                // Allocation publishes a route even while its physical catalyst is
                // absent. The execution path in IvBuffers performs the strict check.
                && IvRecipeInputs.patternOperationsIgnoringReusableInputs(recipe,
                        probe.inputs, probe.virtual, probe.outputs) > 0;
    }
}
