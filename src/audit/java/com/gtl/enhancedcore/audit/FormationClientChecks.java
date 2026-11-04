package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit disposable-world audit; cannot open an existing save. */
@Mod("enhancedcore_audit")
public final class FormationClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/formation-regression-20261103")
            .toAbsolutePath().normalize();
    private final List<String> checks = Collections.synchronizedList(new ArrayList<>());
    private final List<String> failures = Collections.synchronizedList(new ArrayList<>());
    private volatile FormationRegressionChecks world;
    private volatile boolean worldDone;
    private volatile boolean serverStopped;
    private boolean done;
    private int stage;

    public FormationClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::clientTick);
        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        MinecraftForge.EVENT_BUS.addListener(this::stopped);
    }

    private void stopped(ServerStoppedEvent event) { serverStopped = true; }

    private void clientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.formationAudit")) return;
        var mc = Minecraft.getInstance();
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (stage == 0) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                if (!directory.startsWith(ROOT) || directory.equals(ROOT)
                        || !Files.isRegularFile(directory.resolve("formation.marker")))
                    throw new AssertionError("Explicit isolated formation audit directory required");
                if (mc.level != null || mc.getSingleplayerServer() != null)
                    throw new AssertionError("No existing player world may be open");
                try { FormationRegressionChecks.runPreview(checks); }
                catch (Throwable failure) { record("preview", failure); }
                if (Files.exists(directory.resolve("saves/formation-lifecycle")))
                    throw new AssertionError("Fresh isolated formation world required");
                stage = 1;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("formation-lifecycle",
                        new LevelSettings("Formation Recovery Audit", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261103L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            } else if (stage == 1) {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                stage = 2;
                mc.setScreen(null);
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try { world = new FormationRegressionChecks(server.overworld(), checks); }
                    catch (Throwable failure) { record("world initialization", failure); worldDone = true; }
                });
            } else if (stage == 2) {
                if (!worldDone) return;
                if (!FormationJeiVisualChecks.tick(checks)) return;
                stage = 3;
                mc.level.disconnect();
                mc.clearLevel();
                mc.setScreen(new TitleScreen());
            } else if (stage == 3) {
                if (mc.level != null || mc.getSingleplayerServer() != null || !serverStopped) return;
                checks.add("Integrated server fully stopped before result; no player saves opened");
                finish(mc, directory);
            }
        } catch (Throwable failure) {
            record("client lifecycle", failure);
            try { FormationJeiVisualChecks.close(); } catch (Throwable cleanup) { record("JEI cleanup", cleanup); }
            finish(mc, directory);
        }
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || world == null || worldDone) return;
        try { worldDone = world.tick(); }
        catch (Throwable failure) {
            record("world formation", failure);
            try { world.close(); } catch (Throwable cleanup) { record("world cleanup", cleanup); }
            worldDone = true;
        }
    }

    private void record(String group, Throwable failure) {
        failures.add(group + ": " + failure);
        failure.printStackTrace();
    }

    private void finish(Minecraft mc, Path directory) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failures.isEmpty() ? "PASS" : "FAIL");
        result.put("checks", checks);
        result.put("failures", failures);
        result.put("serverStopped", serverStopped);
        result.put("scope", "Native and staged powered previews, real hatches, unload recovery and real recipe in a fresh isolated world");
        try { Files.writeString(directory.resolve("formation-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
