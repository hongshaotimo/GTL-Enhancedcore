package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.gtl.enhancedcore.GTLEnhancedcore;
import com.gtl.enhancedcore.common.data.GTLEnhancedcoreItems;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotNetwork;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotStore;
import com.gtl.enhancedcore.common.world.personalplot.PersonalPlotWorlds;
import com.gtl.enhancedcore.common.world.plot.PlotChunkGenerator;
import com.gtl.enhancedcore.common.world.plot.PlotSettings;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Opt-in legacy ZIP and existing chunk restart checks, restricted to a newly created fixture. */
@Mod("enhancedcore_audit")
@Mod.EventBusSubscriber(modid = "enhancedcore_audit")
public final class PersonalPlotUpgradeRuntimeChecks {
    private static final UUID OWNER = UUID.fromString("d791918a-9694-400e-b89a-0d598a931af7");
    private static final String ARCHIVE_SHA = "5657c7971d26bae9aa16a72eb78881cfcd6c7a5e203896985499dcff68003416";
    private static final BlockPos MARKER = new BlockPos(82, -52, 82);
    private static final BlockPos CHEST = MARKER.east();
    private static int ticks, step, checks;
    private static boolean finished;
    public PersonalPlotUpgradeRuntimeChecks() {}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("gtl.personalPlotUpgrade.audit") || finished
                || event.phase != TickEvent.Phase.END || ++ticks % 30 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path fixture = Path.of(System.getProperty("gtl.personalPlotUpgrade.fixture", "")).toAbsolutePath().normalize();
        if (!root.equals(fixture.resolve("audit-world"))
                || !fixture.toString().contains("personal-plot-upgrade-20261102")
                || !Files.isRegularFile(fixture.resolve("personal-plot-upgrade.marker"))) {
            finished = true; throw new IllegalStateException("Personal plot upgrade audit refused non-fixture world");
        }
        String phase = System.getProperty("gtl.personalPlotUpgrade.phase", "");
        try {
            check(phase.equals("seed") || phase.equals("verify"), "Explicit isolated audit phase");
            ServerLevel personal = server.getLevel(PersonalPlotWorlds.key(OWNER));
            ServerPlayer player = FakePlayerFactory.get(server.overworld(), new GameProfile(OWNER, "LegacyPlotAudit"));
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(GTLEnhancedcoreItems.PLOT_WORLD_CONTROLLER.get()));
            if (step == 0) {
                check(server.isDedicatedServer(), "Real dedicated Forge server");
                check(!player.hasPermissions(2) && !server.getPlayerList().isOp(player.getGameProfile()), "Owner is a non-OP player");
                check(personal != null, "Old v1 dimension ZIP loaded through normal Forge world startup");
                var snapshot = PersonalPlotWorlds.snapshot(player, "");
                check(snapshot.state() == PersonalPlotWorlds.State.READY, "Legacy owner snapshot is READY");
                check(!snapshot.canCreatePersonal() && !snapshot.canEditTemplate(), "Existing non-OP owner cannot recreate or edit template");
                PlotSettings expected = new PlotSettings(5, "minecraft:light_gray_concrete", "minecraft:black_concrete",
                        "minecraft:light_gray_concrete", "minecraft:sea_lantern", PlotSettings.RoadStyle.BOULEVARD,
                        4, PlotSettings.MarkerMode.PLOT, 0, -64, -53, "minecraft:white_concrete",
                        PlotSettings.LineStyle.CROSSWALK, 3, 3, 2);
                check(snapshot.settings().equals(expected), "All original v1 fields restored with white/zero edge defaults");
                check(personal.getChunkSource().getGenerator() instanceof PlotChunkGenerator generator
                        && generator.settings().equals(expected), "Live generator and restored owner settings agree");
                check(PersonalPlotWorlds.isRegisteredPersonalLevel(personal), "Restored owner recognized by personal dimension bridge");
                check(PersonalPlotStore.read(root).worlds().size() == 1, "Exactly one original owner exists");
                check(personal.getBlockState(new BlockPos(5, -64, 5)).is(Blocks.BEDROCK), "Legacy generator keeps bedrock height");
                check(personal.getBlockState(new BlockPos(5, -54, 5)).is(Blocks.LIGHT_GRAY_CONCRETE), "Legacy generator keeps concrete foundation");
                if (phase.equals("seed")) {
                    personal.setBlock(MARKER, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                    personal.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), 3);
                    ((Container) personal.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.EMERALD, 23));
                    for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++)
                        server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
                }
                checkMarkers(personal);
                checkArchive(root);
                player.setPos(8.5, 81, 8.5);
            } else if (step == 1) {
                check(PersonalPlotWorlds.handle(player, PersonalPlotNetwork.Action.TRAVEL, null).endsWith("travelled"), "Non-OP enters existing private world");
                check(player.level() == personal, "Teleport changed the real server player level");
                checkMarkers(personal);
            } else if (step == 2) {
                check(PersonalPlotWorlds.handle(player, PersonalPlotNetwork.Action.RETURN, null).endsWith("returned"), "Non-OP returns through saved departure point");
                check(player.level() == server.overworld() && player.blockPosition().equals(new BlockPos(8, 81, 8)), "Return restores original dimension and position");
            } else if (step == 3) {
                check(PersonalPlotWorlds.handle(player, PersonalPlotNetwork.Action.CREATE, null).endsWith("already_owned"), "Existing legacy UUID cannot create another world");
            } else {
                checkMarkers(personal); checkArchive(root);
                server.saveEverything(true, true, true);
                checkArchive(root);
                JsonObject report = new JsonObject();
                report.addProperty("status", "PASS"); report.addProperty("phase", phase); report.addProperty("assertions", checks);
                report.addProperty("archiveSha256", ARCHIVE_SHA); report.addProperty("owner", OWNER.toString());
                report.addProperty("boundary", "Real Forge startup and FakePlayer server teleport; no player world opened, no client acknowledgement tested. Seeded fixture chunk and chest survive independent JVM restart.");
                Files.writeString(fixture.resolve("report-" + phase + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
                finished = true; server.halt(false);
            }
            step++;
        } catch (Throwable error) {
            finished = true; GTLEnhancedcore.LOGGER.error("[PERSONAL_PLOT_UPGRADE] FAIL", error);
            JsonObject report = new JsonObject(); report.addProperty("status", "FAIL"); report.addProperty("phase", phase);
            report.addProperty("assertions", checks); report.addProperty("error", error.toString());
            try { Files.writeString(fixture.resolve("report-" + phase + ".json"), report.toString()); }
            catch (Exception suppressed) { error.addSuppressed(suppressed); }
            server.halt(false);
        }
    }
    private static void checkMarkers(ServerLevel level) {
        check(level.getBlockState(MARKER).is(Blocks.DIAMOND_BLOCK), "Existing fixture chunk marker remains diamond");
        check(level.getBlockEntity(CHEST) instanceof Container inventory && inventory.getItem(0).is(Items.EMERALD)
                && inventory.getItem(0).getCount() == 23, "Existing block entity inventory remains exactly 23 emeralds");
    }
    private static void checkArchive(Path root) throws Exception {
        byte[] bytes = Files.readAllBytes(PersonalPlotStore.packPath(root, OWNER));
        check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(ARCHIVE_SHA), "Original legacy ZIP bytes unchanged");
    }
    private static void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++; GTLEnhancedcore.LOGGER.info("[PERSONAL_PLOT_UPGRADE] OK {}", description);
    }
}
