package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotDeletion;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotManagement;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork.ManagementAction;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork.ManagementSnapshot;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Real dedicated Forge lifecycle against a fresh fixture; simulated players have no client connection. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class PersonalPlotManagementRuntimeChecks {
    private static final UUID ALICE = UUID.fromString("ac000001-0000-4000-8000-000000000001");
    private static final UUID BOB = UUID.fromString("ac000002-0000-4000-8000-000000000002");
    private static final UUID OP = UUID.fromString("ac000003-0000-4000-8000-000000000003");
    private static final UUID OFFLINE = UUID.fromString("ac000004-0000-4000-8000-000000000004");
    private static final UUID VISITOR = UUID.fromString("ac000005-0000-4000-8000-000000000005");
    private static final PlotSettings ORIGINAL = PlotSettings.preset(3).withGroundBlock("minecraft:light_blue_concrete");
    private static final BlockPos MARKER = new BlockPos(10, ORIGINAL.surfaceY() + 1, 10), CHEST = MARKER.east();
    private static final BlockPos REPLACEMENT = MARKER.north(), OVERWORLD = new BlockPos(20, 81, 20);
    private static final List<String> CHECKS = new ArrayList<>();
    private static int ticks, stage, waitTicks, expiresAt;
    private static boolean finished;
    private static UUID token, staleToken;
    private static ServerPlayer alice, bob, operator, visitor;
    private static String originalOwnerSha;
    public PersonalPlotManagementRuntimeChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("gtl.personalPlotManagement.audit") || finished || event.phase != TickEvent.Phase.END || ++ticks % 10 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer(); if (server == null) return;
        Path fixture = Path.of(System.getProperty("gtl.personalPlotManagement.fixture", "")).toAbsolutePath().normalize();
        Path expected = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/personal-plot-management-20261105").toAbsolutePath().normalize();
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String phase = System.getProperty("gtl.personalPlotManagement.phase", "");
        try {
            checkGuard(fixture.getParent().equals(expected) && root.equals(fixture.resolve("audit-world"))
                    && Files.isRegularFile(fixture.resolve("personal-plot-management-server.marker")), "Refused non-fixture world");
            if (ticks > 9000) throw new AssertionError("Management lifecycle exceeded 9000 ticks");
            if (alice == null) {
                alice = player(server, ALICE, "AuditAlice"); bob = player(server, BOB, "AuditBob"); operator = player(server, OP, "AuditOperator");
                server.getPlayerList().op(operator.getGameProfile());
                check(server.isDedicatedServer() && !alice.hasPermissions(2), "Real dedicated server and ordinary player authority");
            }
            if (phase.equals("template")) {
                check(PersonalPlotWorlds.handle(operator, PersonalPlotNetwork.Action.TEMPLATE, ORIGINAL).endsWith("template_saved"), "Server template saves through production action");
                check(!PersonalPlotWorlds.snapshot(alice, "").templateReady(), "Saved template requires independent process restart");
                var offline = new CompoundTag(); offline.putUUID("UUID", OFFLINE); offline.putString("audit", "offline-playerdata-preserved");
                Path data = root.resolve("playerdata").resolve(OFFLINE + ".dat"); Files.createDirectories(data.getParent()); NbtIo.writeCompressed(offline, data.toFile());
                Files.writeString(fixture.resolve("offline-playerdata.sha256"), sha(data));
                for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                server.overworld().setDefaultSpawnPos(new BlockPos(0, 81, 0), 0);
                server.overworld().setBlock(OVERWORLD, Blocks.CHEST.defaultBlockState(), 3);
                ((Container) server.overworld().getBlockEntity(OVERWORLD)).setItem(0, new ItemStack(Items.NETHER_STAR, 11));
                finish(server, fixture, phase); return;
            }
            if (phase.equals("seed")) {
                switch (stage) {
                    case 0 -> {
                        check(PersonalPlotWorlds.snapshot(alice, "").templateReady(), "Independent restart activates shared template before seeding");
                        notice(manage(alice, ManagementAction.CREATE_FOR, ALICE, null), "created_ready", "Ordinary dedicated player creates own live world");
                        seed(level(server, ALICE));
                    }
                    case 1 -> {
                        notice(manage(bob, ManagementAction.CREATE_FOR, BOB, null), "created_ready", "Second ordinary owner creates an independent protected world");
                        seed(level(server, BOB));
                        Files.writeString(fixture.resolve("protected-pack.sha256"), sha(PersonalPlotStore.packPath(root, BOB)));
                    }
                    case 2 -> {
                        check(server.getPlayerList().getPlayer(OFFLINE) == null, "Known offline UUID has no online player entity before OP creation");
                        notice(manage(operator, ManagementAction.CREATE_FOR, OFFLINE, null), "created_ready", "OP creates live dimension for known offline playerdata UUID");
                        seed(level(server, OFFLINE));
                    }
                    case 3 -> { protectedContents(server, root, fixture); finish(server, fixture, phase); return; }
                    default -> throw new AssertionError("Unknown seed stage " + stage);
                }
                stage++; return;
            }
            if (phase.equals("verify")) {
                check(PersonalPlotWorlds.snapshot(alice, "").state() == PersonalPlotWorlds.State.READY, "Recreated owner loads after independent JVM restart");
                ServerLevel owner = level(server, ALICE);
                check(owner.getBlockState(MARKER).isAir() && owner.getBlockEntity(CHEST) == null, "Deleted original chunk marker and chest do not resurrect on restart");
                check(owner.getBlockState(REPLACEMENT).is(Blocks.EMERALD_BLOCK), "Replacement chunk content survives save and restart");
                check(server.getLevel(PersonalPlotWorlds.key(OFFLINE)) == null && !PersonalPlotStore.read(root).worlds().containsKey(OFFLINE), "Deleted offline owner has no loaded dimension or owner record after restart");
                check(!Files.exists(PersonalPlotStore.packPath(root, OFFLINE)) && !Files.exists(PersonalPlotStore.dimensionDirectory(root, OFFLINE)), "Deleted offline archive and chunk directory stay absent after restart");
                check(PersonalPlotStore.deletions(root).contains(OFFLINE) && !PersonalPlotStore.deletions(root).contains(ALICE), "Deletion tombstone persists only for the deleted owner");
                protectedContents(server, root, fixture); check(PersonalPlotStore.read(root).worlds().size() == 2, "Only recreated and protected owner records remain");
                finish(server, fixture, phase); return;
            }
            if (!phase.equals("lifecycle")) throw new AssertionError("Unknown lifecycle phase: " + phase);
            switch (stage) {
                case 0 -> {
                    check(PersonalPlotWorlds.snapshot(alice, "").templateReady(), "Independent restart activates shared template");
                    server.saveEverything(true, true, true);
                    var dimensions = NbtIo.readCompressed(root.resolve("level.dat").toFile()).getCompound("Data")
                            .getCompound("WorldGenSettings").getCompound("dimensions");
                    for (UUID owner : List.of(ALICE, BOB, OFFLINE)) {
                        check(level(server, owner).getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "Seeded owner chunk loaded from independent JVM: " + owner);
                        check(server.getResourceManager().listPacks().anyMatch(pack -> pack.packId().equals(PersonalPlotStore.packId(owner))),
                                "Owner ZIP is held by the running resource manager before deletion: " + owner);
                        check(dimensions.contains(PersonalPlotWorlds.key(owner).location().toString()), "Seeded dimension stem exists in saved level.dat before deletion: " + owner);
                    }
                    check(PersonalPlotStore.read(root).worlds().size() == 3, "All three saved owner definitions load before lifecycle deletion");
                    originalOwnerSha = sha(PersonalPlotStore.packPath(root, ALICE));
                }
                case 1 -> protectedContents(server, root, fixture);
                case 2 -> notice(manage(alice, ManagementAction.PREPARE_DELETE, BOB, null), "management_denied", "Non-OP cannot request another owner's deletion challenge");
                case 3 -> notice(manage(alice, ManagementAction.CREATE_FOR, OFFLINE, null), "management_denied", "Non-OP cannot create another known owner's world");
                case 4 -> {
                    token = challenge(alice, ALICE); expiresAt = ticks + 1210;
                    check(server.getPlayerList().getPlayer(OFFLINE) == null, "Known offline owner has no online player entity");
                }
                case 5 -> {
                    check(level(server, OFFLINE).getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "Offline owner's saved chunk is present before OP deletion");
                    check(server.getPlayerList().getPlayer(OFFLINE) == null, "Offline owner remains disconnected while OP manages the loaded dimension");
                }
                case 6 -> staleToken = challenge(operator, OFFLINE);
                case 7 -> notice(manage(operator, ManagementAction.CONFIRM_DELETE, OFFLINE, staleToken), "delete_started", "OP confirms known offline owner's deletion");
                case 8 -> { if (!deleted(server, root, OFFLINE)) { waitForCleanup(); return; } check(true, "Offline owner asynchronous archive and region cleanup completes"); }
                case 9 -> {
                    if (ticks < expiresAt) return;
                    notice(manage(alice, ManagementAction.CONFIRM_DELETE, ALICE, token), "confirmation_expired", "Nonce expires after real 1200-tick server deadline");
                    check(level(server, ALICE).getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "Expired nonce cannot delete original world content");
                }
                case 10 -> staleToken = challenge(alice, ALICE);
                case 11 -> token = challenge(alice, ALICE);
                case 12 -> notice(manage(alice, ManagementAction.CONFIRM_DELETE, ALICE, staleToken), "confirmation_expired", "Replacing a challenge rejects its stale nonce");
                case 13 -> notice(manage(alice, ManagementAction.CONFIRM_DELETE, ALICE, token), "confirmation_expired", "Failed stale confirmation consumes outstanding challenge and prevents replay");
                case 14 -> {
                    token = challenge(alice, ALICE);
                    visitor = player(server, VISITOR, "AuditVisitor"); visitor.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
                    visitor.getPersistentData().putString("gtlEnhancedcorePersonalPlotReturn", "audit-stale-return");
                    visitor.teleportTo(level(server, ALICE), 8.5, ORIGINAL.surfaceY() + 1, 8.5, 0, 0);
                    registerVisitor(server, visitor, true);
                    check(level(server, ALICE).players().contains(visitor) && server.getPlayerList().getPlayers().contains(visitor), "Another simulated online player occupies the actual target dimension");
                }
                case 15 -> {
                    Consumer<EntityTravelToDimensionEvent> veto = event1 -> { if (event1.getEntity() == visitor) event1.setCanceled(true); };
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, EntityTravelToDimensionEvent.class, veto);
                    try { notice(manage(alice, ManagementAction.CONFIRM_DELETE, ALICE, token), "travel_cancelled", "Forge evacuation veto aborts deletion"); }
                    finally { MinecraftForge.EVENT_BUS.unregister(veto); }
                    check(visitor.level() == level(server, ALICE) && PersonalPlotStore.read(root).worlds().containsKey(ALICE)
                            && sha(PersonalPlotStore.packPath(root, ALICE)).equals(originalOwnerSha), "Evacuation veto preserves target player, owner definition and archive bytes");
                }
                case 16 -> token = challenge(alice, ALICE);
                case 17 -> {
                    notice(manage(alice, ManagementAction.CONFIRM_DELETE, ALICE, token), "delete_started", "Ordinary owner confirms self deletion");
                    check(visitor.level() == server.overworld() && visitor.getInventory().getItem(0).is(Items.DIAMOND)
                            && visitor.getInventory().getItem(0).getCount() == 7, "Deletion safely evacuates another online player and preserves their inventory");
                    check(!visitor.getPersistentData().contains("gtlEnhancedcorePersonalPlotReturn"), "Evacuation clears stale return dimension data");
                    check(!PersonalPlotStore.read(root).worlds().containsKey(ALICE), "Deletion hides original owner record before background cleanup");
                }
                case 18 -> notice(manage(alice, ManagementAction.CONFIRM_DELETE, ALICE, token), "confirmation_expired", "Used destructive nonce cannot be replayed");
                case 19 -> { if (!deleted(server, root, ALICE)) { waitForCleanup(); return; } check(true, "Self deletion asynchronous archive and region cleanup completes"); }
                case 20 -> {
                    notice(manage(alice, ManagementAction.CREATE_FOR, ALICE, null), "created_ready", "Ordinary owner recreates personal world after deletion finishes");
                    ServerLevel owner = level(server, ALICE);
                    check(owner.getChunkSource().getGenerator() instanceof PlotChunkGenerator generator && generator.settings().equals(ORIGINAL), "Replacement uses exact active server template");
                    check(owner.getBlockState(MARKER).isAir() && owner.getBlockEntity(CHEST) == null, "Newly generated replacement has no deleted marker or inventory");
                    check(owner.getBlockState(new BlockPos(5, ORIGINAL.surfaceY() - 1, 5)).is(Blocks.LIGHT_BLUE_CONCRETE), "Replacement generates expected concrete terrain");
                    owner.setBlock(REPLACEMENT, Blocks.EMERALD_BLOCK.defaultBlockState(), 3);
                }
                case 21 -> {
                    protectedContents(server, root, fixture);
                    registerVisitor(server, visitor, false);
                    finish(server, fixture, phase); return;
                }
                default -> throw new AssertionError("Unknown lifecycle stage " + stage);
            }
            stage++; waitTicks = 0;
        } catch (Throwable failure) {
            finished = true; GTLEnhancedcore.LOGGER.error("[PERSONAL_PLOT_MANAGEMENT] FAIL phase={} stage={}", phase, stage, failure);
            try { writeReport(fixture, phase, "FAIL", failure.toString()); } catch (Exception ignored) { failure.printStackTrace(); }
            server.halt(false);
        }
    }
    private static ServerPlayer player(MinecraftServer server, UUID owner, String name) {
        var player = FakePlayerFactory.get(server.overworld(), new GameProfile(owner, name));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GTLEnhancedcoreItems.PLOT_WORLD_CONTROLLER.get())); return player;
    }
    private static void registerVisitor(MinecraftServer server, ServerPlayer player, boolean present) {
        // Forge exposes an unmodifiable online view; this audit has no socket login to populate its backing state.
        var playerList = server.getPlayerList();
        List<ServerPlayer> players = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.players.PlayerList.class, playerList, "f_11196_");
        Map<UUID, ServerPlayer> byUuid = net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.players.PlayerList.class, playerList, "f_11197_");
        if (present) { players.add(player); byUuid.put(player.getUUID(), player); }
        else { players.remove(player); byUuid.remove(player.getUUID(), player); }
    }
    private static ManagementSnapshot manage(ServerPlayer actor, ManagementAction action, UUID owner, UUID token) { return PersonalPlotManagement.handle(actor, action, owner, token, 0); }
    private static UUID challenge(ServerPlayer actor, UUID owner) {
        var snapshot = manage(actor, ManagementAction.PREPARE_DELETE, owner, null);
        check(owner.equals(snapshot.deleteOwner()) && snapshot.deleteToken() != null, "Server issues owner-bound deletion nonce to " + actor.getGameProfile().getName()); return snapshot.deleteToken();
    }
    private static void notice(ManagementSnapshot snapshot, String suffix, String description) { check(snapshot.notice().endsWith("." + suffix), description + " [" + snapshot.notice() + "]"); }
    private static ServerLevel level(MinecraftServer server, UUID owner) { var level = server.getLevel(PersonalPlotWorlds.key(owner)); if (level == null) throw new AssertionError("Missing audit dimension: " + owner); return level; }
    private static void seed(ServerLevel level) {
        level.setBlock(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3); level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
        ((Container) level.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.EMERALD, 23));
    }
    private static boolean deleted(MinecraftServer server, Path root, UUID owner) {
        return !PersonalPlotDeletion.isDeleting(server, owner) && server.getLevel(PersonalPlotWorlds.key(owner)) == null
                && !Files.exists(PersonalPlotStore.packPath(root, owner)) && !Files.exists(PersonalPlotStore.dimensionDirectory(root, owner));
    }
    private static void waitForCleanup() { if ((waitTicks += 10) > 2500) throw new AssertionError("Background deletion did not finish within 2500 ticks"); }
    private static void protectedContents(MinecraftServer server, Path root, Path fixture) throws Exception {
        ServerLevel other = level(server, BOB);
        check(other.getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK) && other.getBlockEntity(CHEST) instanceof Container contents
                && contents.getItem(0).is(Items.EMERALD) && contents.getItem(0).getCount() == 23, "Other owner's chunk and original inventory remain unchanged");
        check(sha(PersonalPlotStore.packPath(root, BOB)).equals(Files.readString(fixture.resolve("protected-pack.sha256"))), "Other owner archive SHA-256 is unchanged");
        check(server.overworld().getBlockEntity(OVERWORLD) instanceof Container contents && contents.getItem(0).is(Items.NETHER_STAR)
                && contents.getItem(0).getCount() == 11, "Original overworld chest and eleven nether stars remain unchanged");
        check(sha(root.resolve("playerdata").resolve(OFFLINE + ".dat")).equals(Files.readString(fixture.resolve("offline-playerdata.sha256"))), "Offline owner playerdata SHA-256 remains unchanged");
    }
    private static String sha(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static void checkGuard(boolean value, String description) { if (!value) throw new IllegalStateException(description); }
    private static void check(boolean value, String description) { if (!value) throw new AssertionError(description); CHECKS.add(description); GTLEnhancedcore.LOGGER.info("[PERSONAL_PLOT_MANAGEMENT] OK {}", description); }
    private static void writeReport(Path fixture, String phase, String status, String failure) throws Exception {
        Files.writeString(fixture.resolve("report-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "status", status, "phase", phase, "stage", stage, "assertions", CHECKS.size(), "checks", CHECKS, "failure", failure,
                "boundary", "Real dedicated Forge startup, production management actions and asynchronous disk deletion across independent JVMs. Players are Forge FakePlayers; visitor is registered in actual level and online list. No real player worlds or socket/client acknowledgement tested.")));
    }
    private static void finish(MinecraftServer server, Path fixture, String phase) throws Exception {
        server.saveEverything(true, true, true); writeReport(fixture, phase, "PASS", ""); finished = true;
        GTLEnhancedcore.LOGGER.info("[PERSONAL_PLOT_MANAGEMENT] PASS phase={} assertions={}", phase, CHECKS.size()); server.halt(false);
    }
}
