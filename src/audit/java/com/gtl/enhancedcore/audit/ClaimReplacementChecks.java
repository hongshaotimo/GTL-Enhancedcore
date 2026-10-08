package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.config.GTLConfig;
import com.gtl.enhancedcore.common.machine.ClaimReplacementTerminalMachine;
import com.lowdragmc.lowdraglib.gui.widget.PhantomSlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.data.ChunkTeamDataImpl;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.PlayerTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.ForgeRegistries;

/** Real FTB claims, real inventories, Forge protection events and production tick callbacks. */
public final class ClaimReplacementChecks {
    private static final BlockPos TERMINAL = new BlockPos(320, 90, 0);
    private static final BlockPos FIRST = TERMINAL.offset(2, 0, 2);
    private static ClaimReplacementTerminalMachine terminal;
    private static RecordingPlayer owner, actor, outsider;
    private static PlayerTeam actorPersonal;
    private static PartyTeam party;
    private static ChunkTeamDataImpl claims;
    private static ClaimedChunkImpl claim;
    private static Map<UUID, ServerPlayer> online;
    private static List<PhantomSlotWidget> slots;
    private static final BlockPos[] ACCESS_POINTS = {new BlockPos(326, 90, 7), new BlockPos(326, 90, 13)};
    private static int previousInterval, checks;

    private ClaimReplacementChecks() {}

    public static void prepare(MinecraftServer server) throws Exception {
        checks = 0;
        previousInterval = GTLConfig.CLAIM_REPLACEMENT_INTERVAL_TICKS.get();
        GTLConfig.CLAIM_REPLACEMENT_INTERVAL_TICKS.set(1);
        var world = server.overworld();
        world.setChunkForced(20, 0, true);
        world.setBlock(TERMINAL, ForgeRegistries.BLOCKS.getValue(
                new ResourceLocation("gtl_enhancedcore", "claim_replacement_terminal")).defaultBlockState(), 3);
        terminal = (ClaimReplacementTerminalMachine) MetaMachine.getMachine(world, TERMINAL);
        owner = new RecordingPlayer(server, "claim_owner");
        actor = new RecordingPlayer(server, "claim_actor");
        outsider = new RecordingPlayer(server, "claim_outsider");
        online = onlinePlayers(server);
        online.put(owner.getUUID(), owner);
        online.put(actor.getUUID(), actor);
        online.put(outsider.getUUID(), outsider);
        var teams = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        var ownerPersonal = personalTeam(teams, owner);
        actorPersonal = personalTeam(teams, actor);
        personalTeam(teams, outsider);
        party = new PartyTeam(teams, UUID.randomUUID());
        teams.getTeamMap().put(party.getId(), party);
        party.addMember(owner.getUUID(), TeamRank.OWNER);
        party.addMember(actor.getUUID(), TeamRank.MEMBER);
        ownerPersonal.setEffectiveTeam(party);
        actorPersonal.setEffectiveTeam(party);
        claims = (ChunkTeamDataImpl) FTBChunksAPI.api().getManager().getOrCreateData(party);
        var quota = ChunkTeamDataImpl.class.getDeclaredField("maxClaimChunks");
        quota.setAccessible(true);
        quota.setInt(claims, 10);
        var dimension = new ChunkDimPos(world.dimension(), 20, 0);
        var result = claims.claim(server.createCommandSourceStack(), dimension, false);
        check(result instanceof ClaimedChunkImpl, "Cannot create actual FTB claim: " + result);
        claim = (ClaimedChunkImpl) result;
        claim.setForceLoadedTime(System.currentTimeMillis());
        // Reproduce a marked, loaded claim missing from the offline-force-load cache.
        var offline = ChunkTeamDataImpl.class.getDeclaredField("canForceLoadChunks");
        offline.setAccessible(true);
        offline.set(claims, false);
        claims.getManager().clearForceLoadedCache();
        check(claim.isForceLoaded() && !claim.isActuallyForceLoaded(), "Force-load distinction not reproduced");
        check(!claims.getManager().getForceLoadedChunks(world.dimension()).containsKey(
                net.minecraft.world.level.ChunkPos.asLong(20, 0)), "Legacy force-load cache unexpectedly permits claim");
        terminal.onMachinePlaced(owner, ItemStack.EMPTY);
        check(terminal.canOperate(actor) && !terminal.canOperate(outsider), "Team access fixture");
        slots = ((WidgetGroup) terminal.createUIWidget()).getWidgetsByType(PhantomSlotWidget.class);
        check(slots.size() == 2, "Replacement ghost slots missing");
        for (var accessPos : ACCESS_POINTS) {
            world.setBlock(accessPos, appeng.core.definitions.AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState(), 3);
            var access = (appeng.blockentity.networking.WirelessAccessPointBlockEntity) world.getBlockEntity(accessPos);
            var powerPos = accessPos.relative(access.getOrientation().getSide(appeng.api.orientation.RelativeSide.BACK));
            world.setBlock(powerPos, appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
            var drivePos = powerPos.east();
            world.setBlock(drivePos, appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState(), 3);
            var drive = (appeng.blockentity.storage.DriveBlockEntity) world.getBlockEntity(drivePos);
            drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
        }
    }

    public static int run(MinecraftServer server) throws Exception {
        var world = server.overworld();
        reset(3);
        terminal.detectClicked(actor);
        check(number("totalCount") == 3, "Section scan lost target blocks");
        terminal.replaceClicked(actor);
        check(flag("replacing"), "Teammate could not start");
        slots.forEach(slot -> slot.getHandler().setChanged());
        check(flag("replacing"), "Unchanged ghost-slot notification canceled the job");
        tick(3);
        check(number("doneCount") == 3 && !flag("replacing"), "Teammate job stopped without replacing all targets");
        check(count(actor, Items.GOLD_BLOCK) == 13 && count(actor, Items.EMERALD_BLOCK) == 3,
                "Initiator material accounting");
        check(owner.getInventory().isEmpty(), "Bound owner's inventory was used instead of initiator's");
        check(actor.messages.stream().anyMatch(text -> text.contains("claim_replacement.finished")),
                "Completion summary missing");
        log("teammate_inventory_and_marked_claim_without_offline_ticket=OK");

        reset(3);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        tick(1);
        terminal.detectClicked(actor);
        check(!flag("replacing") && number("totalCount") == 2, "Redetect retained running subscription");
        tick(3);
        check(number("queueIndex") == 0 && number("doneCount") == 0, "Stopped callback consumed fresh detection");
        terminal.replaceClicked(actor);
        tick(2);
        check(number("doneCount") == 2 && count(actor, Items.GOLD_BLOCK) == 13, "Redetected queue did not finish");

        reset(2);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        online.remove(actor.getUUID());
        tick(2);
        check(flag("replacing") && number("queueIndex") == 0 && text("resultKey").equals("waiting_player"),
                "Offline initiator consumed or ended the queue");
        online.put(actor.getUUID(), actor);
        tick(2);
        check(number("doneCount") == 2 && !flag("replacing"), "Returning initiator did not resume");

        reset(2);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        actorPersonal.setEffectiveTeam(actorPersonal);
        tick(1);
        check(!flag("replacing") && text("resultKey").equals("permission_lost")
                && count(actor, Items.GOLD_BLOCK) == 16, "Leaving team allowed material consumption");
        actorPersonal.setEffectiveTeam(party);

        reset(1);
        terminal.detectClicked(actor);
        terminal.replaceClicked(outsider);
        check(!flag("replacing"), "Unrelated player started replacement");
        terminal.replaceClicked(actor);
        claim.setForceLoadedTime(0);
        tick(1);
        check(number("doneCount") == 0 && number("skippedCount") == 1
                && world.getBlockState(FIRST).is(Blocks.EMERALD_BLOCK)
                && count(actor, Items.GOLD_BLOCK) == 16, "Removed force-load marking was ignored");
        claim.setForceLoadedTime(System.currentTimeMillis());
        log("redetect_offline_resume_team_change_and_removed_force_load=OK");

        reset(2);
        terminal.detectClicked(actor);
        Consumer<BlockEvent.BreakEvent> deny = event -> {
            if (event.getPos().equals(FIRST)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(deny);
        try {
            terminal.replaceClicked(actor);
            tick(2);
            check(number("doneCount") == 1 && number("skippedCount") == 1
                    && world.getBlockState(FIRST).is(Blocks.EMERALD_BLOCK)
                    && count(actor, Items.GOLD_BLOCK) == 15, "Forge protection or debit order was ignored");
            check(text("resultKey").equals("protected"), "Protection failure was hidden");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(deny);
        }

        testEntityPlaceEventProtection();

        reset(2);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        actor.getInventory().clearContent();
        tick(1);
        check(!flag("replacing") && text("resultKey").equals("insufficient")
                && world.getBlockState(FIRST).is(Blocks.EMERALD_BLOCK), "Missing material silently ended or destroyed target");

        reset(2);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        slots.get(0).getHandler().set(new ItemStack(Items.DIAMOND_BLOCK));
        tick(2);
        check(!flag("replacing") && !flag("detected") && world.getBlockState(FIRST).is(Blocks.EMERALD_BLOCK),
                "Changing a ghost template left a live job");

        reset(1);
        world.setBlock(FIRST, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X), 3);
        slots.get(0).getHandler().set(new ItemStack(Items.BIRCH_LOG));
        slots.get(1).getHandler().set(new ItemStack(Items.OAK_LOG));
        actor.getInventory().add(new ItemStack(Items.BIRCH_LOG));
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        tick(1);
        check(world.getBlockState(FIRST).is(Blocks.BIRCH_LOG)
                && world.getBlockState(FIRST).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X,
                "Replacement lost common block-state properties");
        check(count(actor, Items.OAK_LOG) == 1 && count(actor, Items.BIRCH_LOG) == 0, "Oriented block accounting");

        reset(1);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        world.setBlock(FIRST, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
        tick(1);
        check(number("skippedCount") == 1 && count(actor, Items.GOLD_BLOCK) == 16
                && text("resultKey").equals("changed"), "Changed target debited material");

        reset(1);
        slots.get(0).getHandler().set(new ItemStack(Items.STICK));
        terminal.detectClicked(actor);
        check(!flag("detected"), "Non-placeable replacement passed detection");
        slots.get(0).getHandler().set(new ItemStack(Items.EMERALD_BLOCK));
        terminal.detectClicked(actor);
        check(!flag("detected"), "Same-block replacement passed detection");
        log("forge_protection_material_shortage_template_changes_orientation_and_stale_targets=OK");

        reset(4);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        check(flag("replacing"), "Asynchronous subscription did not start");
        return checks;
    }

    public static int finish() throws Exception {
        check(!flag("replacing") && number("doneCount") == 4, "Actual server tick subscription did not complete: "
                + "replacing=" + flag("replacing") + ", done=" + number("doneCount") + ", total=" + number("totalCount")
                + ", queue=" + number("queueIndex") + ", result=" + text("resultKey") + ", invalid=" + terminal.isInValid()
                + ", state=" + terminal.getBlockState() + ", actorRemoved=" + actor.isRemoved());
        check(count(actor, Items.GOLD_BLOCK) == 12 && count(actor, Items.EMERALD_BLOCK) == 4,
                "Actual tick subscription duplicated or lost material");
        testAeNetworks();
        terminal.onUnload();
        terminal.onLoad();
        check(!flag("detected") && number("totalCount") == 0, "Reload exposed an unpersisted queue");
        log("actual_tick_subscription_and_reload=OK");
        return checks;
    }

    private static void testEntityPlaceEventProtection() throws Exception {
        reset(0);
        check(!flag("aeMode"), "EntityPlaceEvent protection fixture must use the initiator backpack");
        var world = actor.serverLevel();
        var originalState = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.WEST);
        world.setBlock(FIRST, originalState, 3);
        world.setBlock(FIRST.east(), originalState, 3);
        slots.get(1).getHandler().set(new ItemStack(Items.BARREL));
        terminal.detectClicked(actor);
        check(number("totalCount") == 2, "EntityPlaceEvent fixture did not detect both real containers");
        var pending = (List<?>) value("pending");
        check(pending.size() == 2 && pending.contains(FIRST) && pending.contains(FIRST.east()),
                "EntityPlaceEvent fixture contains unexpected targets");
        var protectedPos = (BlockPos) pending.get(0);
        var nextPos = (BlockPos) pending.get(1);
        check(world.getBlockEntity(protectedPos) instanceof BarrelBlockEntity, "Protected target has no real barrel BE");
        var originalBarrel = (BarrelBlockEntity) world.getBlockEntity(protectedPos);
        originalBarrel.setCustomName(Component.literal("claim_entity_place_original"));
        originalBarrel.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var taggedItem = new ItemStack(Items.PAPER, 3);
        taggedItem.getOrCreateTag().putString("ClaimProtectionPayload", "retain-container-item-nbt");
        originalBarrel.setItem(18, taggedItem);
        originalBarrel.setChanged();
        var originalTag = originalBarrel.saveWithFullMetadata().copy();
        var inventoryBefore = actor.getInventory().items.stream().map(stack -> stack.serializeNBT().toString()).toList();
        int materialBefore = count(actor, Items.GOLD_BLOCK);
        int[] breakEvents = new int[2];
        int[] placeEvents = new int[2];
        Consumer<BlockEvent.BreakEvent> observeBreak = event -> {
            if (event.getLevel() != world || event.getPlayer() != actor) return;
            int target = event.getPos().equals(protectedPos) ? 0 : event.getPos().equals(nextPos) ? 1 : -1;
            if (target < 0) return;
            check(!event.isCanceled(), "Real terminal BreakEvent was not allowed before placement");
            check(event.getState().equals(originalState), "BreakEvent lost the original container block state");
            breakEvents[target]++;
        };
        Consumer<BlockEvent.EntityPlaceEvent> protectPlacement = event -> {
            if (event.getLevel() != world || event.getEntity() != actor) return;
            int target = event.getPos().equals(protectedPos) ? 0 : event.getPos().equals(nextPos) ? 1 : -1;
            if (target < 0) return;
            check(!event.isCanceled(), "EntityPlaceEvent was already denied outside the fixture");
            check(breakEvents[target] == 1, "Actual EntityPlaceEvent did not follow the allowed terminal BreakEvent");
            check(event.getPlacedBlock().is(Blocks.GOLD_BLOCK), "Terminal placement event has the wrong replacement");
            check(event.getBlockSnapshot().getReplacedBlock().equals(originalState),
                    "Terminal placement snapshot lost the original container state");
            placeEvents[target]++;
            if (target == 0) event.setCanceled(true);
        };
        try {
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.BreakEvent.class, observeBreak);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.EntityPlaceEvent.class, protectPlacement);
            terminal.replaceClicked(actor);
            check(flag("replacing"), "EntityPlaceEvent protection fixture could not start the real terminal");
            tick(1);
            check(breakEvents[0] == 1 && placeEvents[0] == 1 && breakEvents[1] == 0 && placeEvents[1] == 0,
                    "Protected operation did not execute the actual break and placement event path exactly once");
            check(world.getBlockState(protectedPos).equals(originalState), "Canceled placement changed the original block state");
            check(world.getBlockEntity(protectedPos) instanceof BarrelBlockEntity restored
                    && originalTag.equals(restored.saveWithFullMetadata()),
                    "Canceled placement changed the original BE type, full metadata or stored item NBT");
            check(world.getBlockState(nextPos).equals(originalState), "Canceled operation advanced the next target early");
            check(inventoryBefore.equals(actor.getInventory().items.stream().map(stack -> stack.serializeNBT().toString()).toList()),
                    "EntityPlaceEvent protection debited or mutated the initiator backpack");
            check(count(actor, Items.GOLD_BLOCK) == materialBefore && count(actor, Items.BARREL) == 0,
                    "Canceled placement charged replacement material or credited the old block");
            check(number("doneCount") == 0 && number("skippedCount") == 1 && number("queueIndex") == 1
                    && text("resultKey").equals("protected"), "EntityPlaceEvent cancellation was not counted as PROTECTED");
            check(flag("replacing"), "EntityPlaceEvent rejection stopped the next permitted operation");
            tick(1);
            check(breakEvents[1] == 1 && placeEvents[1] == 1,
                    "Next permitted operation did not execute the real terminal event path");
            check(world.getBlockState(nextPos).is(Blocks.GOLD_BLOCK) && world.getBlockEntity(nextPos) == null,
                    "Next permitted operation did not commit its replacement");
            check(number("doneCount") == 1 && number("skippedCount") == 1 && !flag("replacing")
                    && text("resultKey").equals("protected"), "Recovery lost the protected rejection statistics or queue completion");
            check(count(actor, Items.GOLD_BLOCK) == materialBefore - 1 && count(actor, Items.BARREL) == 1,
                    "Next permitted operation did not charge and return blocks exactly once");
            check(world.getBlockState(protectedPos).equals(originalState)
                    && world.getBlockEntity(protectedPos) instanceof BarrelBlockEntity restored
                    && originalTag.equals(restored.saveWithFullMetadata()),
                    "Next permitted operation corrupted the previously protected container");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(protectPlacement);
            MinecraftForge.EVENT_BUS.unregister(observeBreak);
            if (world.getBlockEntity(protectedPos) instanceof BarrelBlockEntity restored) restored.clearContent();
        }
        log("entity_place_protected_rollback_nbt_inventory_and_next_operation=OK");
    }

    private static void testAeNetworks() throws Exception {
        reset(3);
        actor.getInventory().clearContent();
        owner.getInventory().clearContent();
        var mode = appeng.api.config.Actionable.MODULATE;
        var gold = appeng.api.stacks.AEItemKey.of(Items.GOLD_BLOCK);
        var emerald = appeng.api.stacks.AEItemKey.of(Items.EMERALD_BLOCK);
        appeng.api.storage.MEStorage[] networks = new appeng.api.storage.MEStorage[2];
        ServerPlayer[] players = {actor, owner};
        for (int i = 0; i < ACCESS_POINTS.length; i++) {
            var player = players[i];
            var access = (appeng.blockentity.networking.WirelessAccessPointBlockEntity)
                    player.serverLevel().getBlockEntity(ACCESS_POINTS[i]);
            check(access.isActive(), "Real AE wireless access point inactive: " + i);
            networks[i] = access.getGrid().getStorageService().getInventory();
            var wireless = appeng.core.definitions.AEItems.WIRELESS_TERMINAL.stack();
            appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(wireless,
                    net.minecraft.core.GlobalPos.of(player.level().dimension(), ACCESS_POINTS[i]));
            ((appeng.items.tools.powered.WirelessTerminalItem) wireless.getItem()).injectAEPower(wireless, 10000, mode);
            player.getInventory().add(wireless);
            check(org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver.find(player, player.level())
                    == access.getGrid(), "Actual wireless resolver did not select player network: " + i);
            check(networks[i].insert(gold, 16, mode, appeng.api.networking.security.IActionSource.ofPlayer(player)) == 16,
                    "Cannot insert replacement into AE fixture");
        }
        check(networks[0] != networks[1], "Fixture accidentally connected both AE networks");
        terminal.toggleAeMode(actor);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        check(flag("replacing"), "AE teammate operation did not start");
        tick(3);
        check(number("doneCount") == 3 && !flag("replacing"), "AE replacement did not complete");
        check(networks[0].getAvailableStacks().get(gold) == 13 && networks[0].getAvailableStacks().get(emerald) == 3,
                "Initiator AE network material accounting");
        check(networks[1].getAvailableStacks().get(gold) == 16 && networks[1].getAvailableStacks().get(emerald) == 0,
                "Replacement used the bound owner's AE network");
        check(count(actor, Items.GOLD_BLOCK) == 0 && count(actor, Items.EMERALD_BLOCK) == 0,
                "AE mode incorrectly used backpack");
        actor.serverLevel().setBlock(FIRST, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
        terminal.detectClicked(actor);
        terminal.replaceClicked(actor);
        networks[0].extract(gold, Long.MAX_VALUE, mode, appeng.api.networking.security.IActionSource.ofPlayer(actor));
        tick(1);
        check(!flag("replacing") && text("resultKey").equals("insufficient")
                && actor.serverLevel().getBlockState(FIRST).is(Blocks.EMERALD_BLOCK), "AE shortage destroyed target");
        terminal.toggleAeMode(actor);
        log("two_independent_real_AE_networks_wireless_resolver_and_shortage=OK");
    }

    public static void cleanup() {
        GTLConfig.CLAIM_REPLACEMENT_INTERVAL_TICKS.set(previousInterval);
        if (terminal != null && actor != null) terminal.stopClicked(actor);
        if (online != null) for (var player : new RecordingPlayer[]{owner, actor, outsider}) {
            if (player != null) online.remove(player.getUUID());
        }
    }

    private static void reset(int targets) {
        terminal.stopClicked(actor);
        owner.getInventory().clearContent();
        actor.getInventory().clearContent();
        actor.getInventory().add(new ItemStack(Items.GOLD_BLOCK, 16));
        slots.get(0).getHandler().set(new ItemStack(Items.GOLD_BLOCK));
        slots.get(1).getHandler().set(new ItemStack(Items.EMERALD_BLOCK));
        for (int i = 0; i < 4; i++) actor.serverLevel().setBlock(FIRST.offset(i, 0, 0),
                (i < targets ? Blocks.EMERALD_BLOCK : Blocks.AIR).defaultBlockState(), 3);
    }

    private static void tick(int count) throws Exception {
        var tick = ClaimReplacementTerminalMachine.class.getDeclaredMethod("tickReplace");
        tick.setAccessible(true);
        for (int i = 0; i < count; i++) tick.invoke(terminal);
    }

    private static Object value(String name) throws Exception {
        var field = ClaimReplacementTerminalMachine.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(terminal);
    }
    private static boolean flag(String name) throws Exception { return (boolean) value(name); }
    private static int number(String name) throws Exception { return (int) value(name); }
    private static String text(String name) throws Exception { return (String) value(name); }
    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    @SuppressWarnings("unchecked")
    static PlayerTeam personalTeam(TeamManagerImpl manager, ServerPlayer player) throws Exception {
        var create = TeamManagerImpl.class.getDeclaredMethod("createPlayerTeam", UUID.class, String.class);
        create.setAccessible(true);
        var team = (PlayerTeam) create.invoke(manager, player.getUUID(), player.getGameProfile().getName());
        manager.getTeamMap().put(team.getId(), team);
        var known = TeamManagerImpl.class.getDeclaredField("knownPlayers");
        known.setAccessible(true);
        ((Map<UUID, PlayerTeam>) known.get(manager)).put(player.getUUID(), team);
        return team;
    }

    @SuppressWarnings("unchecked")
    static Map<UUID, ServerPlayer> onlinePlayers(MinecraftServer server) throws Exception {
        for (Field field : PlayerList.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())
                    && field.getGenericType().getTypeName().contains("java.util.UUID")
                    && field.getGenericType().getTypeName().contains("ServerPlayer")) {
                field.setAccessible(true);
                return (Map<UUID, ServerPlayer>) field.get(server.getPlayerList());
            }
        }
        throw new IllegalStateException("Cannot locate server player UUID index");
    }

    private static final class RecordingPlayer extends ServerPlayer {
        final List<String> messages = new ArrayList<>();

        RecordingPlayer(MinecraftServer server, String name) {
            super(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
            connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), this);
            setPos(322.5, 90.5, 4.5);
        }

        @Override public void sendSystemMessage(Component message) {
            messages.add(Component.Serializer.toJson(message));
        }
        @Override public void displayClientMessage(Component message, boolean overlay) {
            messages.add(Component.Serializer.toJson(message));
        }
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new IllegalStateException(reason);
        checks++;
    }
    private static void log(String message) {
        com.gtl.enhancedcore.GTLEnhancedcore.LOGGER.info("[CLAIM_AUDIT] {}", message);
    }
}
