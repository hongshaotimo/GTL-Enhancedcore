package com.gtl.enhancedcore.audit;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.GsonBuilder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.CloudMeNetworkAccess;
import com.gtl.enhancedcore.common.cloud.CloudPatternBufferMachine;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportJournal;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in disposable Forge fixture: real FTB membership and production menus/WAL/store operations. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class CloudTeamPermissionRuntimeChecks {
    private static final BlockPos POS = new BlockPos(480, 100, 480);
    private static final List<String> CHECKS = new ArrayList<>();
    private static boolean done;
    private static int ticks, step;
    private static ServerLevel level;
    private static Path directory;
    private static FakePlayer owner, member, captain, outsider;
    private static PartyTeam party;
    private static CloudPatternLibraryMachine main;
    private static CloudPatternBufferMachine child;
    private static CloudPatternStore store;
    private static CloudPatternStore.Library library;
    private static CloudPatternStore.Entry entry;
    private static ItemStack original;
    private static UUID upload;
    private CloudTeamPermissionRuntimeChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.cloudTeamPermissionAudit")) return;
        try {
            if (++ticks > 600) throw new AssertionError("Cloud team audit timed out at step " + step);
            if (main != null) set(main, "isFormed", true);
            switch (step) {
                case 0 -> { setup(); step++; }
                case 1 -> {
                    if (get(main, "inboxUpload") != null) return;
                    entry = library.entries().stream().filter(e -> e.status() == CloudLedger.Status.AVAILABLE).findFirst().orElseThrow();
                    upload = library.receipts().stream().filter(r -> r.kind().equals("UPLOAD")).findFirst().orElseThrow().operation();
                    check(member.containerMenu.getCarried().isEmpty() && main.importInventory().getStackInSlot(0).isEmpty()
                                    && library.storedCount() == 1 && library.receipt(upload).status().equals("ACKNOWLEDGED"),
                            "Ordinary member cursor upload debits exactly one physical original through production WAL and ACK");
                    exerciseSharedState(); exerciseReview(); exerciseRevocation();
                    var payload = new CompoundTag(); payload.putString("selected", entry.id().toString());
                    main.cloudUiAction(member, "take_pattern", payload);
                    check(ItemStack.matches(member.containerMenu.getCarried(), original) && entry.status() == CloudLedger.Status.EXPORTED
                                    && library.storedCount() == 0,
                            "Ordinary member native menu withdrawal returns exactly one original");
                    store.checkpoint(); finish(null);
                }
                default -> throw new AssertionError("Unknown team audit step");
            }
        } catch (Throwable failure) { finish(failure); }
    }

    private static void setup() throws Exception {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(); level = server.overworld();
        directory = server.getServerDirectory().toPath().toRealPath();
        var root = Path.of(System.getProperty("gtl.enhancedcore.auditRoot")).toRealPath();
        check(directory.startsWith(root) && !directory.equals(root) && Files.isRegularFile(directory.resolve("team-permission.marker"))
                        && server.getWorldPath(LevelResource.ROOT).toRealPath().startsWith(directory),
                "Fresh marked disposable Forge world guards all fixture mutations");
        owner = player("CloudTeamOwner", 1); member = player("CloudTeamMember", 2);
        captain = player("CloudTeamCaptain", 3); outsider = player("CloudTeamOutsider", 4);
        var manager = (TeamManagerImpl)FTBTeamsAPI.api().getManager();
        for (var actor : List.of(owner, member, captain, outsider))
            manager.playerLoggedIn(actor, actor.getUUID(), actor.getGameProfile().getName());
        party = manager.createParty(captain, "CloudTeamPermissionFixture"); party.join(owner); party.join(member);
        manager.playerLoggedOut(owner);
        check(party.getRankForPlayer(captain.getUUID()) == TeamRank.OWNER
                        && party.getRankForPlayer(owner.getUUID()) == TeamRank.MEMBER
                        && party.getRankForPlayer(member.getUUID()) == TeamRank.MEMBER
                        && server.getPlayerList().getPlayer(owner.getUUID()) == null,
                "Real FTB party has an offline ordinary library owner, a distinct captain and an ordinary member");
        level.setChunkForced(POS.getX() >> 4, POS.getZ() >> 4, true); level.getChunkAt(POS);
        check(level.isEmptyBlock(POS) && level.isEmptyBlock(POS.east(2)), "Cloud machine positions are fixture-owned empty blocks");
        var definition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_library"));
        level.setBlockAndUpdate(POS, definition.defaultBlockState());
        main = (CloudPatternLibraryMachine)MetaMachine.getMachine(level, POS); main.onMachinePlaced(owner, definition.asStack());
        set(main, "isFormed", true); set(main, "uploadSourceLoaded", true);
        var childDefinition = GTRegistries.MACHINES.get(new ResourceLocation("gtl_enhancedcore", "cloud_pattern_buffer"));
        level.setBlockAndUpdate(POS.east(2), childDefinition.defaultBlockState());
        child = (CloudPatternBufferMachine)MetaMachine.getMachine(level, POS.east(2)); child.onMachinePlaced(owner, childDefinition.asStack());
        store = CloudPatternStore.get(level); library = store.library(main.libraryId());
        check(library.delegates().isEmpty() && library.authorized(member.getUUID()) && library.authorized(captain.getUUID()),
                "Real FTB captain and ordinary member both inherit the library without grants");
        for (var actor : List.of(owner, member, captain)) {
            check(main.shouldOpenUI(actor, InteractionHand.MAIN_HAND, null) && main.mayConfigureMeNetwork(actor)
                            && child.shouldOpenUI(actor, InteractionHand.MAIN_HAND, null) && child.mayConfigureMeNetwork(actor)
                            && !CloudMeNetworkAccess.denied(actor, main.getPos()) && !CloudMeNetworkAccess.denied(actor, child.getPos()),
                    actor.getGameProfile().getName() + " can open library/unbound child and pass wireless packet authorization");
        }
        check(!main.shouldOpenUI(outsider, InteractionHand.MAIN_HAND, null) && !child.shouldOpenUI(outsider, InteractionHand.MAIN_HAND, null)
                        && CloudMeNetworkAccess.denied(outsider, main.getPos()) && CloudMeNetworkAccess.denied(outsider, child.getPos()),
                "External team cannot open or configure either cloud machine");
        set(child, "libraryId", library.id());
        check(child.shouldOpenUI(member, InteractionHand.MAIN_HAND, null) && !child.shouldOpenUI(outsider, InteractionHand.MAIN_HAND, null),
                "Bound child uses the same dynamic team permission as the central library");
        member.setPos(POS.getX() + 20, POS.getY(), POS.getZ());
        check(!main.mayConfigureMeNetwork(member) && !child.mayConfigureMeNetwork(member), "Team authorization preserves eight-block distance limits");
        near(member); open(member);
        check(main.menuAuthorized(member), "Actual ordinary-member production ModularUI is authorized");
        original = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)});
        member.containerMenu.setCarried(original.copy()); main.cloudUiAction(member, "import_cursor", new CompoundTag());
    }

    private static void exerciseSharedState() {
        library.grant(member.getUUID(), captain.getUUID(), true); library.grant(member.getUUID(), captain.getUUID(), false);
        check(library.authorized(captain.getUUID()), "Grant/revoke by an ordinary member cannot revoke equal current teammate permissions");
        boolean rejected = false;
        try { library.grant(member.getUUID(), outsider.getUUID(), true); } catch (SecurityException expected) { rejected = true; }
        check(rejected && !library.authorized(outsider.getUUID()), "Grant cannot admit an external team");
        var shared = new CompoundTag(); shared.putString("audit", "team shared state");
        library.setCatalysts(member.getUUID(), shared);
        library.setMachineCatalysts(member.getUUID(), "gtceu:electric_blast_furnace", shared);
        entry.setCatalysts(member.getUUID(), shared);
        check(library.catalysts().equals(shared) && library.machineCatalysts("gtceu:electric_blast_furnace").equals(shared)
                        && entry.catalysts().equals(shared), "Ordinary member updates library, machine and original catalyst authority");
        entry.setCatalysts(member.getUUID(), new CompoundTag());
    }

    private static void exerciseReview() throws Exception {
        main.cloud$quarantineImport("Upload " + upload + " requires review");
        var index = get(store, "index"); set(index, "ioFailed", true);
        check(main.shouldOpenUI(member, InteractionHand.MAIN_HAND, null) && main.menuAuthorized(member)
                        && main.cloudUiSnapshot(member, "", "", 0, "").getBoolean("recoveryOwner")
                        && !main.shouldOpenUI(outsider, InteractionHand.MAIN_HAND, null),
                "I/O-failed retained authority recovery menu grants the same team rights and rejects external players");
        set(index, "ioFailed", false);
        CloudImportJournal.retry(level, library.id(), member, upload);
        check(!main.cloud$importQuarantined() && library.storedCount() == 1
                        && library.receipt(upload).status().equals("ACKNOWLEDGED"),
                "Ordinary member retries exact committed source evidence without re-debit or duplicate publication");
    }

    private static void exerciseRevocation() throws Exception {
        library.grant(member.getUUID(), member.getUUID(), true); party.leave(member.getUUID());
        check(!library.authorized(member.getUUID()) && !main.menuAuthorized(member)
                        && !child.mayConfigureMeNetwork(member), "Real FTB departure revokes an already-open menu and a saved delegate immediately");
        boolean rejected = false;
        try { entry.setCatalysts(member.getUUID(), new CompoundTag()); } catch (SecurityException expected) { rejected = true; }
        check(rejected, "Former member authority mutation fails before touching original state");
        party.join(member);
        check(main.menuAuthorized(member) && child.mayConfigureMeNetwork(member), "Real FTB rejoin restores equal rights to the same live machines");
    }

    private static FakePlayer player(String name, int suffix) {
        var actor = FakePlayerFactory.get(level, new GameProfile(new UUID(0x4c6f75645465616dL, suffix), name));
        actor.setGameMode(GameType.CREATIVE); near(actor); return actor;
    }
    private static void near(FakePlayer actor) { actor.setPos(POS.getX() + .5, POS.getY() + .5, POS.getZ() + .5); }
    private static void open(FakePlayer actor) {
        var ui = main.createUI(actor); ui.initWidgets(); actor.containerMenu = new ModularUIContainer(ui, 217);
    }
    private static java.lang.reflect.Field field(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { var value = type.getDeclaredField(name); value.setAccessible(true); return value; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object target, String name, Object value) throws Exception { field(target, name).set(target, value); }
    private static Object get(Object target, String name) throws Exception { return field(target, name).get(target); }
    private static void check(boolean value, String label) { if (!value) throw new AssertionError(label); CHECKS.add(label); }
    private static void finish(Throwable failure) {
        done = true; if (failure != null) failure.printStackTrace();
        var report = new LinkedHashMap<String, Object>(); report.put("status", failure == null ? "PASS" : "FAIL");
        report.put("checks", CHECKS); report.put("ticks", ticks); report.put("step", step);
        report.put("scope", "Real FTB party/rank/leave/rejoin/offline owner, production library/child authorization, ModularUI cursor upload and withdrawal, WAL ACK review, catalyst authority; controlled formed/lifecycle/I/O-failed flags in a fresh marked fixture");
        report.put("notCovered", List.of("Full multiblock formation", "Rendered client GUI", "Claimed-chunk policy changes", "Terminal and wireless real AE routing", "Network import/undo end-to-end", "Independent JVM restart"));
        if (failure != null) report.put("error", failure.toString());
        try {
            if (directory != null) Files.writeString(directory.resolve("cloud-team-permission.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception error) { error.printStackTrace(); }
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null && Boolean.getBoolean("gtl.enhancedcore.cloudTeamPermissionHalt")) server.halt(false);
    }
}
