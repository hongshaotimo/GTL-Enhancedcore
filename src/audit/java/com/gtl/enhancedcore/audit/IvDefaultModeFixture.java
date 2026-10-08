package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.recipe.iv.IvBufferRegistry;
import com.gtl.enhancedcore.mixin.gtceu.MultiblockMachineDefinitionAccessor;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Real controller fixture with ordinary IO and no registered pattern buffer. */
public final class IvDefaultModeFixture {
    public static WorkableElectricMultiblockMachine MACHINE;
    public static ItemBusPartMachine INPUT;
    public static ItemBusPartMachine OUTPUT;
    private static BlockPos mixedBufferPos;

    private IvDefaultModeFixture() {}

    public static void setup(ServerLevel world) {
        var id = new ResourceLocation("gtl_enhancedcore", "plasma_machine_tool");
        var definition = (MultiblockMachineDefinition)GTRegistries.MACHINES.get(id);
        Objects.requireNonNull(definition, "Missing plasma machine definition");
        var materialPorts = IvBufferRegistry.materialPorts(-1);
        var pattern = FactoryBlockPattern.start()
                .aisle("CBC", "CSC", "CIC")
                .aisle("CCC", "CCC", "COC")
                .aisle("CCC", "CEC", "CPC")
                .where('S', Predicates.controller(Predicates.blocks(definition.get())))
                .where('C', Predicates.blocks(block("gtceu:solid_machine_casing")))
                .where('I', materialPorts)
                .where('O', Predicates.blocks(block("gtceu:lv_output_bus")))
                .where('B', materialPorts)
                .where('E', Predicates.abilities(PartAbility.INPUT_ENERGY))
                .where('P', Predicates.blocks(block("gtceu:lv_parallel_hatch")))
                .build();
        ((MultiblockMachineDefinitionAccessor)definition).gtlEnhancedcore$setPatternFactory(() -> pattern);
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        String[][] aisles = {
                {"CBC", "CSC", "CIC"},
                {"CCC", "CCC", "COC"},
                {"CCC", "CEC", "CPC"}
        };
        for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
            for (int z = 0; z < shape[x][y].length; z++) {
                var info = shape[x][y][z];
                char symbol = aisles[aisles.length - 1 - z][y].charAt(shape.length - 1 - x);
                boolean materialPort = symbol == 'I' || symbol == 'B';
                if ((info == null || info.getBlockState().isAir()) && !materialPort) continue;
                var pos = new BlockPos(180 + x, 100 + y, z);
                var state = info == null ? null : info.getBlockState();
                if (symbol == 'I') state = block("gtceu:lv_input_bus").defaultBlockState();
                if (symbol == 'B') {
                    state = block("gtladditions:me_super_pattern_buffer").defaultBlockState();
                    mixedBufferPos = pos;
                }
                world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                world.setBlock(pos, state, 2 | 16);
                var machine = MetaMachine.getMachine(world, pos);
                if (machine instanceof WorkableElectricMultiblockMachine controller
                        && controller.getDefinition().getId().equals(id)) MACHINE = controller;
                if (symbol == 'I') {
                    if (!(machine instanceof ItemBusPartMachine bus))
                        throw new IllegalStateException("Input symbol did not create an item bus at " + pos
                                + ": block=" + ForgeRegistries.BLOCKS.getKey(state.getBlock())
                                + ", machine=" + (machine == null ? "null" : machine.getClass().getName()));
                    INPUT = bus;
                }
                if (symbol == 'O') {
                    if (!(machine instanceof ItemBusPartMachine bus))
                        throw new IllegalStateException("Output symbol did not create an item bus at " + pos
                                + ": block=" + ForgeRegistries.BLOCKS.getKey(state.getBlock())
                                + ", machine=" + (machine == null ? "null" : machine.getClass().getName()));
                    OUTPUT = bus;
                }
            }
        if (MACHINE == null || INPUT == null || OUTPUT == null || mixedBufferPos == null)
            throw new IllegalStateException("Ordinary IO fixture entities missing: controller=" + (MACHINE != null)
                    + ", input=" + (INPUT != null) + ", output=" + (OUTPUT != null)
                    + ", bufferPosition=" + (mixedBufferPos != null));
        if (!MACHINE.getDefinition().getId().equals(id)) throw new IllegalStateException("Fixture found the wrong controller");
        if (MACHINE.checkPattern())
            throw new IllegalStateException("A super pattern buffer mixed with ordinary item buses formed");
        var lock = MACHINE.getPatternLock();
        lock.lock();
        try {
            MACHINE.onStructureInvalid();
            world.setBlock(mixedBufferPos, block("gtceu:lv_input_bus").defaultBlockState(), 2 | 16);
            if (!MACHINE.checkPattern()) throw new IllegalStateException("Ordinary-only material ports did not form");
            MACHINE.onStructureFormed();
        } finally { lock.unlock(); }
        MACHINE.setActiveRecipeType(1);
        INPUT.getInventory().setStackInSlot(0, new ItemStack(Items.APPLE));
        if (!INPUT.getInventory().getStackInSlot(0).is(Items.APPLE))
            throw new IllegalStateException("Ordinary input bus rejected the fixture ingredient");
    }

    private static net.minecraft.world.level.block.Block block(String id) {
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        if (block == null) throw new IllegalStateException("Missing audit block " + id);
        return block;
    }
}
