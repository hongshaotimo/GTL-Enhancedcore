package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Slim isolated runtime entry, selected explicitly by tools/performance_client.py. */
@Mod("enhancedcore_audit")
public final class PerformanceClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/performance-20261102").toAbsolutePath().normalize();
    private boolean done;
    private int stage;
    private final List<String> checks = java.util.Collections.synchronizedList(new ArrayList<>());
    private final List<String> failures = new ArrayList<>();
    private volatile Throwable serverFailure;
    private volatile boolean worldDone;
    private boolean jeiDone;
    private boolean bloomWorldDone;
    private StructurePerformanceWorldChecks worldChecks;

    public PerformanceClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.performanceAudit")) return;
        var mc = Minecraft.getInstance();
        if (stage == 0 && (mc.getOverlay() != null || mc.screen == null)) return;
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (stage == 1) {
                if (!PreviewPerformanceChecks.tickVisual()) return;
                PreviewPerformanceChecks.finishVisual();
                if (Files.exists(directory.resolve("saves/performance-lifecycle")))
                    throw new AssertionError("Fresh isolated lifecycle world required");
                stage = 2;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("performance-lifecycle",
                        new LevelSettings("Performance Lifecycle Audit", GameType.CREATIVE, false,
                                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261102L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (stage == 2) {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                stage = 3;
                mc.setScreen(null);
                mc.getSingleplayerServer().execute(() -> {
                    try { worldChecks = new StructurePerformanceWorldChecks(mc.getSingleplayerServer().overworld(), checks); }
                    catch (Throwable failure) { serverFailure = failure; }
                });
                return;
            }
            if (stage == 3) {
                if (serverFailure != null && !worldDone) {
                    failures.add("Isolated structure lifecycle: " + serverFailure);
                    worldDone = true;
                }
                if (!bloomWorldDone && mc.level.getChunkSource().getLoadedChunksCount() > 0) {
                    checked("Bloom real world", () -> BloomWorldChecks.run(checks));
                    bloomWorldDone = true;
                }
                if (!jeiDone) {
                    try { jeiDone = PreviewPerformanceChecks.tickJei(checks); }
                    catch (Throwable failure) { failures.add("JEI real runtime: " + failure); failure.printStackTrace(); jeiDone = true; }
                }
                if (!worldDone || !jeiDone || !bloomWorldDone) return;
                checks.add("Fresh isolated world lifecycle finished; no player save read or modified");
                finish(mc, directory, null);
                return;
            }
            if (!directory.startsWith(ROOT) || directory.equals(ROOT)
                    || !Files.isRegularFile(directory.resolve("performance.marker")))
                throw new AssertionError("Explicit isolated performance audit directory required");
            if (mc.level != null || mc.getSingleplayerServer() != null)
                throw new AssertionError("No player world may be open");
            checks.add("Isolated Forge title screen; no player world opened");
            checked("Bloom gates", () -> BloomPerformanceChecks.run(checks));
            checked("Empty recipe map", () -> EmptyRecipePerformanceChecks.run(checks));
            checked("AE and pattern slots", () -> AePerformanceChecks.run(checks));
            checked("Structure index", () -> StructurePerformanceChecks.run(checks));
            checked("Preview preparation", () -> PreviewPerformanceChecks.run(checks));
            checked("Preview visual start", () -> PreviewPerformanceChecks.startVisual(checks));
            stage = 1;
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(mc, directory, failure);
        }
    }

    @FunctionalInterface private interface Check { void run() throws Exception; }

    private void checked(String group, Check check) {
        try { check.run(); }
        catch (Throwable failure) { failures.add(group + ": " + failure); failure.printStackTrace(); }
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || worldChecks == null || worldDone || serverFailure != null) return;
        try { if (worldChecks.tick()) worldDone = true; }
        catch (Throwable failure) {
            try { worldChecks.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            serverFailure = failure;
        }
    }

    private void finish(Minecraft mc, Path directory, Throwable failure) {
        done = true;
        try { PreviewPerformanceChecks.finishVisual(); }
        catch (Exception cleanup) {
            if (failure == null) failure = cleanup;
            else failure.addSuppressed(cleanup);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failure == null && failures.isEmpty() ? "PASS" : "FAIL");
        if (failure != null) result.put("error", failure.toString());
        result.put("failures", failures);
        result.put("checks", checks);
        result.put("scope", "Actual transformed Forge classes, virtual preview screenshots and a fresh isolated lifecycle world; no late-game FPS benchmark");
        try {
            Files.writeString(directory.resolve("performance-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
