package com.gtl.enhancedcore.common.cloud.store;

import java.util.*;

/** Bounded runtime invalidation history. Missing history always requires a full scan. */
public final class CloudAllocationChanges {
    private static final int LIMIT = 4096;
    private long fullRevision;
    private final NavigableMap<Long, UUID> entries = new TreeMap<>();
    public void reset(long revision) { fullRevision = revision; entries.clear(); }
    public void record(long revision, UUID entry) {
        if (entry == null) { reset(revision); return; }
        entries.put(revision, entry);
        while (entries.size() > LIMIT) fullRevision = entries.pollFirstEntry().getKey();
    }
    /** Null means that a full invalidation or history eviction occurred since the last scan. */
    public List<UUID> since(long revision) {
        return revision < fullRevision ? null : List.copyOf(new LinkedHashSet<>(entries.tailMap(revision, false).values()));
    }
    /** A cold/full scan handles newly changed entries before walking the old directory. */
    public List<UUID> recent() { return List.copyOf(new LinkedHashSet<>(entries.descendingMap().values())); }
}
