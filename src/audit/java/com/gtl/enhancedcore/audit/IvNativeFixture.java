package com.gtl.enhancedcore.audit;

import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.multiblock.*;
import com.gregtechceu.gtceu.api.pattern.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.recipe.iv.*;
import com.gtl.enhancedcore.mixin.gtceu.*;
import com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

/** Test-only compact shells retain the actual registered classes, modifiers and machine parts. */
public final class IvNativeFixture {
    private static final Set<String> UPGRADES = Set.of("atomic_energy_excitation_plant", "mage_assembler",
            "superconducting_electromagnetism");
    public static final List<WorkableElectricMultiblockMachine> MACHINES = new ArrayList<>();
    public static final List<MESuperPatternBufferPartMachine> BUFFERS = new ArrayList<>();
    private IvNativeFixture() {}
    public static void put(Map<AEKey,Long> map, AEKey key, long amount) { map.put(key,amount); }
    public static final class Computation implements com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler<Integer> {
        public int available;
        public long paid;
        @Override public List<Integer> handleRecipeInner(com.gregtechceu.gtceu.api.capability.recipe.IO io,
                com.gregtechceu.gtceu.api.recipe.GTRecipe recipe, List<Integer> contents, String slot, boolean simulate) {
            int required = contents.stream().mapToInt(Integer::intValue).sum();
            if (io != com.gregtechceu.gtceu.api.capability.recipe.IO.IN || required > available) return contents;
            if (!simulate) { available -= required; paid += required; }
            return null;
        }
        @Override public List<Object> getContents() { return List.of(available); }
        @Override public double getTotalContentAmount() { return available; }
        @Override public com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability<Integer> getCapability() {
            return com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability.CAP;
        }
    }
    public static Computation attachComputation(WorkableElectricMultiblockMachine machine) {
        var supply = new Computation();
        machine.getCapabilitiesProxy().put(com.gregtechceu.gtceu.api.capability.recipe.IO.IN,
                com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability.CAP, List.of(supply));
        return supply;
    }
    public static final class Wireless implements com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler {
        public java.math.BigInteger available = java.math.BigInteger.ZERO, paid = java.math.BigInteger.ZERO;
        public boolean online = true;
        @Override public boolean consumeEnergy(int value) { return consumeEnergy(java.math.BigInteger.valueOf(value)); }
        @Override public boolean consumeEnergy(long value) { return consumeEnergy(java.math.BigInteger.valueOf(value)); }
        @Override public boolean consumeEnergy(java.math.BigInteger value) {
            if (value.signum() > 0) throw new IllegalStateException("Wireless payment must be negative");
            if (!online || available.add(value).signum() < 0) return false;
            available = available.add(value); paid = paid.subtract(value); return true;
        }
        @Override public java.math.BigInteger getMaxAvailableEnergy() { return available; }
        @Override public boolean isOnline() { return online; }
    }
    public static Wireless attachWireless(WorkableElectricMultiblockMachine machine) {
        var supply = new Wireless();
        ((com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine)machine).setWirelessNetworkEnergyHandler(supply);
        return supply;
    }
    private static Block block(String id) {
        var key = new ResourceLocation(id);
        if (!ForgeRegistries.BLOCKS.containsKey(key)) throw new IllegalStateException("Unknown block " + id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }
    public static void setup(ServerLevel world) {
        boolean fullNative = Boolean.getBoolean("gtl.enhancedcore.fullNative");
        if (fullNative)
            setupFull(world);
        setupCompact(world, fullNative ? 4096 : 0);
    }

    private static void setupCompact(ServerLevel world, int baseX) {
        int index = 0;
        for (String name : IvMachineScope.NATIVE_IDS.stream().sorted().toList()) {
            if (UPGRADES.contains(name)) continue;
            var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtceu",name));
            // The large production geometry is audited separately. Verify its IO predicates before replacing it.
            var production = definition.getPatternFactory().get();
            var grid = ((BlockPatternAccessor)production).gtlEnhancedcore$getBlockMatches();
            Set<com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate> predicates = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var aisle : grid) for (var row : aisle) for (var p : row) {
                predicates.addAll(p.common); predicates.addAll(p.limited);
            }
            boolean superBuffer = false, materialIo = false;
            for (var p : predicates) if (p.candidates != null && p.candidates.get() != null) for (var info : p.candidates.get()) {
                if (info == null) continue;
                var b = info.getBlockState().getBlock();
                if (b == block("gtladditions:me_super_pattern_buffer")) superBuffer = true;
                else if (IvNativeHatches.materials(b)) materialIo = true;
            }
            if (!superBuffer || !materialIo) throw new IllegalStateException(name + " missing optional super buffer or ordinary material IO");
            var pattern = FactoryBlockPattern.start()
                    .aisle("CCC","CSC","CBC")
                    .aisle("CCC","CKC","CMC")
                    .aisle("CCC","CEC","CPC")
                    .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                    .where('C', Predicates.blocks(block("gtceu:solid_machine_casing")))
                    .where('B', Predicates.blocks(block("gtladditions:me_super_pattern_buffer")))
                    .where('E', Predicates.blocks(block("gtceu:zpm_energy_input_hatch")))
                    .where('P', Predicates.blocks(block("gtceu:luv_parallel_hatch")))
                    .where('M', Predicates.blocks(block("gtceu:auto_maintenance_hatch")))
                    .where('K', Predicates.heatingCoils()).build();
            ((MultiblockMachineDefinitionAccessor)definition).gtlEnhancedcore$setPatternFactory(() -> pattern);
            var shape = definition.getMatchingShapes().getFirst().getBlocks();
            WorkableElectricMultiblockMachine controller = null; MESuperPatternBufferPartMachine buffer = null;
            for (int x=0; x<shape.length; x++) for (int y=0; y<shape[x].length; y++) for (int z=0; z<shape[x][y].length; z++) {
                var pos = new BlockPos(baseX + index*16+x, 100+y, z);
                world.setChunkForced(pos.getX()>>4, pos.getZ()>>4, true);
                world.setBlock(pos, shape[x][y][z].getBlockState(), 3);
                var machine = MetaMachine.getMachine(world,pos);
                if (machine instanceof WorkableElectricMultiblockMachine electric) controller = electric;
                if (machine instanceof MESuperPatternBufferPartMachine superPart) buffer = superPart;
            }
            if (controller == null || buffer == null) throw new IllegalStateException("Missing compact fixture " + name);
            MACHINES.add(controller); BUFFERS.add(buffer); index++;
            com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[NATIVE_IV] placed {} class={} logic={}",
                    name, controller.getClass().getName(), controller.getRecipeLogic().getClass().getName());
        }
    }

    private static void setupFull(ServerLevel world) {
        int index = 0;
        for (String name : UPGRADES.stream().sorted().toList()) {
            var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(new ResourceLocation("gtceu", name));
            var shape = definition.getMatchingShapes().getFirst().getBlocks();
            var extras = fullFixtureParts(name, shape);
            WorkableElectricMultiblockMachine controller = null;
            MESuperPatternBufferPartMachine buffer = null;
            int solids = 0;
            for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
                for (int z = 0; z < shape[x][y].length; z++) {
                    var info = shape[x][y][z];
                    if (info == null || info.getBlockState().isAir()) continue;
                    var state = extras.getOrDefault(new BlockPos(x, y, z), info.getBlockState());
                    if (IvNativeHatches.materials(state.getBlock()))
                        state = block("gtladditions:me_super_pattern_buffer").defaultBlockState();
                    if (PartAbility.INPUT_ENERGY.isApplicable(state.getBlock()))
                        state = block("gtceu:zpm_energy_input_hatch").defaultBlockState();
                    if (PartAbility.INPUT_LASER.isApplicable(state.getBlock()))
                        state = block("gtceu:zpm_256a_laser_target_hatch").defaultBlockState();
                    if (PartAbility.MAINTENANCE.isApplicable(state.getBlock()))
                        state = block("gtceu:auto_maintenance_hatch").defaultBlockState();
                    if (PartAbility.PARALLEL_HATCH.isApplicable(state.getBlock()))
                        state = block("gtceu:luv_parallel_hatch").defaultBlockState();
                    var pos = new BlockPos(index * 256 + x, 80 + y, z);
                    world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                    world.setBlock(pos, state, 2 | 16);
                    solids++;
                    var part = MetaMachine.getMachine(world, pos);
                    if (part instanceof com.gtladd.gtladditions.common.machine.multiblock.part.ThreadPartMachine thread) {
                        try {
                            var field = thread.getClass().getDeclaredField("astralArrayInventory");
                            field.setAccessible(true);
                            ((com.lowdragmc.lowdraglib.misc.ItemStackTransfer)field.get(thread)).setStackInSlot(0,
                                    com.gtladd.gtladditions.common.items.GTLAddItems.INSTANCE.getASTRAL_ARRAY().asStack());
                        } catch (ReflectiveOperationException error) {
                            throw new IllegalStateException("Cannot populate real thread hatch", error);
                        }
                    }
                    if (part instanceof WorkableElectricMultiblockMachine electric) controller = electric;
                    if (part instanceof MESuperPatternBufferPartMachine superPart) buffer = superPart;
                }
            if (controller == null || buffer == null) throw new IllegalStateException("Missing full fixture " + name);
            if (controller.getRecipeLogic() instanceof com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<?> mutable)
                mutable.setUseMultipleRecipes(true);
            MACHINES.add(controller);
            BUFFERS.add(buffer);
            index++;
            com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[NATIVE_IV] FULL {} blocks={} class={} logic={}",
                    name, solids, controller.getClass().getName(), controller.getRecipeLogic().getClass().getName());
        }
    }

    private static Map<BlockPos, net.minecraft.world.level.block.state.BlockState> fullFixtureParts(
            String name, com.lowdragmc.lowdraglib.utils.BlockInfo[][][] shape) {
        var slots = new ArrayList<BlockPos>();
        var aisles = new ArrayList<String[]>();
        try {
            com.gtl.enhancedcore.common.structure.StructureData.read(
                    com.gtl.enhancedcore.common.structure.GtlMegastructurePatterns.class.getResourceAsStream(
                    "/data/gtl_enhancedcore/structures/gtl/" + name + ".pattern.gz")).forEachAisle(aisles::add);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        boolean energy = false, parallel = false, thread = false;
        var extras = new HashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
            for (int z = 0; z < shape[x][y].length; z++) {
                if (shape[x][y][z] == null) continue;
                var block = shape[x][y][z].getBlockState().getBlock();
                energy |= PartAbility.INPUT_ENERGY.isApplicable(block) || PartAbility.INPUT_LASER.isApplicable(block);
                parallel |= PartAbility.PARALLEL_HATCH.isApplicable(block);
                thread |= block == block("gtladditions:thread_modifier_hatch");
                char symbol = aisles.get(aisles.size() - 1 - z)[y].charAt(shape.length - 1 - x);
                if (symbol == '#')
                    slots.add(new BlockPos(x, y, z));
            }
        var needed = new ArrayList<String>();
        if (!energy) needed.add("gtceu:zpm_energy_input_hatch");
        if (!parallel) needed.add("gtceu:luv_parallel_hatch");
        if (!thread && (name.equals("atomic_energy_excitation_plant") || name.equals("super_blast_smelter")))
            needed.add("gtladditions:thread_modifier_hatch");
        com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[NATIVE_IV] PARTS {} existing_thread={} added={}",
                name, thread, needed);
        if (slots.size() < needed.size()) throw new IllegalStateException("Not enough fixture ports " + name);
        for (int i = 0; i < needed.size(); i++)
            extras.put(slots.get(i), block(needed.get(i)).defaultBlockState());
        return extras;
    }

    public static String formationDetails(WorkableElectricMultiblockMachine machine) {
        var state = new MultiblockState(machine.getLevel(), machine.getPos());
        boolean matched = machine.getPattern().checkPatternAt(state, machine.getPos(),
                machine.getFrontFacing(), machine.getUpwardsFacing(), false, false);
        return "direct=" + matched + " at=" + state.getPos() + " block=" + state.getBlockState()
                + " reason=" + (state.error == null ? "none" : state.error.getErrorInfo().getString());
    }

    public static BlockPos networkPosition(MESuperPatternBufferPartMachine buffer) {
        var world = buffer.getLevel();
        for (var direction : net.minecraft.core.Direction.values()) {
            var pos = buffer.getPos().relative(direction);
            if (world.isEmptyBlock(pos) && world.isEmptyBlock(pos.above())) return pos;
        }
        throw new IllegalStateException("No exterior network attachment");
    }

    public static void thermalRules(WorkableElectricMultiblockMachine machine) throws Exception {
        String name = machine.getDefinition().getId().getPath();
        if (!name.equals("super_blast_smelter") && !name.equals("atomic_energy_excitation_plant")) return;
        var type = (com.gregtechceu.gtceu.api.block.ICoilType)machine.getClass().getMethod("getCoilType").invoke(machine);
        var recipe = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.of(
                new ResourceLocation("gtl_enhancedcore:thermal_audit"), machine.getRecipeType())
                .EUt(2048).duration(800).buildRawRecipe();
        recipe.data.putInt("ebf_temp", Integer.MAX_VALUE);
        var logic = machine.getRecipeLogic();
        boolean accepted;
        if (logic instanceof org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic multiple)
            accepted = multiple.getDataCheck().test(recipe.data, machine);
        else accepted = ((IvNativeMutableAccessor)logic).iv$recipeCheck().test(recipe, machine);
        if (accepted) throw new AssertionError("Excessive temperature accepted by " + name);
        recipe.data.putInt("ebf_temp", 0);
        if (logic instanceof org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic multiple) {
            if (!multiple.getDataCheck().test(recipe.data, machine)) throw new AssertionError("Low temperature rejected");
            var nativeLogic = (com.gtl.enhancedcore.mixin.gtlcore.IvNativeMultipleAccessor)logic;
            double raw = 2048.0 * 800;
            double reduced = nativeLogic.iv$totalEu(recipe);
            if (reduced <= 0 || reduced >= raw) throw new AssertionError("Native coil energy reduction missing");
            com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[NATIVE_IV] THERMAL {} temperature={} raw={} reduced={}",
                    name, type.getCoilTemperature(), raw, reduced);
        }
    }

    public static void batchTiming(WorkableElectricMultiblockMachine machine, MESuperPatternBufferPartMachine buffer) {
        if (!machine.getDefinition().getId().getPath().equals("super_blast_smelter")) return;
        var logic = (com.gtl.enhancedcore.mixin.gtlcore.IvNativeMultipleAccessor)machine.getRecipeLogic();
        for (var job : IvBuffers.state(buffer).jobs) if (job.active()) {
            double cost = logic.iv$totalEu(job.recipe) * job.parallel * logic.iv$euMultiplier();
            var expected = com.gtladd.gtladditions.utils.RecipeCalculationHelper.INSTANCE.buildNormalRecipe(
                    List.of(), List.of(), cost, machine.getOverclockVoltage(), 20);
            if (job.duration != expected.duration || job.eut != com.gregtechceu.gtceu.api.recipe.RecipeHelper.getInputEUt(expected))
                throw new AssertionError("Isolated batch lost native coil timing/energy");
        }
    }
}
