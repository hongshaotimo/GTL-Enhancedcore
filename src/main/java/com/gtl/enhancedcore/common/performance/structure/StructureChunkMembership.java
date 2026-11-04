package com.gtl.enhancedcore.common.performance.structure;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.IdentityHashMap;

/** Reverse membership: removal touches only the chunks previously indexed for this state. */
public final class StructureChunkMembership<T> {
    private final IdentityHashMap<T, LongOpenHashSet> chunks = new IdentityHashMap<>();

    public LongOpenHashSet add(T state, LongIterator positions) {
        LongOpenHashSet unique = new LongOpenHashSet();
        while (positions.hasNext()) unique.add(chunkOfBlock(positions.nextLong()));
        chunks.put(state, unique);
        return unique;
    }

    public LongOpenHashSet remove(T state) { return chunks.remove(state); }

    public static long chunkOfBlock(long position) {
        int x = (int) (position >> 38) >> 4;
        int z = (int) (position << 26 >> 38) >> 4;
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }
}
