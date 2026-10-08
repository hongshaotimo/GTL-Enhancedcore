package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.GTLEnhancedcore;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in integrated-client benchmark. Only runs in a launcher-created audit directory. */
@Mod.EventBusSubscriber(modid = "enhancedcore_audit", value = Dist.CLIENT)
public final class ChunkPerformanceChecks {
    private static final boolean ENABLED = Boolean.getBoolean("gtl.enhancedcore.chunkBenchmark");
    private static final int STEPS = Integer.getInteger("gtl.enhancedcore.chunkSteps", 64);
    private static final int ORIGIN_X = Integer.getInteger("gtl.enhancedcore.chunkOriginX", 0);
    private static final int ORIGIN_Z = Integer.getInteger("gtl.enhancedcore.chunkOriginZ", 0);
    private static final int HEIGHT = Integer.getInteger("gtl.enhancedcore.chunkHeight", -48);
    private static final AtomicLong LOADS = new AtomicLong();
    private static final AtomicLong UNLOADS = new AtomicLong();
    private static final AtomicLong NEW = new AtomicLong();
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static final List<Map<String, Object>> PHASES = new ArrayList<>();
    private static final List<Double> FRAMES = new ArrayList<>();
    private static final List<Double> TICKS = new ArrayList<>();
    private static final Object TICK_LOCK = new Object();
    private static volatile String phase = "loading";
    private static volatile long phaseStarted;
    private static volatile long serverTickStarted;
    private static long worldReady, lastFrame, nextSample, nextStep;
    private static long phaseLoads, phaseUnloads, phaseNew;
    private static int step, stage;
    private static boolean opened, finished;
    private static java.lang.reflect.Method sodiumInstance, sodiumDebug, sodiumReady;

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (!ENABLED || event.getLevel().isClientSide()) return;
        LOADS.incrementAndGet();
        if (event.isNewChunk()) NEW.incrementAndGet();
    }

    @SubscribeEvent
    public static void unloaded(ChunkEvent.Unload event) {
        if (ENABLED && !event.getLevel().isClientSide()) UNLOADS.incrementAndGet();
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!ENABLED) return;
        if (event.phase == TickEvent.Phase.START) {
            serverTickStarted = System.nanoTime();
        } else if (serverTickStarted != 0 && !phase.equals("loading")) {
            synchronized (TICK_LOCK) {
                TICKS.add((System.nanoTime() - serverTickStarted) / 1_000_000.0);
            }
        }
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        long now = System.nanoTime();
        if (lastFrame != 0 && !phase.equals("loading")) FRAMES.add((now - lastFrame) / 1_000_000.0);
        lastFrame = now;
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("chunk-benchmark.marker"))) {
                throw new IllegalStateException("Missing isolated benchmark marker");
            }
            mc.options.pauseOnLostFocus = false;
            if (!opened && mc.screen instanceof TitleScreen) {
                opened = true;
                mc.createWorldOpenFlows().loadLevel(mc.screen, "chunk-benchmark");
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
            long now = System.nanoTime();
            if (worldReady == 0) {
                ItemStackKeyChecks.run();
                FlightStateConcurrencyChecks.run();
                if (Boolean.getBoolean("gtl.enhancedcore.expectNaturalOresDisabled")) {
                    // The unpatched entry dereferences level immediately. Null inputs prove early cancellation.
                    new com.gregtechceu.gtceu.api.data.worldgen.ores.OrePlacer().placeOres(null, null, null);
                    GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] NATURAL_ORES_DISABLED verified before world access");
                }
                var executor = net.minecraft.Util.backgroundExecutor();
                int parallelism = executor instanceof java.util.concurrent.ForkJoinPool pool ? pool.getParallelism() : -1;
                GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] WORKERS backgroundProperty={} actualParallelism={}",
                        System.getProperty("max.bg.threads"), parallelism);
                int expected = Integer.getInteger("gtl.enhancedcore.expectedBgThreads", -1);
                if (expected > 0 && parallelism != expected) throw new AssertionError("Background pool size mismatch");
                worldReady = now;
                mc.setScreen(null);
                mc.getSingleplayerServer().execute(() -> {
                    GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] ORE_LAYERS {}",
                            OreGenerationDiagnostics.layers(mc.getSingleplayerServer().overworld()));
                    var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    player.setGameMode(GameType.CREATIVE);
                    player.getAbilities().flying = true;
                    player.getAbilities().mayfly = true;
                    player.onUpdateAbilities();
                    player.teleportTo(player.serverLevel(), ORIGIN_X + 0.5, HEIGHT, ORIGIN_Z + 0.5, -90, 15);
                });
                var cls = Class.forName("me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");
                sodiumInstance = cls.getMethod("instance");
                sodiumDebug = cls.getMethod("getDebugStrings");
                sodiumReady = cls.getMethod("isSectionReady", int.class, int.class, int.class);
                begin("warmup", now);
            }
            if (now >= nextSample) {
                nextSample = now + 1_000_000_000L;
                sample(mc, now);
            }
            long elapsed = now - phaseStarted;
            if (stage == 0 && elapsed >= 30_000_000_000L && PreviewJeiAudit.runtime != null) {
                end(now);
                stage = 1;
                step = 0;
                begin("new_chunks", now);
            } else if ((stage == 1 || stage == 3) && now >= nextStep) {
                nextStep = now + 500_000_000L;
                int x = (stage == 1 ? ++step : STEPS - ++step) * 16;
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    if (server.getPlayerList().getPlayers().isEmpty()) return;
                    var player = server.getPlayerList().getPlayers().get(0);
                    player.teleportTo(player.serverLevel(), ORIGIN_X + x + 0.5, HEIGHT, ORIGIN_Z + 0.5, -90, 15);
                });
                if (step >= STEPS) {
                    end(now);
                    stage++;
                    begin(stage == 2 ? "new_drain" : "reload_drain", now);
                }
            } else if (stage == 2 && elapsed >= 15_000_000_000L) {
                end(now);
                stage = 3;
                step = 0;
                begin("loaded_chunks", now);
            } else if (stage == 4 && elapsed >= 15_000_000_000L) {
                end(now);
                Screenshot.grab(mc.gameDirectory, "chunk-benchmark.png", mc.getMainRenderTarget(), message -> {});
                var output = new LinkedHashMap<String, Object>();
                output.put("phases", PHASES);
                output.put("samples", SAMPLES);
                output.put("steps", STEPS);
                output.put("renderDistance", mc.options.renderDistance().get());
                output.put("simulationDistance", mc.options.simulationDistance().get());
                Files.writeString(mc.gameDirectory.toPath().resolve("chunk-results.json"),
                        new GsonBuilder().setPrettyPrinting().create().toJson(output));
                finished = true;
                GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] COMPLETE");
                mc.stop();
            }
        } catch (Throwable error) {
            finished = true;
            GTLEnhancedcore.LOGGER.error("[CHUNK_BENCH] FAIL", error);
            mc.stop();
        }
    }

    private static void begin(String name, long now) {
        phase = name;
        phaseStarted = now;
        nextStep = now + 500_000_000L;
        phaseLoads = LOADS.get();
        phaseUnloads = UNLOADS.get();
        phaseNew = NEW.get();
        FRAMES.clear();
        synchronized (TICK_LOCK) { TICKS.clear(); }
        lastFrame = 0;
        GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] BEGIN {}", name);
    }

    private static void end(long now) {
        var result = new LinkedHashMap<String, Object>();
        double seconds = (now - phaseStarted) / 1_000_000_000.0;
        result.put("phase", phase);
        result.put("seconds", seconds);
        result.put("serverLoads", LOADS.get() - phaseLoads);
        result.put("serverNewChunks", NEW.get() - phaseNew);
        result.put("serverUnloads", UNLOADS.get() - phaseUnloads);
        result.put("loadsPerSecond", (LOADS.get() - phaseLoads) / seconds);
        result.put("oreGeneratorCallsCumulative", OreGenerationDiagnostics.counts());
        result.put("frameMs", statistics(FRAMES));
        synchronized (TICK_LOCK) { result.put("tickMs", statistics(TICKS)); }
        PHASES.add(result);
        GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] END {}", result);
    }

    private static Map<String, Object> statistics(List<Double> values) {
        var sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        if (sorted.length == 0) return Map.of("count", 0);
        double sum = 0;
        for (double value : sorted) sum += value;
        return Map.of("count", sorted.length, "mean", sum / sorted.length,
                "p50", sorted[sorted.length / 2], "p95", sorted[Math.min(sorted.length - 1, (int) (sorted.length * .95))],
                "p99", sorted[Math.min(sorted.length - 1, (int) (sorted.length * .99))], "max", sorted[sorted.length - 1]);
    }

    private static void sample(Minecraft mc, long now) throws ReflectiveOperationException {
        var sample = new LinkedHashMap<String, Object>();
        sample.put("phase", phase);
        sample.put("seconds", (now - worldReady) / 1_000_000_000.0);
        sample.put("x", mc.player.getX());
        sample.put("loadedClientChunks", mc.level.getChunkSource().getLoadedChunksCount());
        sample.put("serverLoads", LOADS.get());
        sample.put("serverUnloads", UNLOADS.get());
        sample.put("heapUsedMb", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576);
        Object renderer = sodiumInstance.invoke(null);
        sample.put("renderer", sodiumDebug.invoke(renderer));
        int cx = mc.player.chunkPosition().x;
        int cz = mc.player.chunkPosition().z;
        int ready = 0;
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            if ((boolean) sodiumReady.invoke(renderer, cx + dx, -4, cz + dz)) ready++;
        }
        sample.put("nearbyGroundSectionsReady", ready);
        SAMPLES.add(sample);
        GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] SAMPLE {}", sample);
    }
}
