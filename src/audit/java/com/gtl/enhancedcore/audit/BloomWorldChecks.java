package com.gtl.enhancedcore.audit;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.gtlcore.gtlcore.client.bloom.SectionMeshes;
import org.gtlcore.gtlcore.config.BloomOptions;
import org.gtlcore.gtlcore.config.ConfigHolder;

/** Invoke on the render thread after joining an isolated new world with nonempty chunks. */
public final class BloomWorldChecks {
    private BloomWorldChecks() {}

    @SuppressWarnings("unchecked")
    public static void run(List<String> checks) throws Exception {
        if (!RenderSystem.isOnRenderThread()) throw new IllegalStateException("Bloom audit requires render thread");
        var level = Minecraft.getInstance().level;
        if (level == null) throw new IllegalStateException("Bloom audit requires a real client world");
        var options = ConfigHolder.INSTANCE.graphics.bloom;
        var previousMode = options.bloomMode;
        var meshes = new SectionMeshes();
        var entries = (Map<Long, Object>) field(SectionMeshes.class, "entries").get(meshes);
        var levelField = field(SectionMeshes.class, "level");
        var jobField = field(SectionMeshes.class, "job");
        var uploads = (ArrayDeque<?>) field(SectionMeshes.class, "uploads").get(meshes);
        try {
            options.bloomMode = BloomOptions.Mode.FORCE_LOCAL;
            meshes.setLevel(level);
            check(levelField.get(meshes) == level && !entries.isEmpty(), checks,
                    "Bloom real level native discovery finds loaded nonempty sections");
            var expected = new HashSet<>(entries.keySet());
            check(meshes.pendingCount() == expected.size(), checks,
                    "Bloom native discovery queues every loaded nonempty section once");
            Object first = entries.values().iterator().next();
            Class<?> entryClass = first.getClass();
            int x = field(entryClass, "x").getInt(first);
            int y = field(entryClass, "y").getInt(first);
            int z = field(entryClass, "z").getInt(first);
            var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk == null) throw new AssertionError("native discovered chunk is no longer loaded");

            options.bloomMode = BloomOptions.Mode.OFF;
            meshes.setLevel(level);
            check(entries.isEmpty() && meshes.pendingCount() == 0 && levelField.get(meshes) == null
                            && jobField.get(meshes) == null && uploads.isEmpty(), checks,
                    "Bloom OFF clears real level sections pending work active job uploads and level reference");
            check(!field(entryClass, "valid").getBoolean(first), checks,
                    "Bloom OFF invalidates existing native section entries");
            for (int i = 0; i < 20; i++) {
                meshes.loadChunk(chunk);
                meshes.dirty(x, y, z);
                meshes.dirtyBlock(x << 4, y << 4, z << 4);
                meshes.dirtyArea(x << 4, y << 4, z << 4, (x << 4) + 15, (y << 4) + 15, (z << 4) + 15);
                meshes.unloadChunk(x, z);
                meshes.rebuildAll();
                meshes.setLevel(level);
            }
            check(entries.isEmpty() && meshes.pendingCount() == 0 && levelField.get(meshes) == null, checks,
                    "Bloom OFF ignores real loaded chunk and dirty producers across repeated frames");

            options.bloomMode = BloomOptions.Mode.FORCE_LOCAL;
            meshes.setLevel(level);
            check(levelField.get(meshes) == level && entries.keySet().equals(expected)
                            && meshes.pendingCount() == expected.size(), checks,
                    "Bloom OFF to ON rediscovers and requeues all loaded sections after discarded callbacks");
            Field dirty = field(entryClass, "dirty");
            check(entries.values().stream().allMatch(entry -> fullDirty(dirty, entry)), checks,
                    "Bloom resumed sections carry all 4096 dirty positions for a complete rebuild");
            Object resumed = entries.values().iterator().next();
            meshes.setLevel(level);
            check(entries.containsValue(resumed) && meshes.pendingCount() == expected.size(), checks,
                    "Bloom enabled same level frame preserves existing entries and queue membership");

            options.bloomMode = BloomOptions.Mode.OFF;
            meshes.dirtyBlock(x << 4, y << 4, z << 4);
            check(entries.isEmpty() && meshes.pendingCount() == 0 && levelField.get(meshes) == null, checks,
                    "Bloom real world producer observes a second OFF transition before the next frame");
            meshes.clear();
            options.bloomMode = BloomOptions.Mode.AUTO;
            meshes.setLevel(level);
            check(entries.keySet().equals(expected) && meshes.pendingCount() == expected.size(), checks,
                    "Bloom AUTO after OFF and explicit world cleanup restores native section discovery");
            meshes.setLevel(null);
            check(entries.isEmpty() && meshes.pendingCount() == 0 && levelField.get(meshes) == null, checks,
                    "Bloom enabled real world to null transition releases sections and queue");
        } finally {
            meshes.clear();
            options.bloomMode = previousMode;
        }
    }

    private static boolean fullDirty(Field field, Object entry) {
        try {
            return ((BitSet) field.get(entry)).cardinality() == 4096;
        } catch (IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void check(boolean condition, List<String> checks, String message) {
        if (!condition) throw new AssertionError(message);
        checks.add(message);
    }
}
