package com.gtl.enhancedcore.audit;

import com.google.gson.GsonBuilder;
import com.gtl.enhancedcore.client.performance.map.PackedMapImage;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Dedicated slim helper. Never packaged in production; never opens a world. */
@Mod("enhancedcore_audit")
public final class FramePacingClientChecks {
    private static final Path ROOT = Path.of("C:/IDEA/GTL-Enhancedcore/_audit/framepacing-opt-20261020").toAbsolutePath().normalize();
    private final List<String> checks = new ArrayList<>();
    private boolean done;

    public FramePacingClientChecks() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END || !Boolean.getBoolean("gtl.enhancedcore.framePacingAudit")) return;
        var mc = Minecraft.getInstance();
        if (mc.getOverlay() != null || mc.screen == null) return;
        done = true;
        var result = new LinkedHashMap<String, Object>();
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            check(directory.startsWith(ROOT) && !directory.equals(ROOT), "Task-owned isolated directory");
            check(Files.isRegularFile(directory.resolve("frame-pacing.marker")), "Explicit audit marker");
            check(mc.level == null && mc.getSingleplayerServer() == null, "No player or test world opened");
            mapWorkers();
            bloomLifecycle();
            probeLifecycle(mc);
            result.put("status", "PASS");
        } catch (Throwable failure) {
            result.put("status", "FAIL");
            result.put("error", failure.toString());
            failure.printStackTrace();
        }
        result.put("checks", checks);
        result.put("scope", "Real Forge client Mixin handlers and CSV/JFR lifecycle; no world, no FPS benchmark");
        try {
            Files.writeString(directory.resolve("frame-pacing-checks.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }

    private void mapWorkers() throws Exception {
        Class<?> map = Class.forName("dev.ftb.mods.ftbchunks.client.map.MapRegionData");
        Method handler = Arrays.stream(map.getDeclaredMethods())
            .filter(m -> m.getName().equals("enhanced$parallelMapRaster"))
            .findFirst().orElseThrow(() -> new AssertionError("Parallel map write handler not merged"));
        check(handler.getParameterCount() == 1, "Parallel map handler keeps its callback signature");
        Method invoker = Arrays.stream(map.getDeclaredMethods())
            .filter(m -> m.getName().equals("enhanced$writeData"))
            .findFirst().orElseThrow(() -> new AssertionError("Upstream map writer invoker not merged"));
        check(invoker.getParameterCount() == 6, "Original asynchronous PNG writer remains reachable");

        Class<?> workers = Class.forName("com.gtl.enhancedcore.client.performance.map.MapRasterWorkers");
        var count = workers.getDeclaredField("THREADS"); count.setAccessible(true);
        check(count.getInt(null) == 2, "Isolated startup config selects two map workers");
        short[] height = new short[512 * 512], light = new short[height.length];
        int[] foliage = new int[height.length], grass = new int[height.length], water = new int[height.length];
        height[height.length - 1] = (short) 0x8123; light[height.length - 1] = (short) 0x4567;
        foliage[height.length - 1] = 0x11223344; grass[height.length - 1] = 0x55667788;
        water[height.length - 1] = 0x99aabbcc;
        BufferedImage[] images = {
            PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_ARGB),
            PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB),
            PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB),
            PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB),
            PackedMapImage.create(512, 512, BufferedImage.TYPE_INT_RGB)
        };
        workers.getMethod("populate", short[].class, short[].class, int[].class, int[].class, int[].class,
                BufferedImage[].class).invoke(null, height, light, foliage, grass, water, images);
        check(images[0].getRGB(511, 511) == 0x81234567, "Parallel ARGB map plane preserves packed source bits");
        check(images[1].getRGB(511, 511) == 0xff223344, "Parallel RGB map plane preserves source color");
        check(images[4].getRGB(511, 511) == 0xff115599, "Parallel block map plane combines palette indices");
    }

    @SuppressWarnings("unchecked")
    private void bloomLifecycle() throws Exception {
        Class<?> type = Class.forName("org.gtlcore.gtlcore.client.bloom.SectionMeshes");
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object meshes = constructor.newInstance();
        var field = type.getDeclaredField("pending");
        field.setAccessible(true);
        // The production comparator accepts only private SectionMeshes.Entry objects.
        // Replace this audit-only instance's queue to exercise merged handlers with integers.
        var queue = new PriorityQueue<Object>();
        field.set(meshes, queue);
        queue.addAll(List.of(1, 2, 3, 4));
        var defer = Arrays.stream(type.getDeclaredMethods())
            .filter(m -> m.getName().contains("enhancedcore$deferPrune")).findFirst().orElseThrow();
        defer.setAccessible(true);
        int[] visits = {0};
        Predicate<Object> predicate = value -> { visits[0]++; return ((Integer) value) % 2 == 0; };
        defer.invoke(meshes, queue, predicate);
        defer.invoke(meshes, queue, predicate);
        check(visits[0] == 0 && queue.size() == 4, "Merged unload handler defers repeated scans");
        int count = (Integer) type.getMethod("pendingCount").invoke(meshes);
        check(count == 2 && visits[0] == 4, "Actual pendingCount injection flushes exactly once");
        queue.add(6);
        defer.invoke(meshes, queue, predicate);
        // level is null: original buildSome returns, but HEAD injection must flush first.
        type.getMethod("buildSome", Vec3.class, net.minecraft.client.renderer.culling.Frustum.class).invoke(meshes, Vec3.ZERO, null);
        check(queue.size() == 2 && visits[0] == 7, "Actual buildSome injection flushes before original body");
        queue.add(10);
        defer.invoke(meshes, queue, predicate);
        type.getMethod("setLevel", net.minecraft.client.multiplayer.ClientLevel.class).invoke(meshes, (Object) null);
        check(queue.size() == 2 && visits[0] == 10, "Actual same-level return flushes while Bloom rendering is disabled");
        defer.invoke(meshes, queue, predicate);
        type.getMethod("clear").invoke(meshes);
        queue.add(8);
        type.getMethod("pendingCount").invoke(meshes);
        check(queue.size() == 1 && visits[0] == 10, "Actual clear injection releases deferred predicate");
        queue.clear();
        type.getMethod("clear").invoke(meshes);
    }

    private void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks.add(label);
    }

    private void probeLifecycle(Minecraft mc) throws Exception {
        Class<?> type = Class.forName("com.gtl.frameprobe.ProbeSession");
        var constructor = type.getDeclaredConstructor(Minecraft.class, Thread.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(mc, Thread.currentThread());
        var finish = type.getDeclaredMethod("finishAndWait", long.class);
        finish.setAccessible(true);
        finish.invoke(session, 10000L);
        var pathField = type.getDeclaredField("directory");
        pathField.setAccessible(true);
        Path path = (Path) pathField.get(session);
        var metadata = com.google.gson.JsonParser.parseString(Files.readString(path.resolve("session.json"))).getAsJsonObject();
        check(metadata.get("state").getAsString().equals("complete"), "Real Forge probe closes CSV worker");
        check(metadata.getAsJsonObject("jfr").get("state").getAsString().equals("complete"), "Real Forge automatic module starts and closes JFR");
        check(Files.size(path.resolve("recording.jfr")) > 0, "Real Forge probe writes JFR file");
        try (var recording = new jdk.jfr.consumer.RecordingFile(path.resolve("recording.jfr"))) {
            check(recording.readEventTypes().size() > 0, "Real Forge JFR file immediately readable after stop");
        }
        check(Files.isRegularFile(path.resolve("frames.csv")), "CSV survives JFR lifecycle");
    }
}
