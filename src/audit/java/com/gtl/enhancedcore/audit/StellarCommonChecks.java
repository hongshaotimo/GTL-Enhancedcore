package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.GTRecipeLookup;
import com.gregtechceu.gtceu.common.block.CoilBlock;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyConnectionHatch;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks;
import com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower;
import com.gtl.enhancedcore.common.recipe.GalaxyIndependentRecipeEngine;
import com.gtl.enhancedcore.common.recipe.GalaxyRecipeBatch;
import com.gtl.enhancedcore.common.recipe.GalaxyNativeRecipePreparation;
import com.gtl.enhancedcore.common.recipe.ThreadLimitedRecipeLogic;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.machine.trait.RecipeHandlePart;
import org.gtlcore.gtlcore.api.recipe.IParallelLogic;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;

/** Focused registered-machine reproduction; called by the existing audit client, never a second mod. */
public final class StellarCommonChecks {
    private static final long INPUT = 5_000_000_000L;
    private static final long FLUID_PER_INPUT = 144L;
    private StellarCommonChecks() {}

    public static List<String> run() throws Exception {
        return runWithFixture(true);
    }

    /** Separately callable ordinary / ADD representatives, without running the large-stock scenarios again. */
    public static List<String> independentRepresentatives() throws Exception {
        return runWithFixture(false);
    }

    private static List<String> runWithFixture(boolean includeMega) throws Exception {
        var checks = new ArrayList<String>();
        var previousData = WirelessEnergySavaedData.INSTANCE;
        var statistics = Class.forName("com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat")
                .getDeclaredField("server");
        statistics.setAccessible(true);
        Object previousServer = statistics.get(null);
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe)unsafeField.get(null);
        statistics.set(null, unsafe.allocateInstance(net.minecraft.client.server.IntegratedServer.class));
        WirelessEnergySavaedData.INSTANCE = new WirelessEnergySavaedData(null);
        try {
            if (includeMega) {
                megaExtractor(true, checks);
                megaExtractor(false, checks);
            }
            independentRepresentative("gtl_enhancedcore:platinum_refining_matrix",
                    com.gregtechceu.gtceu.api.machine.trait.RecipeLogic.class, checks);
            independentRepresentative("gtceu:qft", MutableRecipesLogic.class, checks);
            independentRepresentative("gtl_enhancedcore:large_furnace", ThreadLimitedRecipeLogic.class, checks);
            independentRepresentative("gtceu:super_particle_collider",
                    com.gtl.enhancedcore.common.machine.AddThreadMutableMachine.AddThreadRecipeLogic.class, checks);
        } finally {
            statistics.set(null, previousServer);
            WirelessEnergySavaedData.INSTANCE = previousData;
        }
        return checks;
    }

    private static void megaExtractor(boolean optimized, List<String> checks) throws Exception {
        String label = "mega_extractor/" + (optimized ? "optimized-ME" : "ordinary-3-slot");
        long expectedInput = optimized ? INPUT : 192;
        long expectedFluid = expectedInput * FLUID_PER_INPUT;
        var fixture = StellarIvGuiChecks.fixture("gtceu:mega_extractor", false, false, true);
        var machine = fixture.machine();
        var type = machine.getRecipeType();
        var originalLookup = type.getLookup();
        try {
            // Keep real registered controller, logic and link; remove the IV buffer installed by the GUI fixture.
            var parts = machine.getParts().stream().filter(GalaxyConnectionHatch.class::isInstance).toList();
            set(machine, "parts", new ArrayList<>(parts));
            set(machine, "partPositions", parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
            set(machine, "coilType", CoilBlock.CoilType.TRITANIUM);
            ((IBatchMachine)machine).setBatchEnabled(false);
            var logic = machine.getRecipeLogic();
            require(logic instanceof MultipleRecipesLogic && GalaxyLinks.unlimited(machine),
                    label + " uses registered MultipleRecipesLogic, non-IV parts and 64 arrays", checks);
            UUID owner = UUID.randomUUID();
            set(GalaxyLinks.linkedStation(machine), "userId", owner);
            BigInteger initial = BigInteger.TEN.pow(46).add(BigInteger.valueOf(47));
            WirelessEnergyManager.setUserEU(owner, initial);
            require(GalaxyPower.availablePower(machine).equals(initial), label + " receives exact BigInteger grid balance", checks);

            RecordingInput input = optimized ? new LongMEInput(machine) : new RecordingInput(machine, 3);
            if (!optimized) {
                for (int slot = 0; slot < 3; slot++)
                    input.setStackInSlot(slot, new ItemStack(Items.STRUCTURE_VOID, 64));
            }
            var output = new NotifiableFluidTank(machine, 1, expectedFluid + 1, IO.OUT);
            machine.getCapabilitiesProxy().clear();
            machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input));
            machine.getCapabilitiesProxy().put(IO.OUT, FluidRecipeCapability.CAP, List.of(output));
            set(machine, "normalCapabilities", new EnumMap<IO,List<RecipeHandlePart>>(Map.of(
                    IO.IN, List.of(RecipeHandlePart.of(IO.IN, List.of(input))),
                    IO.OUT, List.of(RecipeHandlePart.of(IO.OUT, List.of(output))))));
            set(machine, "sharedInputRecipeHandlePart", RecipeHandlePart.of(IO.IN, List.of(input)));
            var shared = ((org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine)machine).getSharedRecipeHandlePart();
            long sharedCount = shared.getSelfContent(ItemRecipeCapability.CAP,
                    ((ParallelMachine)machine).needConfirmMEStock()).values().longStream().sum();
            require(sharedCount == expectedInput, label + " native shared getSelfContent sees exactly " + sharedCount + " input", checks);

            // Use the real native lookup, with a private tree so no player or shared recipe is changed.
            var lookup = new GTRecipeLookup(type);
            set(type, "lookup", lookup);
            var origin = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "stellar_common_" + (optimized ? "me" : "slots")), type)
                    .inputItems(Items.STRUCTURE_VOID).outputFluids(FluidStack.create(Fluids.WATER, FLUID_PER_INPUT))
                    .EUt(GTValues.V[GTValues.MAX]).duration(4).blastFurnaceTemp(2000).buildRawRecipe();
            require(lookup.addRecipe(origin), label + " synthetic recipe enters native lookup.addRecipe", checks);
            long budget = GalaxyLinks.batchBudget(machine, (long)((ParallelMachine)machine).getMaxParallel() * 64);
            long itemParallel = IParallelLogic.getInputItemParallel(machine, origin, budget);
            long maxParallel = IParallelLogic.getMaxParallel(machine, origin, budget);
            String admission = label + " batchBudget=" + budget + ", inputItemParallel=" + itemParallel
                    + ", maxParallel=" + maxParallel + ", inventory=" + input.amount();
            checks.add(admission);
            require(input.amount() == expectedInput && itemParallel == expectedInput && maxParallel == expectedInput,
                    admission + "; simulation sees all supplied inputs", checks);
            require(WirelessEnergyManager.getUserEU(owner).equals(initial), label + " admission simulation has no debit", checks);

            logic.serverTick();
            CompoundTag saved = new CompoundTag();
            logic.saveCustomPersistedData(saved, false);
            var jobs = saved.getCompound("gtleIndependentRecipes").getList("jobs", Tag.TAG_COMPOUND);
            require(jobs.size() == 1, label + " first real serverTick admits one independent job, status=" + logic.getStatus(), checks);
            var selected = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, jobs.getCompound(0).get("recipe"))
                    .getOrThrow(false, ignored -> {});
            var segments = selected.outputs.getOrDefault(FluidRecipeCapability.CAP, List.of()).stream()
                    .map(content -> ((FluidIngredient)content.content).getAmount()).toList();
            long selectedFluid = segments.stream().mapToLong(Long::longValue).sum();
            String selectedState = label + " independentJobFluidMB=" + selectedFluid + ", buckets=" + selectedFluid / 1000
                    + ", outputSegmentsMB=" + segments + ", inputSegments=" + input.withdrawals
                    + ", inputLeft=" + input.amount() + ", nativeBatchTicks=" + selected.duration
                    + ", elapsed=" + jobs.getCompound(0).getInt("elapsed");
            checks.add(selectedState);
            require(selectedFluid == expectedFluid && (!optimized || selectedFluid > 309_000_000_000L) && input.amount() == 0,
                    selectedState + "; one selection consumes exactly " + expectedInput + " inputs", checks);
            require(output.getFluidInTank(0).isEmpty(), label + " independent admission freezes output without delivery", checks);
            BigInteger expectedPayment = new BigInteger(selected.data.getString(GalaxyPower.LEFT_TAG));
            BigInteger tickPayment = new BigInteger(selected.data.getString(GalaxyPower.EU_TAG));
            require(selected.duration == 20 && tickPayment.signum() > 0 && tickPayment.multiply(BigInteger.valueOf(selected.duration))
                            .compareTo(expectedPayment) >= 0
                            && (!optimized || expectedPayment.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0),
                    label + " native 20-tick batch payment=" + expectedPayment + ", EU/t=" + tickPayment, checks);
            require(WirelessEnergyManager.getUserEU(owner).equals(initial), label + " setup does not charge a processing tick", checks);
            int ticks = 0;
            while (output.getFluidInTank(0).getAmount() != expectedFluid && ticks < 24) {
                logic.serverTick();
                ticks++;
                long delivered = output.getFluidInTank(0).getAmount();
                require(input.amount() == 0 && (delivered == 0 || delivered == expectedFluid),
                        label + " tick=" + ticks + " conserves frozen output, deliveredMB=" + delivered, checks);
            }
            BigInteger paid = initial.subtract(WirelessEnergyManager.getUserEU(owner));
            require(output.getFluidInTank(0).getAmount() == expectedFluid && paid.equals(expectedPayment),
                    label + " deliveredMB=" + output.getFluidInTank(0).getAmount() + ", buckets=" + expectedFluid / 1000
                            + ", ticks=" + ticks + ", actualPaid=" + paid + ", expectedPaid=" + expectedPayment, checks);
            logic.serverTick();
            require(output.getFluidInTank(0).getAmount() == expectedFluid && input.amount() == 0
                            && WirelessEnergyManager.getUserEU(owner).equals(initial.subtract(expectedPayment)),
                    label + " idle tick repeats neither output nor payment", checks);
        } finally {
            set(type, "lookup", originalLookup);
            fixture.holders().forEach(holder -> holder.setLevel(null));
        }
    }

    private static void independentRepresentative(String id, Class<?> expectedClass, List<String> checks) throws Exception {
        var fixture = StellarIvGuiChecks.fixture(id, false, false, true);
        var machine = fixture.machine();
        var type = machine.getRecipeType();
        var originalLookup = type.getLookup();
        try {
            var parts = machine.getParts().stream().filter(GalaxyConnectionHatch.class::isInstance).toList();
            set(machine, "parts", new ArrayList<>(parts));
            set(machine, "partPositions", parts.stream().map(p -> p.self().getPos()).toArray(BlockPos[]::new));
            ((IBatchMachine)machine).setBatchEnabled(false);
            var logic = machine.getRecipeLogic();
            require(GalaxyIndependentRecipeEngine.supports(logic) && GalaxyLinks.unlimited(machine)
                            && logic.getClass() == expectedClass,
                    id + " real registered representative class=" + logic.getClass().getName() + ", no IV buffer", checks);
            UUID owner = UUID.randomUUID();
            set(GalaxyLinks.linkedStation(machine), "userId", owner);
            BigInteger initial = BigInteger.TEN.pow(30);
            WirelessEnergyManager.setUserEU(owner, initial);
            var input = new NotifiableItemStackHandler(machine, 4, IO.IN);
            var output = new NotifiableItemStackHandler(machine, 4, IO.OUT);
            machine.getCapabilitiesProxy().clear();
            machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input));
            machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output));
            set(machine, "normalCapabilities", new EnumMap<IO,List<RecipeHandlePart>>(Map.of(
                    IO.IN, List.of(RecipeHandlePart.of(IO.IN, List.of(input))),
                    IO.OUT, List.of(RecipeHandlePart.of(IO.OUT, List.of(output))))));
            set(machine, "sharedInputRecipeHandlePart", RecipeHandlePart.of(IO.IN, List.of(input)));
            var lookup = new GTRecipeLookup(type);
            set(type, "lookup", lookup);
            if (logic instanceof ThreadLimitedRecipeLogic limited)
                fractionalNativePreparation(machine, limited, input, checks);
            var shortOrigin = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "stellar_representative_short"), type)
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(0).duration(4)
                    .blastFurnaceTemp(2000).buildRawRecipe();
            var longOrigin = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "stellar_representative_long"), type)
                    .inputItems(Items.CARROT).outputItems(Items.IRON_NUGGET).EUt(0).duration(9)
                    .blastFurnaceTemp(3000).buildRawRecipe();
            require(lookup.addRecipe(shortOrigin) && lookup.addRecipe(longOrigin),
                    id + " two different metadata recipes registered through real lookup.addRecipe", checks);
            input.setStackInSlot(0, new ItemStack(Items.APPLE, 3));
            input.setStackInSlot(1, new ItemStack(Items.CARROT, 5));
            logic.markLastRecipeDirty(); // The registered ThreadLimited trait also enforces its idle retry policy.
            logic.serverTick();
            var admission = new CompoundTag();
            logic.saveCustomPersistedData(admission, false);
            var admittedJobs = admission.getCompound("gtleIndependentRecipes").getList("jobs", Tag.TAG_COMPOUND);
            require(admittedJobs.size() == 2 && input.getStackInSlot(0).isEmpty() && input.getStackInSlot(1).isEmpty(),
                    id + " real serverTick independently admits both recipes and exactly consumes 3+5 input; jobs="
                            + admittedJobs.size() + ", status=" + logic.getStatus(), checks);
            var metadata = new java.util.HashSet<Integer>();
            for (int index = 0; index < admittedJobs.size(); index++) {
                var recipe = decode(admittedJobs.getCompound(index));
                metadata.add(recipe.data.getInt("ebf_temp"));
            }
            require(metadata.equals(java.util.Set.of(2000, 3000)) && WirelessEnergyManager.getUserEU(owner).equals(initial),
                    id + " actual admission preserves both metadata values and defers EU to work ticks", checks);

            // Admission above is unseeded. Controlled paid durations below test completion and refill,
            // retaining the actual saved job identities and the registered native machine callbacks.
            for (int index = 0; index < admittedJobs.size(); index++) {
                var entry = admittedJobs.getCompound(index);
                boolean shortJob = entry.getString("originId").contains("_short");
                int count = shortJob ? 3 : 5;
                int duration = shortJob ? 4 : 9;
                int eut = shortJob ? 13 : 17;
                var paid = GTRecipeBuilder.of(new ResourceLocation(entry.getString("recipeId")), type)
                        .outputItems(new ItemStack(shortJob ? Items.GOLD_NUGGET : Items.IRON_NUGGET, count))
                        .EUt(0).duration(duration).blastFurnaceTemp(shortJob ? 2000 : 3000).buildRawRecipe();
                paid.data.putBoolean(GalaxyRecipeBatch.MARKER, true);
                paid.data.putLong(GalaxyRecipeBatch.OPERATIONS_TAG, count);
                paid.data.putLong(GalaxyRecipeBatch.RECIPES_TAG, 1);
                paid.data.putString(GalaxyPower.EU_TAG, Integer.toString(eut));
                paid.data.putString(GalaxyPower.LEFT_TAG, Integer.toString(eut * duration));
                IGTRecipe.of(paid).setBatchProcessed(true);
                IGTRecipe.of(paid).setHasTick(true);
                entry.put("recipe", GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, paid).getOrThrow(false, ignored -> {}));
                entry.putInt("elapsed", 0);
            }
            logic.loadCustomPersistedData(admission);
            logic.serverTick();
            require(WirelessEnergyManager.getUserEU(owner).equals(initial.subtract(BigInteger.valueOf(30))),
                    id + " two restored paid jobs debit exactly 13+17 EU on real serverTick", checks);
            logic.serverTick();
            logic.serverTick();
            input.setStackInSlot(0, new ItemStack(Items.APPLE, 2));
            logic.serverTick();
            var refill = new CompoundTag();
            logic.saveCustomPersistedData(refill, false);
            var remaining = refill.getCompound("gtleIndependentRecipes").getList("jobs", Tag.TAG_COMPOUND);
            BigInteger expectedFinalPayment = initial.subtract(WirelessEnergyManager.getUserEU(owner));
            for (int index = 0; index < remaining.size(); index++)
                expectedFinalPayment = expectedFinalPayment.add(new BigInteger(
                        decode(remaining.getCompound(index)).data.getString(GalaxyPower.LEFT_TAG)));
            require(itemCount(output, Items.GOLD_NUGGET) == 3 && itemCount(output, Items.IRON_NUGGET) == 0
                            && input.getStackInSlot(0).isEmpty() && remaining.size() == 2,
                    id + " short job delivers alone; long job continues while its free slot admits two fresh apples", checks);
            logic.loadCustomPersistedData(refill);
            for (int tick = 0; tick < 24; tick++) logic.serverTick();
            long gold = itemCount(output, Items.GOLD_NUGGET), iron = itemCount(output, Items.IRON_NUGGET);
            BigInteger actualPaid = initial.subtract(WirelessEnergyManager.getUserEU(owner));
            require(gold == 5 && iron == 5 && actualPaid.equals(expectedFinalPayment),
                    id + " saved long job and refill complete exactly once; output=" + gold + " gold+" + iron
                            + " iron, exact paid EU=" + actualPaid + ", expected=" + expectedFinalPayment, checks);
            logic.serverTick();
            require(itemCount(output, Items.GOLD_NUGGET) == 5 && itemCount(output, Items.IRON_NUGGET) == 5
                            && WirelessEnergyManager.getUserEU(owner).equals(initial.subtract(expectedFinalPayment)),
                    id + " idle tick does not duplicate either completed metadata recipe", checks);
        } finally {
            set(type, "lookup", originalLookup);
            fixture.holders().forEach(holder -> holder.setLevel(null));
        }
    }

    private static void fractionalNativePreparation(WorkableElectricMultiblockMachine machine,
            ThreadLimitedRecipeLogic logic, NotifiableItemStackHandler input, List<String> checks) throws Exception {
        var limited = (com.gtladd.gtladditions.api.machine.IGTLAddMultiRecipeMachine)machine;
        int previousDuration = limited.getLimitedDuration();
        double previousEU = logic.getReductionEUt(), previousTime = logic.getReductionDuration();
        var type = machine.getRecipeType();
        var previousLookup = type.getLookup();
        try {
            limited.setLimitedDuration(7);
            logic.setReduction(0.25, 1);
            var probe = GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "stellar_fractional_native"), type)
                    .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(1).duration(1).buildRawRecipe();
            var lookup = new GTRecipeLookup(type);
            set(type, "lookup", lookup);
            require(lookup.addRecipe(probe), "large_furnace fractional recipe enters native lookup", checks);
            input.setStackInSlot(0, new ItemStack(Items.APPLE, 5));
            var singleton = GalaxyNativeRecipePreparation.prepare(logic, probe);
            require(singleton != null && singleton.duration == limited.getLimitedDuration()
                            && singleton.duration == 7 && singleton.data.getString(GalaxyPower.LEFT_TAG).equals("0"),
                    "large_furnace singleton retains configured seven-tick duration and fractional 0.25 EU cost", checks);
            var prepared = GalaxyRecipeBatch.prepareIndependent(machine, logic, 0, java.util.Set.of(), 0, 1, BigInteger.ZERO);
            require(prepared.size() == 1 && prepared.getFirst().recipe().duration == 7
                            && prepared.getFirst().recipe().data.getLong(GalaxyRecipeBatch.OPERATIONS_TAG) == 5
                            && prepared.getFirst().recipe().data.getString(GalaxyPower.LEFT_TAG).equals("1")
                            && input.getStackInSlot(0).getCount() == 5,
                    "large_furnace simulated five-copy native admission rounds 0.25x5=1.25 to one total EU after scaling, leaves inputs untouched", checks);
        } finally {
            input.setStackInSlot(0, ItemStack.EMPTY);
            limited.setLimitedDuration(previousDuration);
            logic.setReduction(previousEU, previousTime);
            set(type, "lookup", previousLookup);
        }
    }

    private static GTRecipe decode(CompoundTag entry) {
        return GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, entry.get("recipe")).getOrThrow(false, ignored -> {});
    }

    private static long itemCount(NotifiableItemStackHandler handler, net.minecraft.world.item.Item item) {
        long count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
            if (handler.getStackInSlot(slot).is(item)) count += handler.getStackInSlot(slot).getCount();
        return count;
    }

    /** Ordinary GT handler with observation only; all ingredient withdrawal remains native. */
    private static class RecordingInput extends NotifiableItemStackHandler {
        final List<Long> withdrawals = new ArrayList<>();
        RecordingInput(WorkableElectricMultiblockMachine machine, int slots) { super(machine, slots, IO.IN); }
        long amount() {
            long total = 0;
            for (int slot = 0; slot < getSlots(); slot++) total += getStackInSlot(slot).getCount();
            return total;
        }
        @Override public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> ingredients,
                String slotName, boolean simulate) {
            long before = amount();
            var remaining = super.handleRecipeInner(io, recipe, ingredients, slotName, simulate);
            if (!simulate && before != amount()) withdrawals.add(before - amount());
            return remaining;
        }
    }

    /** Narrow ME stock seam: real optimized long-count API, predicate match and exact atomic debit. */
    private static final class LongMEInput extends RecordingInput implements IOptimizedMEList {
        private final ItemStack key = new ItemStack(Items.STRUCTURE_VOID);
        private long stock = INPUT;
        LongMEInput(WorkableElectricMultiblockMachine machine) { super(machine, 1); }
        @Override long amount() { return stock; }
        @Override public boolean isEmpty() { return stock == 0; }
        @Override public double getTotalContentAmount() { return stock; }
        @Override public List<Object> getContents() { return stock == 0 ? List.of() : List.of(key); }
        @Override public Object2LongMap<ItemStack> getMEItemMap() { return getItemMap(); }
        @Override public Object2LongOpenHashMap<ItemStack> getItemMap() {
            var result = new Object2LongOpenHashMap<ItemStack>();
            if (stock > 0) result.put(key, stock);
            return result;
        }
        @Override public boolean isStocking() { return true; }
        @Override public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> ingredients,
                String slotName, boolean simulate) {
            if (io != IO.IN) return ingredients;
            long requested = 0;
            for (var ingredient : ingredients) {
                if (!ingredient.test(key)) return ingredients;
                long count = ingredient instanceof LongIngredient wide ? wide.getActualAmount()
                        : ingredient instanceof SizedIngredient sized ? sized.getAmount() : 1;
                requested = Math.addExact(requested, count);
            }
            if (requested > stock) return ingredients;
            if (!simulate && requested > 0) {
                stock -= requested;
                withdrawals.add(requested);
                onContentsChanged();
            }
            return null;
        }
        @Override public Object createSnapshot() { return stock; }
        @Override public void restoreFromSnapshot(Object snapshot) { stock = (Long)snapshot; }
    }

    private static void require(boolean valid, String detail, List<String> checks) {
        if (!valid) throw new AssertionError(detail);
        checks.add(detail);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
