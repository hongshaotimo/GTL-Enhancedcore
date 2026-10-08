package com.gtl.enhancedcore.audit;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.gregtechceu.gtceu.api.block.IMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.block.LampBlock;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.integration.terminal.LampPlacement;
import org.gtlcore.gtlcore.integration.terminal.StableBlockCandidates;
import com.gtl.enhancedcore.mixin.gtceu.BlockPatternAccessor;
import com.hepdd.gtmthings.common.item.AdvancedTerminalBehavior;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior;

/** Lamp-only audit through unchanged upstream terminal placement and material paths. */
public final class LampPlacementChecks {
    private static final BlockPos CENTER = new BlockPos(512, 100, 0);
    private static final BlockPos ACCESS = new BlockPos(521, 100, 9);
    private static final List<LampBlock> LAMPS = new ArrayList<>();
    private static ServerPlayer player;
    private static IMultiController controller;
    private static Map<UUID, ServerPlayer> online;
    private static MEStorage storage;
    private static ItemStack backpack;
    private static Method offset;
    private static int checks, lampCase;
    private static boolean giantDone;

    public static void prepare(MinecraftServer server) throws Exception {
        var world = server.overworld();
        for (int x = 30; x <= 33; x++) for (int z = -2; z <= 2; z++) world.setChunkForced(x, z, true);
        var id = new ResourceLocation("gtl_enhancedcore:universal_joint_factory");
        var block = ForgeRegistries.BLOCKS.getValue(id);
        check(block != null && id.equals(ForgeRegistries.BLOCKS.getKey(block)), "Missing lamp audit controller " + id);
        world.setBlock(CENTER, block.defaultBlockState(), 3);
        controller = (IMultiController) MetaMachine.getMachine(world, CENTER);
        check(controller != null, "Lamp audit controller did not create its machine");
        player = new ServerPlayer(server, world, new GameProfile(UUID.randomUUID(), "lamp_audit"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        player.setPos(550, 90, 30);
        online = ClaimReplacementChecks.onlinePlayers(server);
        online.put(player.getUUID(), player);
        var teams = (dev.ftb.mods.ftbteams.data.TeamManagerImpl) dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
        ClaimReplacementChecks.personalTeam(teams, player);
        ForgeRegistries.BLOCKS.getValues().stream().filter(LampBlock.class::isInstance)
                .map(LampBlock.class::cast).forEach(LAMPS::add);
        check(LAMPS.size() == 32, "Expected 32 regular/borderless lamp colors");
        offset = BlockPattern.class.getDeclaredMethod("setActualRelativeOffset", int.class, int.class, int.class,
                Direction.class, Direction.class, boolean.class);
        offset.setAccessible(true);
        backpack = BackpackBuildFixture.create();
        world.setBlock(ACCESS, appeng.core.definitions.AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState(), 3);
        var access = (appeng.blockentity.networking.WirelessAccessPointBlockEntity) world.getBlockEntity(ACCESS);
        var power = ACCESS.relative(access.getOrientation().getSide(appeng.api.orientation.RelativeSide.BACK));
        world.setBlock(power, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        world.setBlock(power.east(), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState(), 3);
        ((appeng.blockentity.storage.DriveBlockEntity) world.getBlockEntity(power.east()))
                .getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
    }

    public static void begin(MinecraftServer server) {
        var access = (appeng.blockentity.networking.WirelessAccessPointBlockEntity)
                server.overworld().getBlockEntity(ACCESS);
        check(access.isActive(), "AE fixture inactive");
        storage = access.getGrid().getStorageService().getInventory();
        checkTransforms();
        for (String id : List.of("void_constrained_mining_field", "dragon_field_proliferation_core")) {
            var definition = com.gregtechceu.gtceu.api.registry.GTRegistries.MACHINES
                    .get(new ResourceLocation("gtl_enhancedcore", id));
            var pattern = ((com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition) definition)
                    .getPatternFactory().get();
            int found = 0;
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<TraceabilityPredicate, Boolean>());
            for (var aisle : ((BlockPatternAccessor) pattern).gtlEnhancedcore$getBlockMatches())
                for (var row : aisle) for (var predicate : row) {
                    if (!seen.add(predicate)) continue;
                    for (var simple : predicate.common) if (simple.candidates != null)
                        for (var info : simple.candidates.get()) if (info.getBlockState().getBlock() instanceof LampBlock) {
                            found++;
                            check(LampBlock.isInverted(info.getBlockState()), "Blueprint lost inverted preview");
                            var candidates = new UltimateTerminalBehavior.AutoBuildSetting().apply(
                                    new com.lowdragmc.lowdraglib.utils.BlockInfo[]{info});
                            check(candidates.size() == 1 && LampBlock.isInverted(candidates.getFirst().getTag())
                                    && LampBlock.isLightEnabled(candidates.getFirst().getTag()),
                                    "Blueprint lost exact inverted/light NBT");
                        }
                }
            check(found > 0, "No configured lamps in " + id);
        }
        log("actual_mining_and_dragon_blueprint_preview_and_material_NBT=OK");
    }

    private static void checkTransforms() {
        var original = FactoryBlockPattern.start().aisle("CS")
                .where('C', Predicates.controller(Predicates.blocks((IMachineBlock) controller.self().getBlockState().getBlock())))
                .where('S', Predicates.blocks(Blocks.STONE)).build();
        var ultimate = org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern.getAdvancedBlockPattern(original);
        var advanced = com.hepdd.gtmthings.api.pattern.AdvancedBlockPattern.getAdvancedBlockPattern(original);
        var random = new java.util.Random(275);
        try {
            var ultimateOffset = relativeOffsetMethod(ultimate.getClass());
            var advancedOffset = relativeOffsetMethod(advanced.getClass());
            for (var front : Direction.values()) for (var up : Direction.Plane.HORIZONTAL) {
                for (boolean flipped : new boolean[]{false, true}) for (int i = 0; i < 50; i++) {
                    int x = random.nextInt(567) - 283, y = random.nextInt(365) - 182, z = random.nextInt(567) - 283;
                    var expected = (BlockPos) offset.invoke(original, x, y, z, front, up, flipped);
                    check(expected.equals(ultimateOffset.invoke(ultimate, x, y, z, front, up, flipped)), "Ultimate transform changed");
                    check(expected.equals(advancedOffset.invoke(advanced, x, y, z, front, up, flipped)), "Advanced transform changed");
                }
            }
            log("transforms=4800 all_front_up_flip_combinations=OK");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static Method relativeOffsetMethod(Class<?> type) throws NoSuchMethodException {
        var method = type.getDeclaredMethod("setActualRelativeOffset", int.class, int.class, int.class,
                Direction.class, Direction.class, boolean.class);
        method.setAccessible(true);
        return method;
    }

    public static boolean tick(MinecraftServer server) throws Exception {
        for (int i = 0; i < 4 && lampCase < 1280; i++, lampCase++) lamp(server, lampCase);
        if (lampCase < 1280) return false;
        if (!giantDone) {
            giantDone = true;
            checks += GiantTerminalChecks.run(player, storage, ACCESS);
        }
        log("actual_lamp_placements=1280 colors=32 configurations=8 native_routes="
                + "ultimate_creative,advanced_inventory,basic_inventory,ultimate_AE,ultimate_sophisticated_backpack");
        return true;
    }

    private static void lamp(MinecraftServer server, int test) throws Exception {
        LampBlock lamp = LAMPS.get(test / 40);
        int variant = test / 5 % 8;
        int route = test % 5;
        var stack = lamp.getStackFromIndex(variant);
        var expected = lamp.defaultBlockState().setValue(LampBlock.INVERTED, LampBlock.isInverted(stack.getTag()))
                .setValue(LampBlock.BLOOM, LampBlock.isBloomEnabled(stack.getTag()))
                .setValue(LampBlock.LIGHT, LampBlock.isLightEnabled(stack.getTag()));
        var info = LampPlacement.info(expected);
        var predicate = new TraceabilityPredicate(state -> state.getBlockState().is(lamp),
                StableBlockCandidates.mark(() -> new com.lowdragmc.lowdraglib.utils.BlockInfo[]{info}));
        var pattern = FactoryBlockPattern.start().aisle("CL")
                .where('C', Predicates.controller(Predicates.blocks((IMachineBlock) controller.self().getBlockState().getBlock())))
                .where('L', predicate).build();
        var pos = CENTER.offset((BlockPos) offset.invoke(pattern, 1, 0, 0,
                controller.self().getFrontFacing(), controller.self().getUpwardsFacing(), false));
        var world = server.overworld();
        world.removeBlock(pos, false);
        world.removeBlock(pos.above(), false);
        player.setGameMode(route == 0 ? GameType.CREATIVE : GameType.SURVIVAL);
        player.getInventory().clearContent();
        var key = AEItemKey.of(stack);
        if (route == 3) {
            check(storage.insert(key, 1, Actionable.MODULATE, IActionSource.ofPlayer(player)) == 1, "AE insert failed");
        } else if (route == 4) {
            BackpackBuildFixture.insert(backpack, stack.copy());
            player.getInventory().setItem(9, backpack);
        } else if (route != 0) player.getInventory().add(stack.copy());
        if (route == 1) {
            com.hepdd.gtmthings.api.pattern.AdvancedBlockPattern.getAdvancedBlockPattern(pattern)
                    .autoBuild(player, controller.getMultiblockState(), new AdvancedTerminalBehavior.AutoBuildSetting());
        } else if (route == 2) {
            pattern.autoBuild(player, controller.getMultiblockState());
        } else {
            var settings = new UltimateTerminalBehavior.AutoBuildSetting();
            settings.setAeMode(route == 3);
            settings.setBoundAE(GlobalPos.of(world.dimension(), ACCESS));
            org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern.getAdvancedBlockPattern(pattern)
                    .autoBuild(player, controller.getMultiblockState(), settings);
        }
        var actual = world.getBlockState(pos);
        check(actual.is(lamp), "Lamp not placed: route=" + route + " variant=" + variant);
        check(actual.getValue(LampBlock.INVERTED).equals(expected.getValue(LampBlock.INVERTED))
                && actual.getValue(LampBlock.BLOOM).equals(expected.getValue(LampBlock.BLOOM))
                && actual.getValue(LampBlock.LIGHT).equals(expected.getValue(LampBlock.LIGHT)),
                "Lamp settings changed: " + actual + " expected=" + expected);
        check(actual.getLightEmission(world, pos) == (variant == 0 || variant == 1 ? 15 : 0),
                "Incorrect unpowered lamp light");
        world.setBlock(pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        actual = world.getBlockState(pos);
        check(actual.getValue(LampBlock.POWERED) && actual.getLightEmission(world, pos)
                == (variant == 4 || variant == 5 ? 15 : 0), "Redstone inversion broken");
        world.removeBlock(pos.above(), false);
        check(!world.getBlockState(pos).getValue(LampBlock.POWERED), "Power state stuck");
        if (route == 3) check(storage.getAvailableStacks().get(key) == 0, "AE material accounting mismatch");
        else if (route == 4) check(BackpackBuildFixture.contents(backpack).isEmpty()
                && player.getInventory().getItem(9) == backpack, "Backpack material accounting mismatch");
        else if (route != 0) check(player.getInventory().isEmpty(), "Inventory material accounting mismatch");
        world.removeBlock(pos, false);
    }

    public static int cleanup() {
        if (online != null && player != null) online.remove(player.getUUID());
        return checks;
    }
    private static void check(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
        checks++;
    }
    private static void log(String message) { GTLEnhancedcore.LOGGER.info("[LAMP_AUDIT] {}", message); }
}
