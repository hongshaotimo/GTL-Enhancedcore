package com.gtl.enhancedcore.common.performance.structure;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded chunk acceleration for unfinished structures. Overflow keeps one bounds record per search. */
public final class StructureSearchWatches {
    public static final int MAX_CHUNKS_PER_SEARCH = 1024;
    public static final int MAX_CHUNK_REFERENCES = 8192;
    private final Map<StructureSearchBackoff, Bounds> searches = new IdentityHashMap<>();
    private final ConcurrentHashMap<Long, Set<StructureSearchBackoff>> chunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StructureSearchBackoff, Bounds> overflow = new ConcurrentHashMap<>();
    private int references;
    private volatile boolean empty = true;

    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds union(Bounds other) {
            return new Bounds(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX),
                    Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
        }
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
        public boolean containsChunk(int x, int z) {
            return x >= (minX >> 4) && x <= (maxX >> 4) && z >= (minZ >> 4) && z <= (maxZ >> 4);
        }
        long chunkCount() {
            return ((long) (maxX >> 4) - (minX >> 4) + 1) * ((long) (maxZ >> 4) - (minZ >> 4) + 1);
        }
    }

    public synchronized void watch(StructureSearchBackoff search, Bounds bounds) {
        Bounds previous = searches.get(search);
        if (bounds.equals(previous)) return;
        remove(search);
        searches.put(search, bounds);
        empty = false;
        long count = bounds.chunkCount();
        if (count > MAX_CHUNKS_PER_SEARCH || references + count > MAX_CHUNK_REFERENCES) {
            overflow.put(search, bounds);
            return;
        }
        for (int x = bounds.minX >> 4; x <= (bounds.maxX >> 4); x++) {
            for (int z = bounds.minZ >> 4; z <= (bounds.maxZ >> 4); z++) {
                chunks.computeIfAbsent(key(x, z), ignored -> ConcurrentHashMap.newKeySet()).add(search);
            }
        }
        references += (int) count;
    }

    public synchronized void watch(StructureSearchBackoff search, Bounds bounds, long token) {
        // Checked under the same monitor as removal: an attempt that outlives onUnload must
        // not recreate its watcher after the unload path has removed it.
        if (search.current(token)) watch(search, bounds);
    }

    public synchronized void remove(StructureSearchBackoff search) {
        Bounds previous = searches.remove(search);
        empty = searches.isEmpty();
        if (previous == null || overflow.remove(search) != null) return;
        for (int x = previous.minX >> 4; x <= (previous.maxX >> 4); x++) {
            for (int z = previous.minZ >> 4; z <= (previous.maxZ >> 4); z++) {
                long key = key(x, z);
                Set<StructureSearchBackoff> members = chunks.get(key);
                members.remove(search);
                if (members.isEmpty()) chunks.remove(key, members);
            }
        }
        references -= (int) previous.chunkCount();
    }

    public void blockChanged(int x, int y, int z) {
        if (empty) return;
        synchronized (this) {
            Set<StructureSearchBackoff> members = chunks.get(key(x >> 4, z >> 4));
            if (members != null) for (var search : members) {
                if (searches.get(search).contains(x, y, z)) search.wake();
            }
            for (var entry : overflow.entrySet()) if (entry.getValue().contains(x, y, z)) entry.getKey().wake();
        }
    }

    public void chunkChanged(int x, int z) {
        if (empty) return;
        synchronized (this) {
            Set<StructureSearchBackoff> members = chunks.get(key(x, z));
            if (members != null) for (var search : members) search.wake();
            for (var entry : overflow.entrySet()) if (entry.getValue().containsChunk(x, z)) entry.getKey().wake();
        }
    }

    public synchronized void clear() {
        for (var search : searches.keySet()) search.unload();
        searches.clear();
        chunks.clear();
        overflow.clear();
        references = 0;
        empty = true;
    }

    public synchronized int referenceCount() { return references; }
    public synchronized int searchCount() { return searches.size(); }
    private static long key(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }
}
