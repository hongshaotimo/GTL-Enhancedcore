package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.lowdragmc.lowdraglib.utils.BlockInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;
import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior;

/** Whole compressor through the installed terminal, followed by exact AE/backpack repair accounting. */
public final class GiantTerminalChecks {
    private GiantTerminalChecks() {}

    public static int run(ServerPlayer player, MEStorage storage, BlockPos access) throws Exception {
        var world = player.serverLevel();
        var definition = (MultiblockMachineDefinition) GTRegistries.MACHINES.get(
                new ResourceLocation("gtl_enhancedcore:infinity_singularity_compressor"));
        BlockInfo[][][] shape = definition.getMatchingShapes().getFirst().getBlocks();
        BlockPos localCore = null;
        for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
            for (int z = 0; z < shape[x][y].length; z++) {
                var info = shape[x][y][z];
                if (info != null && info.getBlockState().getBlock() instanceof IMachineBlock block
                        && block.getDefinition() == definition) localCore = new BlockPos(x, y, z);
            }
        require(localCore != null, "Missing giant controller");
        var center = new BlockPos(2048, 100, 0);
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> expected = new ArrayList<>();
        Map<Block, List<BlockPos>> repairGroups = new LinkedHashMap<>();
        for (int x = 0; x < shape.length; x++) for (int y = 0; y < shape[x].length; y++)
            for (int z = 0; z < shape[x][y].length; z++) {
                var info = shape[x][y][z];
                if (info == null || info.getBlockState().isAir()) continue;
                var pos = center.offset(x - localCore.getX(), y - localCore.getY(), z - localCore.getZ());
                require(!world.isOutsideBuildHeight(pos), "Giant exceeds fixture build height");
                positions.add(pos);
                expected.add(info.getBlockState());
                if (!info.hasBlockEntity() && !(info.getBlockState().getBlock() instanceof LampBlock)
                        && info.getBlockState().getFluidState().isEmpty()) {
                    var group = repairGroups.computeIfAbsent(info.getBlockState().getBlock(), ignored -> new ArrayList<>());
                    if (group.size() < 5000) group.add(pos);
                }
            }
        world.setBlock(center, definition.getBlock().defaultBlockState(), 3);
        var machine = (MultiblockControllerMachine) MetaMachine.getMachine(world, center);
        machine.setFrontFacing(Direction.NORTH);
        var pattern = AdvancedBlockPattern.getAdvancedBlockPattern(machine.getPattern());
        var settings = new UltimateTerminalBehavior.AutoBuildSetting();
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        long start = System.nanoTime();
        pattern.autoBuild(player, machine.getMultiblockState(), settings);
        log("creative_build_ms=" + (System.nanoTime() - start) / 1_000_000.0 + " solids=" + positions.size());
        int checks = 0, lamps = 0;
        for (int i = 0; i < positions.size(); i++) {
            var actual = world.getBlockState(positions.get(i));
            var wanted = expected.get(i);
            if (wanted.getBlock() instanceof IMachineBlock) continue; // Native terminal retains hatch candidate selection.
            require(actual.is(wanted.getBlock()), "Missing giant block at " + positions.get(i));
            checks++;
            if (wanted.getBlock() instanceof LampBlock) {
                require(LampBlock.isInverted(actual) == LampBlock.isInverted(wanted)
                        && LampBlock.isLightEnabled(actual) == LampBlock.isLightEnabled(wanted)
                        && LampBlock.isBloomEnabled(actual) == LampBlock.isBloomEnabled(wanted), "Giant lamp state changed");
                lamps++;
            }
        }
        var repair = repairGroups.entrySet().stream().max(java.util.Comparator.comparingInt(entry -> entry.getValue().size())).orElseThrow();
        require(repair.getValue().size() == 5000, "Insufficient giant repair positions");
        for (int route = 0; route < 2; route++) {
            for (var pos : repair.getValue()) world.removeBlock(pos, false);
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            var material = new ItemStack(repair.getKey(), 5000);
            var key = AEItemKey.of(material);
            ItemStack backpack = route == 1 ? BackpackBuildFixture.create() : ItemStack.EMPTY;
            long before = storage.getAvailableStacks().get(key);
            if (route == 0) require(storage.insert(key, 5000, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 5000, "AE supply rejected");
            else {
                BackpackBuildFixture.insert(backpack, material);
                player.getInventory().setItem(9, backpack);
            }
            settings.setAeMode(route == 0);
            settings.setBoundAE(GlobalPos.of(world.dimension(), access));
            start = System.nanoTime();
            pattern.autoBuild(player, machine.getMultiblockState(), settings);
            for (var pos : repair.getValue()) require(world.getBlockState(pos).is(repair.getKey()), "Giant repair missing");
            if (route == 0) require(storage.getAvailableStacks().get(key) == before, "AE giant accounting changed");
            else require(BackpackBuildFixture.contents(backpack).isEmpty()
                    && player.getInventory().getItem(9) == backpack, "Backpack giant accounting changed");
            log("route=" + (route == 0 ? "AE" : "sophisticated_backpack") + " repair=5000 elapsed_ms="
                    + (System.nanoTime() - start) / 1_000_000.0 + " exact_accounting=OK");
            checks += 5001;
        }
        log("giant_geometry=OK lamps=" + lamps + " checks=" + checks);
        return checks;
    }

    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[LAMP_AUDIT] {}", message); }
}
