package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Full production geometry and a long-lived, energy-paying audit recipe in a disposable world. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class StellarForgeFixture {
    public static final BlockPos CORE = new BlockPos(0, 100, 0);
    private static final List<BlockPos> placed = new ArrayList<>();
    private static WorkableElectricMultiblockMachine machine;
    private static boolean paused;
    private static int readyTicks;
    private static int orientation;

    public static void setup(MinecraftServer server, int facing) {
        var world = server.overworld();
        if (machine != null) machine.onStructureInvalid();
        for (var pos : placed) world.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        placed.clear();
        orientation = facing;
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(
                new ResourceLocation("gtceu:star_ultimate_material_forge_factory"));
        var shape = definition.getMatchingShapes().getFirst().getBlocks();
        for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
            for (int z = 0; z < shape[x][y].length; z++) {
                var info = shape[x][y][z];
                if (info == null || info.getBlockState().isAir()) continue;
                int dx = x - 106, dz = z;
                if (facing == 1) { int old = dx; dx = -dz; dz = -old; }
                var pos = CORE.offset(dx, y - 39, dz);
                if (pos.equals(CORE)) continue;
                world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                world.setBlock(pos, info.getBlockState(), 2 | 16);
                placed.add(pos);
            }
        world.setChunkForced(0, 0, true);
        world.setBlock(CORE, definition.defaultBlockState(), 2 | 16);
        placed.add(CORE);
        machine = (WorkableElectricMultiblockMachine) MetaMachine.getMachine(world, CORE);
        machine.setFrontFacing(facing == 0 ? Direction.NORTH : Direction.EAST);
        machine.setFlipped(facing != 0);
        paused = false;
        readyTicks = 0;
        GTLEnhancedcore.LOGGER.info("[STELLAR_SERVER] PLACED orientation={} blocks={}", facing, placed.size());
    }

    public static boolean tick() {
        if (machine == null || !machine.isFormed()) return false;
        machine.getEnergyContainer().addEnergy(machine.getEnergyContainer().getEnergyCapacity());
        var logic = machine.getRecipeLogic();
        logic.setWorkingEnabled(!paused);
        if (!paused && logic.getLastRecipe() == null) {
            logic.setupRecipe(GTRecipeBuilder.of(new ResourceLocation("gtl_enhancedcore:stellar_render_audit"),
                    machine.getRecipeType()).outputItems(Items.DIAMOND).duration(1_000_000).EUt(8).buildRawRecipe());
        }
        if (!paused && !logic.isWorking()) return false;
        if (++readyTicks == 40) {
            GTLEnhancedcore.LOGGER.info("[STELLAR_SERVER] RUNNING orientation={} progress={}",
                    orientation, logic.getProgress());
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void command(ServerChatEvent event) {
        if (machine == null || !event.getUsername().equals("PreviewAudit")) return;
        String message = event.getRawText();
        if (!message.startsWith("stellar-audit:")) return;
        event.setCanceled(true);
        var server = event.getPlayer().server;
        server.execute(() -> {
            switch (message.substring("stellar-audit:".length())) {
                case "pause" -> paused = true;
                case "resume" -> paused = false;
                case "mirror" -> setup(server, 1);
                default -> throw new IllegalArgumentException(message);
            }
        });
    }
}
