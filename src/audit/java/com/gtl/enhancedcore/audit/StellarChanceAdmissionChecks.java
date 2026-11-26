package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.IngredientEquality;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeEngine;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;

/** Real registered candidate/admission/payment path; only rejection predicates and inventory endpoint are injected. */
public final class StellarChanceAdmissionChecks {
    private StellarChanceAdmissionChecks() {}

    public static Map<String, Object> run() throws Exception {
        var f = StellarIvGuiChecks.fixture("gtladditions:lucid_etchdreamer", false, false, true);
        var machine = f.machine();
        var logic = machine.getRecipeLogic();
        var type = machine.getRecipeType();
        var previousLookup = type.getLookup();
        var predicateField = GTLAddMultipleRecipesLogic.class.getDeclaredField("beforeWorking");
        predicateField.setAccessible(true);
        var previousPredicate = predicateField.get(logic);
        try {
            var parts = machine.getParts().stream().filter(GalaxyConnectionHatch.class::isInstance).toList();
            field(machine, "parts").set(machine, new ArrayList<>(parts));
            field(machine, "partPositions").set(machine,
                    parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
            require(logic.getClass() == GTLAddMultipleRecipesLogic.class
                    && GalaxyIndependentRecipeEngine.supports(logic), "Registered ordinary lucid admission is supported");
            UUID owner = UUID.randomUUID();
            field(GalaxyLinks.linkedStation(machine), "userId").set(GalaxyLinks.linkedStation(machine), owner);
            WirelessEnergyManager.setUserEU(owner, BigInteger.TEN.pow(30));

            var input = new RejectingInput(machine);
            var output = new NotifiableItemStackHandler(machine, 4, IO.OUT);
            machine.getCapabilitiesProxy().clear();
            machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input));
            machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output));
            field(machine, "normalCapabilities").set(machine, new EnumMap<IO, List<RecipeHandlePart>>(Map.of(
                    IO.IN, List.of(RecipeHandlePart.of(IO.IN, List.of(input))),
                    IO.OUT, List.of(RecipeHandlePart.of(IO.OUT, List.of(output))))));
            field(machine, "sharedInputRecipeHandlePart").set(machine, RecipeHandlePart.of(IO.IN, List.of(input)));
            var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "chance_admission_probe"), type)
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(0).duration(20).buildRawRecipe();
            var sugar = LongIngredient.create(Ingredient.of(Items.SUGAR), 1L);
            var diamond = LongIngredient.create(Ingredient.of(Items.DIAMOND), 1L);
            var inputs = new ArrayList<>(recipe.inputs.get(ItemRecipeCapability.CAP));
            inputs.add(new Content(sugar, 5_000, 10_000, 0, null, null));
            recipe.inputs.put(ItemRecipeCapability.CAP, inputs);
            var outputs = new ArrayList<>(recipe.outputs.get(ItemRecipeCapability.CAP));
            outputs.add(new Content(diamond, 5_000, 10_000, 0, null, null));
            recipe.outputs.put(ItemRecipeCapability.CAP, outputs);
            IGTRecipe.of(recipe).setBatchProcessed(true);
            var lookup = new GTRecipeLookup(type);
            field(type, "lookup").set(type, lookup);
            require(lookup.addRecipe(recipe), "One real chance candidate is registered");
            input.setStackInSlot(0, new ItemStack(Items.APPLE));
            input.setStackInSlot(1, new ItemStack(Items.SUGAR));

            var cache = ItemRecipeCapability.CAP.makeChanceCache();
            cache.put(sugar, 0); cache.put(diamond, 0);
            requireFixedKey(cache, sugar); requireFixedKey(cache, diamond);
            logic.getChanceCaches().put(ItemRecipeCapability.CAP, cache);
            var engine = new GalaxyIndependentRecipeEngine(logic);
            var admit = GalaxyIndependentRecipeEngine.class.getDeclaredMethod("admit");
            admit.setAccessible(true);
            var beforeCalls = new AtomicInteger();
            predicateField.set(logic, (Predicate<IRecipeLogicMachine>) ignored -> { beforeCalls.incrementAndGet(); return false; });
            require(!(boolean) admit.invoke(engine) && beforeCalls.get() == 1 && !engine.hasJobs(),
                    "Actual beforeAdmission predicate rejects a prepared candidate");
            require(logic.getChanceCaches().get(ItemRecipeCapability.CAP) == cache
                    && cache.getInt(sugar) == 0 && cache.getInt(diamond) == 0,
                    "Rejected beforeAdmission leaves both original probability entries unchanged");

            predicateField.set(logic, (Predicate<IRecipeLogicMachine>) ignored -> true);
            input.rejectExecution = true;
            require(!(boolean) admit.invoke(engine) && input.rejections > 0 && !engine.hasJobs(),
                    "Real input execution failure rejects the rolled candidate without a job");
            require(input.getStackInSlot(0).is(Items.APPLE) && input.getStackInSlot(1).is(Items.SUGAR)
                    && logic.getChanceCaches().get(ItemRecipeCapability.CAP) == cache
                    && cache.getInt(sugar) == 0 && cache.getInt(diamond) == 0,
                    "Failed input payment restores original cache identity and exact values without consuming items");

            input.rejectExecution = false;
            boolean accepted = (boolean) admit.invoke(engine);
            String state = " accepted=" + accepted + ", jobs=" + engine.hasJobs() + ", apple=" + input.getStackInSlot(0)
                    + ", sugar=" + input.getStackInSlot(1) + ", inputCache=" + cache.getInt(sugar)
                    + ", outputCache=" + cache.getInt(diamond) + ", rejectedExecutions=" + input.rejections
                    + ", cacheEntries=" + cache;
            require(accepted && engine.hasJobs() && input.getStackInSlot(0).isEmpty()
                    && input.getStackInSlot(1).is(Items.SUGAR), "Actual acceptance pays deterministic input once;" + state);
            require(logic.getChanceCaches().get(ItemRecipeCapability.CAP) == cache
                    && cache.getInt(sugar) == 2_500 && cache.getInt(diamond) == 2_500,
                    "One accepted half-chance operation commits exactly one cache update for input and output;" + state);
            return Map.of("status", "PASS", "beforeAdmissionRejected", true, "inputExecutionRejected", true,
                    "acceptedJobs", 1, "inputCache", cache.getInt(sugar), "outputCache", cache.getInt(diamond),
                    "directSetup", directSetup(),
                    "scope", "Registered lucid controller, actual prepareIndependent/admit and Core probability/payment; injected ADD predicate and rejecting inventory endpoint. No forced machine.beforeWorking rejection, world or AE CPU.");
        } finally {
            predicateField.set(logic, previousPredicate);
            field(type, "lookup").set(type, previousLookup);
            f.holders().forEach(holder -> holder.setLevel(null));
        }
    }

    private static Map<String, Object> directSetup() throws Exception {
        var f = StellarIvGuiChecks.fixture("gtl_enhancedcore:platinum_refining_matrix", false, false, true);
        var machine = f.machine(); var logic = machine.getRecipeLogic();
        try {
            var parts = machine.getParts().stream().filter(GalaxyConnectionHatch.class::isInstance).toList();
            field(machine, "parts").set(machine, new ArrayList<>(parts));
            field(machine, "partPositions").set(machine,
                    parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
            UUID owner = UUID.randomUUID();
            field(GalaxyLinks.linkedStation(machine), "userId").set(GalaxyLinks.linkedStation(machine), owner);
            WirelessEnergyManager.setUserEU(owner, BigInteger.TEN.pow(30));
            require(GalaxyRecipeBatch.applicable(logic), "Direct setup fixture enables stellar expansion");
            var input = new RejectingInput(machine);
            var output = new NotifiableItemStackHandler(machine, 4, IO.OUT);
            machine.getCapabilitiesProxy().clear();
            machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input));
            machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output));
            field(machine, "normalCapabilities").set(machine, new EnumMap<IO, List<RecipeHandlePart>>(Map.of(
                    IO.IN, List.of(RecipeHandlePart.of(IO.IN, List.of(input))),
                    IO.OUT, List.of(RecipeHandlePart.of(IO.OUT, List.of(output))))));
            field(machine, "sharedInputRecipeHandlePart").set(machine, RecipeHandlePart.of(IO.IN, List.of(input)));
            var recipe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "direct_chance_probe"), machine.getRecipeType())
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(0).duration(20).buildRawRecipe();
            var sugar = LongIngredient.create(Ingredient.of(Items.SUGAR), 1L);
            var diamond = LongIngredient.create(Ingredient.of(Items.DIAMOND), 1L);
            var inputs = new ArrayList<>(recipe.inputs.get(ItemRecipeCapability.CAP));
            inputs.add(new Content(sugar, 5_000, 10_000, 0, null, null)); recipe.inputs.put(ItemRecipeCapability.CAP, inputs);
            var outputs = new ArrayList<>(recipe.outputs.get(ItemRecipeCapability.CAP));
            outputs.add(new Content(diamond, 5_000, 10_000, 0, null, null)); recipe.outputs.put(ItemRecipeCapability.CAP, outputs);
            input.setStackInSlot(0, new ItemStack(Items.APPLE)); input.setStackInSlot(1, new ItemStack(Items.SUGAR));
            var cache = ItemRecipeCapability.CAP.makeChanceCache(); cache.put(sugar, 0); cache.put(diamond, 0);
            requireFixedKey(cache, sugar); requireFixedKey(cache, diamond);
            logic.getChanceCaches().put(ItemRecipeCapability.CAP, cache);
            input.rejectExecution = true; logic.setupRecipe(recipe);
            require(!logic.isActive() && input.rejections > 0 && input.getStackInSlot(0).is(Items.APPLE)
                    && input.getStackInSlot(1).is(Items.SUGAR) && logic.getChanceCaches().get(ItemRecipeCapability.CAP) == cache
                    && cache.getInt(sugar) == 0 && cache.getInt(diamond) == 0,
                    "Direct expanded input execution failure preserves original input/cache");
            input.rejectExecution = false; logic.setupRecipe(recipe);
            String state = " active=" + logic.isActive() + ", recipe=" + logic.getLastRecipe()
                    + ", apple=" + input.getStackInSlot(0) + ", sugar=" + input.getStackInSlot(1)
                    + ", inputCache=" + cache.getInt(sugar) + ", outputCache=" + cache.getInt(diamond)
                    + ", cacheEntries=" + cache;
            require(logic.isActive() && logic.getLastRecipe() != null
                    && logic.getLastRecipe().data.getBoolean(GalaxyRecipeBatch.MARKER)
                    && input.getStackInSlot(0).isEmpty() && input.getStackInSlot(1).is(Items.SUGAR)
                    && logic.getChanceCaches().get(ItemRecipeCapability.CAP) == cache
                    && cache.getInt(sugar) == 2_500 && cache.getInt(diamond) == 2_500,
                    "Direct accepted expanded batch pays once and commits exactly one chance update;" + state);
            return Map.of("status", "PASS", "inputExecutionRejected", true,
                    "acceptedActive", true, "inputCache", cache.getInt(sugar), "outputCache", cache.getInt(diamond),
                    "scope", "Real registered platinum refinery direct setup/expand; injected execute-only inventory rejection. Synthetic formed membership, no full structure.");
        } finally { f.holders().forEach(holder -> holder.setLevel(null)); }
    }

    private static void requireFixedKey(Object2IntMap<Ingredient> cache, Ingredient source) {
        var copy = ItemRecipeCapability.CAP.copyInner(source);
        var strategy = IngredientEquality.IngredientHashStrategy.INSTANCE;
        String state = " sourceClass=" + source.getClass().getName() + ", copyClass=" + copy.getClass().getName()
                + ", sourceHash=" + strategy.hashCode(source) + ", copyHash=" + strategy.hashCode(copy)
                + ", forwardEqual=" + strategy.equals(source, copy) + ", reverseEqual=" + strategy.equals(copy, source)
                + ", containsCopy=" + cache.containsKey(copy);
        require(source.getClass() == LongIngredient.class && copy.getClass() == LongIngredient.class
                && strategy.hashCode(source) == strategy.hashCode(copy)
                && strategy.equals(source, copy) && strategy.equals(copy, source)
                && cache.containsKey(copy) && cache.getInt(copy) == 0,
                "Fixed probability key matches an actual capability copy under Core hash/equality;" + state);
    }

    private static final class RejectingInput extends NotifiableItemStackHandler {
        boolean rejectExecution;
        int rejections;
        RejectingInput(MetaMachine machine) { super(machine, 4, IO.IN); }
        @Override public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left,
                String slotName, boolean simulate) {
            if (io == IO.IN && !simulate && rejectExecution) { rejections++; return left; }
            return super.handleRecipeInner(io, recipe, left, slotName, simulate);
        }
    }

    private static Field field(Object owner, String name) throws Exception {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { var result = type.getDeclaredField(name); result.setAccessible(true); return result; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static void require(boolean success, String message) {
        if (!success) throw new AssertionError(message);
    }
}
