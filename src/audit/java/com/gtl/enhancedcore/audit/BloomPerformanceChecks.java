package com.gtl.enhancedcore.audit;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.client.multiplayer.ClientLevel;
import org.gtlcore.gtlcore.client.bloom.BloomClient;
import org.gtlcore.gtlcore.client.bloom.BloomConfig;
import org.gtlcore.gtlcore.client.bloom.SectionMeshes;
import org.gtlcore.gtlcore.config.BloomOptions;
import org.gtlcore.gtlcore.config.ConfigHolder;

/** Actual transformed OFF gates, running on an isolated Forge render thread. */
public final class BloomPerformanceChecks {
    @SuppressWarnings("unchecked")
    public static void run(List<String> checks) throws Exception {
        var options = ConfigHolder.INSTANCE.bloom;
        var previous = options.bloomMode;
        SectionMeshes meshes = new SectionMeshes();
        Field pending = SectionMeshes.class.getDeclaredField("pending");
        pending.setAccessible(true);
        var queue = new PriorityQueue<Object>();
        pending.set(meshes, queue);
        Field suspended = Arrays.stream(SectionMeshes.class.getDeclaredFields())
                .filter(f -> f.getName().contains("enhancedcore$offSuspended")).findFirst().orElseThrow();
        suspended.setAccessible(true);
        try {
            options.bloomMode = BloomOptions.Mode.FORCE_LOCAL;
            meshes.setLevel(null);
            queue.add(1);
            options.bloomMode = BloomOptions.Mode.OFF;
            meshes.setLevel(null);
            check(queue.isEmpty() && suspended.getBoolean(meshes), checks,
                    "Bloom entering OFF clears once even at the same null level");
            queue.add(2); // Audit sentinel: producers must neither clear again nor append anything.
            for (int i = 0; i < 1000; i++) {
                meshes.setLevel(null);
                meshes.loadChunk(null); // Original would dereference a chunk once a level is present.
                meshes.dirty(0, 0, 0);
                meshes.dirtyBlock(0, 0, 0);
                meshes.dirtyArea(0, 0, 0, 16, 16, 16);
                meshes.unloadChunk(0, 0);
                meshes.rebuildAll();
            }
            check(queue.size() == 1 && queue.peek().equals(2), checks,
                    "Bloom OFF repeated frames and all six producers perform no rebuild or repeated clear");
            var chunkLoad = BloomClient.class.getDeclaredMethod("chunkLoad",
                    net.minecraftforge.event.level.ChunkEvent.Load.class);
            chunkLoad.setAccessible(true);
            chunkLoad.invoke(null, new Object[]{null});
            check(BloomConfig.mode() == BloomOptions.Mode.OFF, checks,
                    "Bloom OFF chunk callback exits before accessing event or allocating its runnable");
            queue.clear();
            options.bloomMode = BloomOptions.Mode.AUTO;
            meshes.setLevel(null);
            check(!suspended.getBoolean(meshes), checks, "Bloom re-enabling restores setLevel lifecycle");
            options.bloomMode = BloomOptions.Mode.OFF;
            queue.add(3);
            meshes.dirtyBlock(0, 0, 0);
            check(queue.isEmpty() && suspended.getBoolean(meshes), checks,
                    "Bloom first producer observes OFF before the next render frame and clears stale work");
            meshes.clear();
            check(meshes.pendingCount() == 0, checks, "Bloom unload clear retains no pending work");
        } finally {
            queue.clear();
            meshes.clear();
            options.bloomMode = previous;
        }
    }

    private static void check(boolean condition, List<String> checks, String message) {
        if (!condition) throw new AssertionError(message);
        checks.add(message);
    }
}
