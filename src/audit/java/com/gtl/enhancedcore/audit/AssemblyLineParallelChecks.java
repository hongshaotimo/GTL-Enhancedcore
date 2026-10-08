package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.logic.OCParams;
import com.gregtechceu.gtceu.api.recipe.logic.OCResult;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.common.recipe.AssemblyLineParallelDisplay;
import com.gtl.enhancedcore.integration.jade.AssemblyLineParallelProvider;
import com.gtl.enhancedcore.mixin.gtceu.RecipeModifierListAccessor;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.IAdvancedOCResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.api.ITooltip;

/** Uses the real assembly-line structure, real hatches, transformed modifier chain and recipe logic. */
public final class AssemblyLineParallelChecks {
    private static WorkableElectricMultiblockMachine machine;
    private static final List<ItemBusPartMachine> inputs = new ArrayList<>(), outputs = new ArrayList<>();
    private static int checks;

    public static String run(ServerLevel world, boolean baseline) {
        var shape = GTMachines.ASSEMBLY_LINE.getMatchingShapes().getFirst().getBlocks();
        for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
            for (int z = 0; z < shape[x][y].length; z++) {
                var info = shape[x][y][z];
                if (info == null || info.getBlockState().isAir()) continue;
                var pos = new BlockPos(128 + x, 100 + y, 128 + z);
                world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                world.setBlock(pos, info.getBlockState(), 2 | 16);
                var part = MetaMachine.getMachine(world, pos);
                if (part instanceof ItemBusPartMachine bus) {
                    // The original assembly line requires one-slot input buses. Keep that contract.
                    if (bus.getInventory().getHandlerIO() == IO.IN) inputs.add(bus);
                    else {
                        world.setBlock(pos, GTMachines.ITEM_EXPORT_BUS[GTValues.UV].getBlock()
                                .withPropertiesOf(info.getBlockState()), 2 | 16);
                        outputs.add((ItemBusPartMachine) MetaMachine.getMachine(world, pos));
                    }
                } else if (part instanceof EnergyHatchPartMachine) {
                    world.setBlock(pos, GTMachines.ENERGY_INPUT_HATCH[GTValues.UV].getBlock()
                            .withPropertiesOf(info.getBlockState()), 2 | 16);
                } else if (part instanceof WorkableElectricMultiblockMachine controller
                        && controller.getDefinition().getId().toString().equals("gtceu:assembly_line")) machine = controller;
            }
        check(machine != null && !inputs.isEmpty(), "Missing real assembly-line controller/input parts");
        // The generated preview may choose input buses for every input-or-output alternative.
        // Select an output position by the unmodified real pattern rather than inventing a fixture pattern.
        if (outputs.isEmpty()) {
            var positions = inputs.stream().map(MetaMachine::getPos).toList();
            for (var pos : positions) {
                var original = world.getBlockState(pos);
                world.setBlock(pos, GTMachines.ITEM_EXPORT_BUS[GTValues.UV].getBlock().withPropertiesOf(original), 2 | 16);
                if (machine.checkPattern()) {
                    outputs.add((ItemBusPartMachine) MetaMachine.getMachine(world, pos));
                    break;
                }
                world.setBlock(pos, original, 2 | 16);
            }
            inputs.clear();
            for (var pos : positions) {
                var bus = (ItemBusPartMachine) MetaMachine.getMachine(world, pos);
                if (bus.getInventory().getHandlerIO() == IO.IN) inputs.add(bus);
            }
        }
        check(!outputs.isEmpty(), "Original pattern accepted no output candidate: " + machine.getMultiblockState().error);
        var lock = machine.getPatternLock();
        lock.lock();
        try {
            check(machine.checkPattern(), "Original assembly-line preview did not form: " + machine.getMultiblockState().error);
            machine.onStructureFormed();
        } finally { lock.unlock(); }
        machine.setWorkingEnabled(false);
        ((IBatchMachine) machine).setBatchEnabled(true);
        machine.getEnergyContainer().addEnergy(machine.getEnergyContainer().getEnergyCapacity());
        fill(128);
        var raw = recipe(8, 1_000_000);
        var full = modify(raw);
        if (baseline) return "BASELINE actual=" + parallel(full) + " expected=64 ocVoltage=" + machine.getOverclockVoltage()
                + " eut=" + (full == null ? -1 : RecipeHelper.getInputEUt(full));
        // Reconstruct the shipped 3.1.2 ordering from the same registered upstream modifiers.
        var elements = ((RecipeModifierListAccessor) (RecipeModifierList) machine.getDefinition().getRecipeModifier())
                .gtlEnhancedcore$getModifiers();
        var oldOrder = Arrays.copyOfRange(elements, 1, elements.length);
        oldOrder[oldOrder.length - 1] = (m, r, p, o) -> {
            o.init(o.getEut(), o.getDuration(), 64, 0L, ((IAdvancedOCResult) (Object) o).getBaseOCLevel());
            IGTRecipe.of(r).setBatchProcessed(true);
            return r;
        };
        var oldResult = new RecipeModifierList(oldOrder).apply(machine, raw.copy(), new OCParams(), new OCResult());
        check(parallel(oldResult) > 0 && parallel(oldResult) < 64,
                "Old ordering did not reproduce the voltage-starved parallel bug: " + parallel(oldResult));
        check(parallel(full) == 64, "Full-supply long recipe parallel=" + parallel(full));
        check(RecipeHelper.getInputEUt(full) <= machine.getOverclockVoltage(), "Parallel exceeds power budget");
        check(parallel(raw) == 1 && raw.duration == 1_000_000, "Shared raw recipe was mutated");
        check(count(inputs) == 128 && count(outputs) == 0, "Modifier simulation consumed/delivered items");
        fill(7);
        check(parallel(modify(raw)) == 7, "Limited input did not reduce actual parallel to 7");
        fill(128);
        check(parallel(modify(recipe(machine.getOverclockVoltage() / 4, 80))) == 4, "Power limit did not reduce parallel to 4");
        check(parallel(modify(recipe(8, 1))) == 64, "Sub-tick/batch processing exceeded or reduced fixed cap");
        for (var bus : outputs) for (int slot = 0; slot < bus.getInventory().getSlots(); slot++)
            bus.getInventory().setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
        ((IRecipeCapabilityMachine) machine).upDate();
        check(!RecipeRunnerHelper.matchRecipe(machine, raw), "Full output still matched a recipe");
        check(count(inputs) == 128, "Blocked output consumed input");
        clear(outputs);
        fill(0);
        ((IRecipeCapabilityMachine) machine).upDate();
        check(!RecipeRunnerHelper.matchRecipe(machine, raw), "Empty input matched a recipe");
        display(world, 0);
        fill(64);
        machine.setWorkingEnabled(true);
        var cycle = recipe(8, 128);
        ((IRecipeCapabilityMachine) machine).upDate();
        check(RecipeRunnerHelper.matchRecipe(machine, cycle), "Cycle did not match");
        check(machine.getRecipeLogic().checkMatchedRecipeAvailable(cycle), "Cycle was rejected");
        check(count(inputs) == 0, "Cycle did not consume exactly 64 inputs");
        display(world, 64);
        for (int tick = 0; tick < 256 && count(outputs) == 0; tick++) {
            machine.getEnergyContainer().addEnergy(machine.getEnergyContainer().getEnergyCapacity());
            machine.getRecipeLogic().serverTick();
        }
        check(count(outputs) == 64 && count(inputs) == 0, "Cycle output/input conservation failed");
        machine.getRecipeLogic().resetRecipeLogic();
        display(world, 0);
        return "checks=" + checks + " original_structure=true old_order=" + parallel(oldResult)
                + " fixed=64,7,4 subtick=64 paid_input=64 output=64 gui_and_jade=0,64,0";
    }

    private static GTRecipe recipe(long eut, int duration) {
        return GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore", "audit_assembly_parallel"), machine.getRecipeType())
                .inputItems(Items.APPLE).outputItems(Items.GOLD_NUGGET).EUt(eut).duration(duration).buildRawRecipe();
    }

    private static GTRecipe modify(GTRecipe raw) {
        ((IRecipeCapabilityMachine) machine).upDate();
        RecipeRunnerHelper.matchRecipe(machine, raw);
        return machine.fullModifyRecipe(raw.copy(), new OCParams(), new OCResult());
    }

    private static long parallel(GTRecipe recipe) { return recipe == null ? -1 : IGTRecipe.of(recipe).getRealParallels(); }
    private static void clear(List<ItemBusPartMachine> buses) {
        for (var bus : buses) for (int slot = 0; slot < bus.getInventory().getSlots(); slot++)
            bus.getInventory().setStackInSlot(slot, ItemStack.EMPTY);
    }
    private static void fill(int amount) {
        clear(inputs);
        for (var bus : inputs) for (int slot = 0; slot < bus.getInventory().getSlots() && amount > 0; slot++) {
            int part = Math.min(amount, 64);
            bus.getInventory().setStackInSlot(slot, new ItemStack(Items.APPLE, part));
            amount -= part;
        }
        check(amount == 0, "Original input buses lack fixture capacity");
    }
    private static int count(List<ItemBusPartMachine> buses) {
        int count = 0;
        for (var bus : buses) for (int slot = 0; slot < bus.getInventory().getSlots(); slot++)
            count += bus.getInventory().getStackInSlot(slot).getCount();
        return count;
    }
    private static void display(ServerLevel world, long expected) {
        var lines = new ArrayList<Component>();
        machine.addDisplayText(lines);
        check(AssemblyLineParallelDisplay.current(machine) == expected, "GUI actual parallel mismatch");
        check(lines.stream().anyMatch(c -> Component.Serializer.toJson(c).contains("assembly_line.parallel_fixed")), "GUI fixed64 missing");
        check(lines.stream().anyMatch(c -> Component.Serializer.toJson(c).contains("parallel.current")), "GUI actual row missing");
        var pos = machine.getPos();
        var player = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "assembly_audit"));
        var data = new CompoundTag();
        var accessor = new BlockAccessorImpl.Builder().level(world).player(player).serverData(data).serverConnected(true)
                .showDetails(true).blockState(world.getBlockState(pos)).blockEntity(() -> world.getBlockEntity(pos))
                .hit(new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)).build();
        new AssemblyLineParallelProvider().appendServerData(data, accessor);
        check(data.contains("enhancedAssemblyLineParallel") && data.getLong("enhancedAssemblyLineParallel") == expected,
                "Jade server payload mismatch");
        var jadeLines = new ArrayList<Component>();
        var tooltip = (ITooltip) Proxy.newProxyInstance(ITooltip.class.getClassLoader(), new Class<?>[]{ITooltip.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("add") && arguments != null && arguments[0] instanceof Component component)
                        jadeLines.add(component);
                    return null;
                });
        // Jade accessors snapshot NBT at construction. Model the client receiving the filled payload.
        var clientAccessor = new BlockAccessorImpl.Builder().from(accessor).serverData(data).build();
        new AssemblyLineParallelProvider().appendTooltip(tooltip, clientAccessor, null);
        var actualRows = jadeLines.stream().map(Component.Serializer::toJson).toList();
        var expectedRows = AssemblyLineParallelDisplay.lines(expected).stream().map(Component.Serializer::toJson).toList();
        check(actualRows.equals(expectedRows), "Jade fixed/actual rendered rows mismatch: " + actualRows + " expected " + expectedRows);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new IllegalStateException(message);
    }
}
