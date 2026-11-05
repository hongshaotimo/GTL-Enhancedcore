package com.gtl.enhancedcore.common.cloud;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.utils.GTUtil;
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
                ignored -> new RecipeIndex(cloudRecipes(level, type)));
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
        final Map<GTRecipe, Family> families = new IdentityHashMap<>();
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
    private record Family(Tag body, Tag catalysts) {}

    /** A bounded scan may resolve catalyst variants, never arbitrary overlapping recipes. */
    public static final class MatchSet {
        private final RecipeIndex index;
        private int count, strictCount;
        private GTRecipe first, strict;
        private Family family;
        private boolean sameFamily = true, differentCatalysts;
        private final List<String> candidateIds = new ArrayList<>();
        public MatchSet() { this(null); }
        MatchSet(RecipeIndex index) { this.index = index; }
        public void add(GTRecipe recipe, WorkableMultiblockMachine machine, Probe probe) {
            if (!matches(recipe, machine, probe)) return;
            // Both chemical maps may contain their own copy. Preserve ambiguity
            // within the preferred map instead of counting the fallback copy twice.
            if (first != null && recipe.recipeType != first.recipeType
                    && CloudChemicalModes.equivalent(first.recipeType.registryName.toString(), recipe.recipeType.registryName.toString())) return;
            if (candidateIds.size() < 64) candidateIds.add(recipe.id.toString());
            Family next = index == null ? family(recipe) : index.families.computeIfAbsent(recipe, CloudPatternMatching::family);
            if (count++ == 0) { first = recipe; family = next; }
            else {
                sameFamily &= Objects.equals(family.body, next.body);
                differentCatalysts |= !Objects.equals(family.catalysts, next.catalysts);
            }
            if (IvRecipeInputs.patternOperations(recipe, probe.inputs, probe.virtual, probe.outputs) > 0) {
                strictCount++; strict = recipe;
            }
        }
        public GTRecipe unique() {
            if (count == 1) return first;
            // A recipe without an ordinary catalyst is not evidence that the player
            // selected it over another variant whose catalyst is merely missing.
            return sameFamily && differentCatalysts && strictCount == 1 && hasCatalyst(strict) ? strict : null;
        }
        public boolean ambiguous() { return count > 1 && unique() == null; }
        List<String> candidateIds() { return List.copyOf(candidateIds); }
        int candidateCount() { return count; }
    }
    private static boolean hasCatalyst(GTRecipe recipe) {
        return recipe.inputs.entrySet().stream().anyMatch(entry -> entry.getValue().stream().anyMatch(content ->
                content.chance == 0 && !circuit(entry.getKey(), content)));
    }
    private static boolean circuit(RecipeCapability<?> capability, Content content) {
        if (capability != ItemRecipeCapability.CAP) return false;
        ItemStack[] choices = ItemRecipeCapability.CAP.of(content.content).getItems();
        return choices.length > 0 && Arrays.stream(choices).allMatch(IntCircuitBehaviour::isIntegratedCircuit);
    }
    private static Family family(GTRecipe recipe) {
        // Preserve complete ingredients, quantities, probabilities, conditions,
        // data and non-EU tick requirements. Only catalyst definitions and their
        // intended duration/EU trade-off may distinguish a selectable variant.
        GTRecipe normalized = recipe.copy();
        normalized.data = recipe.data.copy();
        // Core fix4 derives this field in both Java and KubeJS EUt setters.
        // An explicit tier override is a condition and must remain distinct.
        // JSON recipe loading narrows small integral values to ByteTag/ShortTag;
        // compare their exact numeric value, preserving fractional/explicit overrides.
        boolean derivedTier = recipe.data.contains("euTier", Tag.TAG_ANY_NUMERIC)
                && recipe.data.getDouble("euTier") == GTUtil.getTierByVoltage(Math.abs(RecipeHelper.getInputEUt(recipe)));
        if (derivedTier) normalized.data.remove("euTier");
        normalized.duration = 1;
        normalized.inputs.replaceAll((cap, contents) -> contents.stream().filter(content ->
                content.chance != 0 || circuit(cap, content)).toList());
        normalized.inputs.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        normalized.tickInputs.getOrDefault(EURecipeCapability.CAP, List.of()).forEach(content -> content.content = 1L);
        Tag body = GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, normalized).result().orElse(null);
        // Nonstandard recipe subclasses or an incomplete codec cannot prove
        // equivalence. Their distinct identity keeps the result ambiguous.
        if (body == null || recipe.getClass() != GTRecipe.class) {
            var unknown = new CompoundTag(); unknown.putString("unprovenRecipe", recipe.id.toString()); body = unknown;
        } else if (body instanceof CompoundTag tag) {
            tag.putInt("cloudParallels", recipe.parallels); tag.putInt("cloudOcTier", recipe.ocTier);
            tag.putBoolean("cloudDerivedEuTier", derivedTier);
        }
        GTRecipe catalysts = recipe.copy();
        catalysts.inputs.replaceAll((cap, contents) -> contents.stream().filter(content ->
                content.chance == 0 && !circuit(cap, content)).toList());
        catalysts.inputs.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        Tag reusable = GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, catalysts).result().orElse(null);
        // Include only inputs in the catalyst identity; costs remain in the body
        // solely as normalized values, never a tie-breaking recipe preference.
        return new Family(body, reusable instanceof CompoundTag tag ? tag.get("inputs") : null);
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
    public static List<com.gregtechceu.gtceu.api.recipe.GTRecipeType> queryTypes(com.gregtechceu.gtceu.api.recipe.GTRecipeType type) {
        return CloudChemicalModes.queryModes(type.registryName.toString()).stream().map(mode ->
                mode.equals(type.registryName.toString()) ? type
                        : com.gregtechceu.gtceu.api.registry.GTRegistries.RECIPE_TYPES.get(new net.minecraft.resources.ResourceLocation(mode))).toList();
    }
    private static List<GTRecipe> cloudRecipes(net.minecraft.world.level.Level level, com.gregtechceu.gtceu.api.recipe.GTRecipeType type) {
        var recipes = new LinkedHashMap<net.minecraft.resources.ResourceLocation, GTRecipe>();
        for (var candidateType : queryTypes(type)) {
            recipes(level, candidateType).forEach(recipe -> recipes.putIfAbsent(recipe.id, recipe));
        }
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
    public static boolean supported(GTRecipe recipe, WorkableMultiblockMachine machine) {
        return (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine
                || !recipe.tickInputs.containsKey(EURecipeCapability.CAP))
                && recipe.ingredientActions.isEmpty() && recipe.tickOutputs.isEmpty()
                && recipe.inputs.keySet().stream().allMatch(cap -> cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP)
                && recipe.outputs.keySet().stream().allMatch(cap -> cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP)
                && recipe.tickInputs.keySet().stream().allMatch(cap -> cap == EURecipeCapability.CAP
                || (IvMachineScope.nativeTarget(machine) || IvBuffers.genericCloudTarget(machine)) && cap == CWURecipeCapability.CAP
                || (cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP)
                    && machine.getRecipeLogic() instanceof IvNativeAccess
                    && machine.getParts().stream().anyMatch(CloudPatternBufferMachine.class::isInstance));
    }
    public static boolean matches(GTRecipe recipe, WorkableMultiblockMachine machine, Probe probe) {
        return probe != null && supported(recipe, machine)
                // Allocation publishes a route even while its physical catalyst is
                // absent, while retaining the integrated-circuit selector. The
                // execution path in IvBuffers performs the strict catalyst check.
                && IvRecipeInputs.patternOperationsIgnoringReusableInputs(recipe,
                        probe.inputs, probe.virtual, probe.outputs) > 0;
    }
}
