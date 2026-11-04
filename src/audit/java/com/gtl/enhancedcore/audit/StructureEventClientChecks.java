package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** Explicitly selected tiny title/new-world event dispatch audit; never opens an existing save. */
@Mod("enhancedcore_audit")
public final class StructureEventClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/structure-event-hotfix-20261102")
            .toAbsolutePath().normalize();
    private final List<String> checks = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger clientChunkUnloads = new AtomicInteger();
    private final AtomicInteger clientLevelUnloads = new AtomicInteger();
    private final AtomicInteger serverChunkUnloads = new AtomicInteger();
    private final AtomicInteger serverLevelUnloads = new AtomicInteger();
    private volatile boolean serverStopped;
    private volatile Throwable serverFailure;
    private volatile boolean serverDone;
    private int stage;
    private int ticks;
    private boolean done;

    public StructureEventClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ChunkEvent.Unload.class, event -> {
            if (event.getLevel() != null && event.getLevel().isClientSide()) clientChunkUnloads.incrementAndGet();
            else if (event.getLevel() != null) serverChunkUnloads.incrementAndGet();
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, ServerStoppedEvent.class,
                event -> serverStopped = true);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LevelEvent.Unload.class, event -> {
            if (event.getLevel().isClientSide()) clientLevelUnloads.incrementAndGet();
            else serverLevelUnloads.incrementAndGet();
        });
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.structureEventAudit")) return;
        var mc = Minecraft.getInstance();
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (++ticks > 2400) throw new AssertionError("Structure event audit timed out at stage " + stage);
            if (stage == 0) {
                if (mc.getOverlay() != null || mc.screen == null) return;
                if (!directory.startsWith(ROOT) || directory.equals(ROOT)
                        || !Files.isRegularFile(directory.resolve("structure-event.marker")))
                    throw new AssertionError("Explicit isolated structure-event directory required");
                if (mc.level != null || mc.getSingleplayerServer() != null)
                    throw new AssertionError("No existing player world may be open");
                StructureEventBusChecks.run(checks);
                if (Files.exists(directory.resolve("saves/structure-event-lifecycle")))
                    throw new AssertionError("Fresh isolated event world required");
                stage = 1;
                mc.options.pauseOnLostFocus = false;
                mc.createWorldOpenFlows().createFreshLevel("structure-event-lifecycle",
                        new LevelSettings("Structure Event Audit", GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20261102L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                return;
            }
            if (stage == 1) {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
                stage = 2;
                mc.setScreen(null);
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try { StructureEventBusChecks.runWorld(server.overworld(), checks); }
                    catch (Throwable failure) { serverFailure = failure; }
                    finally { serverDone = true; }
                });
                return;
            }
            if (stage == 2) {
                if (!serverDone) return;
                if (serverFailure != null) throw new AssertionError("Server event dispatch failed", serverFailure);
                // runWorld loads an un-ticketed chunk far outside spawn/player view. Let the
                // normal server chunk lifecycle unload it, rather than directly calling handlers.
                if (serverChunkUnloads.get() == 0) return;
                int x = mc.player.blockPosition().getX() >> 4;
                int z = mc.player.blockPosition().getZ() >> 4;
                if (mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) == null) return;
                int before = clientChunkUnloads.get();
                mc.level.getChunkSource().drop(x, z);
                if (clientChunkUnloads.get() <= before) throw new AssertionError("Real client chunk removal did not dispatch unload");
                checks.add("Actual client cache drop removes a loaded chunk and dispatches global ChunkEvent.Unload without handler cast failure");
                checks.add("Actual server chunk lifecycle globally dispatches ChunkEvent.Unload without handler cast failure");
                stage = 3;
                mc.level.disconnect();
                mc.clearLevel();
                mc.setScreen(new TitleScreen());
                return;
            }
            if (stage == 3) {
                if (mc.level != null || mc.getSingleplayerServer() != null
                        || clientLevelUnloads.get() == 0 || serverLevelUnloads.get() == 0 || !serverStopped) return;
                checks.add("Actual client and integrated-server world exit both dispatch global LevelEvent.Unload successfully");
                checks.add("Integrated ServerStoppedEvent observed before recording PASS");
                finish(mc, directory, null);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(mc, directory, failure);
        }
    }

    private void finish(Minecraft mc, Path directory, Throwable failure) {
        done = true;
        var result = new LinkedHashMap<String, Object>();
        result.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) result.put("error", failure.toString());
        result.put("checks", checks);
        result.put("clientChunkUnloads", clientChunkUnloads.get());
        result.put("clientLevelUnloads", clientLevelUnloads.get());
        result.put("serverChunkUnloads", serverChunkUnloads.get());
        result.put("serverLevelUnloads", serverLevelUnloads.get());
        result.put("serverStopped", serverStopped);
        result.put("scope", "Real ModLauncher event wrappers, isolated event buses and a fresh disposable world; no player saves opened");
        try { Files.writeString(directory.resolve("structure-event-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result)); }
        catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
