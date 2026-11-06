package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gtl.enhancedcore.common.recipe.HarmonyAddRecipeLogic;
import com.gtl.enhancedcore.common.recipe.HarmonyAddRecipes;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
import com.gtl.enhancedcore.common.util.MachineTooltips;
import com.gtladd.gtladditions.common.machine.multiblock.controller.ArcanicAstrograph;
import com.hepdd.gtmthings.api.misc.GlobalVariableStorage;
import com.hepdd.gtmthings.api.misc.WirelessEnergyManager;
import com.hepdd.gtmthings.data.WirelessEnergySavaedData;
import com.hepdd.gtmthings.utils.TeamUtil;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.HarmonyMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;

/** Initialized real controller factories, transformed logic, native tips and merge ownership. */
public final class HarmonyAddRuntimeChecks {
    private HarmonyAddRuntimeChecks() {}

    public static void run(Path directory, List<String> checks) throws Exception {
        var rows = new ArrayList<Map<String, Object>>();
        for (String id : List.of("gtceu:eye_of_harmony", "gtladditions:arcanic_astrograph")) {
            try (var fixture = new MachineRuntimeFixture(id, false)) {
                require(fixture.machine instanceof HarmonyMachine, id + " retains native Harmony inheritance", checks);
                var machine = (HarmonyMachine) fixture.machine;
                var logic = machine.getRecipeLogic();
                if (machine instanceof ArcanicAstrograph astrograph) {
                    require(astrograph.getRecipeLogic() == logic && logic.getClass().getName().endsWith("ArcanicAstrographRecipeLogic"),
                            id + " retains its final native covariant logic getter and compression trait", checks);
                    require(astrograph.getParallelAmount() == 2048 && astrograph.getAstralArrayCompression() != null,
                            id + " retains its original 2048 parallel baseline and real compression trait", checks);
                } else {
                    require(logic instanceof HarmonyAddRecipeLogic, id + " actual registered factory selects ADD cosmos adapter", checks);
                }
                require(!IvMachineScope.nativeTarget(machine) && !IvMachineScope.addThreadOnly(machine.getDefinition().getId())
                                && !IvMachineScope.crossRecipeEnabled(machine),
                        id + " remains outside IV isolation with ordinary physical ports", checks);
                var tips = new ArrayList<Component>();
                machine.getDefinition().getTooltipBuilder().accept(machine.getDefinition().asStack(), tips);
                require(tips.stream().anyMatch(line -> MachineTooltips.containsKey(line,
                                "tooltip.gtl_enhancedcore.harmony_add.cross_recipe")),
                        id + " real registered tooltip builder includes the requested default cross-recipe capability", checks);
                var display = new ArrayList<Component>();
                machine.addDisplayText(display);
                require(display.stream().anyMatch(line -> MachineTooltips.containsKey(line,
                                "gtl_enhancedcore.gui.harmony_add_threads"))
                                && display.stream().anyMatch(line -> MachineTooltips.containsKey(line,
                                "gtl_enhancedcore.gui.harmony_add_active")),
                        id + " real native GUI dispatch reaches the shared thread and active-recipe display", checks);
                rows.add(Map.of("id", id, "machine", machine.getClass().getName(), "logic", logic.getClass().getName(),
                        "tips", tips.stream().map(Component.Serializer::toJson).toList(),
                        "display", display.stream().map(Component.Serializer::toJson).toList()));
            }
            paidCosmosBatch(id, rows, checks);
        }
        var first = GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.recipeBuilder("audit_harmony_merge_first")
                .inputItems(Items.APPLE).outputItems(Items.DIAMOND).duration(20).buildRawRecipe();
        var second = GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.recipeBuilder("audit_harmony_merge_second")
                .inputItems(Items.CARROT).outputItems(Items.EMERALD).duration(20).buildRawRecipe();
        String beforeFirst = first.data.toString();
        var merge = HarmonyAddRecipes.class.getDeclaredMethod("merge", List.class); merge.setAccessible(true);
        var batch = (com.gregtechceu.gtceu.api.recipe.GTRecipe) merge.invoke(null, List.of(first, second));
        batch.data.putInt("auditOwnership", 1);
        require(first.data.toString().equals(beforeFirst) && !first.inputs.isEmpty() && !second.inputs.isEmpty()
                        && first.outputs.values().stream().mapToInt(List::size).sum() == 1
                        && second.outputs.values().stream().mapToInt(List::size).sum() == 1
                        && batch.inputs.isEmpty() && batch.outputs.values().stream().mapToInt(List::size).sum() == 2,
                "Actual GTRecipe merge preserves both source recipes and owns its output-only map and NBT", checks);
        Files.writeString(directory.resolve("harmony-add-runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "status", "PASS", "rows", rows, "checks", checks.stream().filter(line -> line.startsWith("harmony_add:")).toList(),
                "scope", "Actual registered controller factories, transformed logic, native tips/GUI, merge ownership, and two synthetic cosmos recipes admitted through the production lookup/ADD allocator with real physical inventories, wireless startup and hydrogen/helium payment; native ticks retain a blocked paid batch and deliver both outputs once after release.",
                "notCovered", List.of("Giant structure formation", "Non-certain probability distribution", "Paid-batch JVM restart", "Native hidden compression production"))));
    }

    private static void paidCosmosBatch(String id, List<Map<String, Object>> rows, List<String> checks) throws Exception {
        try (var statistics = new StatisticsContext()) {
            paidCosmosBatchWithContext(id, rows, checks);
        }
    }

    private static void paidCosmosBatchWithContext(String id, List<Map<String, Object>> rows, List<String> checks) throws Exception {
        var lookup = GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.getLookup();
        var branch = lookup.getLookup();
        var savedNodes = new HashMap<>(branch.getNodes());
        var savedSpecialNodes = new HashMap<>(branch.getSpecialNodes());
        var savedEnergyData = WirelessEnergySavaedData.INSTANCE;
        boolean wasDirty = savedEnergyData != null && savedEnergyData.isDirty();
        UUID user = UUID.randomUUID(), team = TeamUtil.getTeamUUID(user);
        boolean hadBalance = GlobalVariableStorage.GlobalEnergy.containsKey(team);
        BigInteger savedBalance = GlobalVariableStorage.GlobalEnergy.get(team);
        HarmonyMachine audited = null;
        try (var fixture = new MachineRuntimeFixture(id, false)) {
            var machine = (HarmonyMachine) fixture.machine; audited = machine;
            var logic = machine.getRecipeLogic();
            var input = (ItemBusPartMachine) fixture.place("gtceu:lv_input_bus", 3).getMetaMachine();
            var output = (ItemBusPartMachine) fixture.place("gtceu:lv_output_bus", 4).getMetaMachine();
            fixture.parts(List.of(input, output));
            machine.getCapabilitiesProxy().clear();
            machine.getCapabilitiesProxy().put(IO.IN, ItemRecipeCapability.CAP, List.of(input.getInventory()));
            machine.getCapabilitiesProxy().put(IO.OUT, ItemRecipeCapability.CAP, List.of(output.getInventory()));
            ((IRecipeCapabilityMachine) machine).upDate();
            input.getInventory().setStackInSlot(0, new ItemStack(Items.APPLE));
            input.getInventory().setStackInSlot(1, new ItemStack(Items.CARROT));
            lookup.removeAllRecipes();
            var first = GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.recipeBuilder("audit_harmony_paid_apple")
                    .inputItems(Items.APPLE).outputItems(Items.DIAMOND).duration(20).buildRawRecipe();
            var second = GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.recipeBuilder("audit_harmony_paid_carrot")
                    .inputItems(Items.CARROT).outputItems(Items.EMERALD).duration(20).buildRawRecipe();
            require(lookup.addRecipe(first) && lookup.addRecipe(second), id + " installs two distinct synthetic cosmos recipes into the real lookup", checks);
            field(machine, "userid").set(machine, user);
            field(machine, "oc").setInt(machine, 4);
            long gasBefore = 3_072_000_000L, gasCost = 2_048_000_000L;
            field(machine, "hydrogen").setLong(machine, gasBefore);
            field(machine, "helium").setLong(machine, gasBefore);
            var costGetter = HarmonyMachine.class.getDeclaredMethod("getStartupEnergy"); costGetter.setAccessible(true);
            BigInteger perRecipe = BigInteger.valueOf((long) costGetter.invoke(machine));
            BigInteger supply = perRecipe.multiply(BigInteger.TWO);
            if (WirelessEnergySavaedData.INSTANCE == null) WirelessEnergySavaedData.INSTANCE = new WirelessEnergySavaedData(null);
            WirelessEnergyManager.setUserEU(user, supply);
            logic.markLastRecipeDirty(); logic.setWorkingEnabled(true);
            var eligible = new ArrayList<String>();
            var iterator = lookup.getRecipeIterator(machine, recipe -> org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipe(machine, recipe)
                    && recipe.checkConditions(logic).isSuccess());
            ((org.gtlcore.gtlcore.api.recipe.IAdditionalRecipeIterator) iterator).setUseDiveIngredientTreeFind(true);
            while (iterator.hasNext()) {
                var candidate = iterator.next();
                if (candidate != null) eligible.add(candidate.id.toString());
            }
            logic.findAndHandleRecipe();
            var paid = logic.getLastRecipe();
            var details = new java.util.LinkedHashMap<String, Object>();
            details.put("id", id); details.put("candidates", eligible); details.put("status", logic.getStatus().name());
            details.put("recipe", paid == null ? "null" : paid.id.toString());
            details.put("count", paid == null ? 0 : paid.data.getInt(HarmonyAddRecipes.RECIPE_COUNT));
            details.put("duration", logic.getDuration()); details.put("apples", count(input.getInventory(), Items.APPLE));
            details.put("carrots", count(input.getInventory(), Items.CARROT)); details.put("eu", WirelessEnergyManager.getUserEU(user).toString());
            details.put("hydrogen", field(machine, "hydrogen").getLong(machine)); details.put("helium", field(machine, "helium").getLong(machine));
            details.put("recipeStatus", String.valueOf(((org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus) logic).getRecipeStatus()));
            System.out.println("HARMONY_ADMISSION_DETAILS " + new GsonBuilder().create().toJson(details));
            require(logic.isWorking() && paid != null && paid.data.getInt(HarmonyAddRecipes.RECIPE_COUNT) == 2
                            && count(input.getInventory(), Items.APPLE) == 0 && count(input.getInventory(), Items.CARROT) == 0,
                    id + " real lookup and ADD allocator admit both recipes together and consume each physical input once " + details, checks);
            require(WirelessEnergyManager.getUserEU(user).equals(BigInteger.ZERO)
                            && field(machine, "hydrogen").getLong(machine) == gasBefore - gasCost
                            && field(machine, "helium").getLong(machine) == gasBefore - gasCost,
                    id + " actual startup path pays exactly two wireless costs and two hydrogen/helium startup amounts", checks);
            int duration = logic.getDuration();
            require(duration == 300 && count(output.getInventory(), Items.DIAMOND) == 0
                            && count(output.getInventory(), Items.EMERALD) == 0,
                    id + " original OC4 Harmony duration is retained and no output is delivered at admission", checks);
            for (int slot = 0; slot < output.getInventory().getSlots(); slot++)
                output.getInventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
            for (int tick = 0; tick < duration + 20; tick++) fixture.tick();
            require(logic.getLastRecipe() == paid && !logic.isIdle()
                            && WirelessEnergyManager.getUserEU(user).signum() == 0
                            && field(machine, "hydrogen").getLong(machine) == gasBefore - gasCost
                            && field(machine, "helium").getLong(machine) == gasBefore - gasCost
                            && count(output.getInventory(), Items.DIAMOND) == 0 && count(output.getInventory(), Items.EMERALD) == 0,
                    id + " native ticks retain the exact paid batch while output is full without new input, gas or EU payment", checks);
            for (int slot = 0; slot < output.getInventory().getSlots(); slot++) output.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
            for (int tick = 0; tick < duration + 5 && (count(output.getInventory(), Items.DIAMOND) != 1
                    || count(output.getInventory(), Items.EMERALD) != 1); tick++) fixture.tick();
            for (int tick = 0; tick < 5; tick++) fixture.tick();
            require(count(output.getInventory(), Items.DIAMOND) == 1 && count(output.getInventory(), Items.EMERALD) == 1
                            && count(input.getInventory(), Items.APPLE) == 0 && count(input.getInventory(), Items.CARROT) == 0
                            && WirelessEnergyManager.getUserEU(user).signum() == 0
                            && field(machine, "hydrogen").getLong(machine) == gasBefore - gasCost
                            && field(machine, "helium").getLong(machine) == gasBefore - gasCost,
                    id + " native completion delivers each settled output exactly once after unblock and never repays startup", checks);
            rows.add(Map.of("id", id, "productionCase", "two-physical-cosmos-recipes-with-paid-output-block",
                    "recipes", 2, "duration", duration, "wirelessPaid", supply.toString(), "hydrogenPaid", gasCost,
                    "heliumPaid", gasCost, "diamonds", count(output.getInventory(), Items.DIAMOND),
                    "emeralds", count(output.getInventory(), Items.EMERALD)));
        } finally {
            lookup.removeAllRecipes();
            branch.getNodes().putAll(savedNodes); branch.getSpecialNodes().putAll(savedSpecialNodes);
            if (hadBalance) GlobalVariableStorage.GlobalEnergy.put(team, savedBalance);
            else GlobalVariableStorage.GlobalEnergy.remove(team);
            HarmonyMachine completed = audited;
            if (completed != null) WirelessEnergyManager.MachineData.keySet().removeIf(key -> key.getSecond() == completed);
            WirelessEnergySavaedData.INSTANCE = savedEnergyData;
            if (savedEnergyData != null) savedEnergyData.setDirty(wasDirty);
        }
        require(branch.getNodes().equals(savedNodes) && branch.getSpecialNodes().equals(savedSpecialNodes)
                        && GlobalVariableStorage.GlobalEnergy.containsKey(team) == hadBalance,
                id + " audit restores both original lookup roots and the temporary wireless account", checks);
    }

    private static long count(NotifiableItemStackHandler handler, Item item) {
        long count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++)
            if (handler.getStackInSlot(slot).is(item)) count += handler.getStackInSlot(slot).getCount();
        return count;
    }

    private static Field field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    /** GTMAdvancedHatch's real payment Mixin records EnergyStat and requires a server tick clock. */
    private static final class StatisticsContext implements AutoCloseable {
        private final Field serverField;
        private final Field energyStatsField;
        private final Map<Object, Object> previousEnergyStats;
        private final Field convertedDataField;
        private final Object previousConvertedData;
        private final Object previousServer;
        private final WirelessEnergySavaedData previousData;

        private StatisticsContext() throws Exception {
            var minecraft = Minecraft.getInstance();
            if (minecraft.level != null || minecraft.getSingleplayerServer() != null)
                throw new IllegalStateException("Harmony payment fixture requires title screen without a live world/server");
            serverField = Class.forName("com.xingmot.gtmadvancedhatch.integration.gtmt.newmonitor.EnergyStat")
                    .getDeclaredField("server");
            serverField.setAccessible(true);
            energyStatsField = serverField.getDeclaringClass().getDeclaredField("GlobalEnergyStat");
            energyStatsField.setAccessible(true);
            previousEnergyStats = new HashMap<>((Map<Object, Object>) energyStatsField.get(null));
            convertedDataField = Class.forName("com.xingmot.gtmadvancedhatch.api.util.MachineDataStorage")
                    .getDeclaredField("convertedData");
            convertedDataField.setAccessible(true);
            Object converted = convertedDataField.get(null);
            previousConvertedData = converted == null ? null : new HashMap<>((Map<?, ?>) converted);
            previousServer = serverField.get(null);
            previousData = WirelessEnergySavaedData.INSTANCE;
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            MinecraftServer clock = (MinecraftServer) unsafe.allocateInstance(IntegratedServer.class);
            if (clock.getTickCount() != 0) throw new AssertionError("Detached Harmony statistics clock must start at tick zero");
            serverField.set(null, clock);
            WirelessEnergySavaedData.INSTANCE = new WirelessEnergySavaedData(null);
        }

        @Override public void close() throws IllegalAccessException {
            serverField.set(null, previousServer);
            Map<Object, Object> energyStats = (Map<Object, Object>) energyStatsField.get(null);
            energyStats.clear(); energyStats.putAll(previousEnergyStats);
            if (previousConvertedData == null) convertedDataField.set(null, null);
            else {
                Map<Object, Object> converted = (Map<Object, Object>) convertedDataField.get(null);
                converted.clear(); converted.putAll((Map<?, ?>) previousConvertedData);
            }
            WirelessEnergySavaedData.INSTANCE = previousData;
        }
    }

    private static void require(boolean valid, String message, List<String> checks) {
        if (!valid) throw new AssertionError("harmony_add:" + message);
        checks.add("harmony_add:" + message);
    }
}
