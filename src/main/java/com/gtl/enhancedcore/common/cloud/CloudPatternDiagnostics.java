package com.gtl.enhancedcore.common.cloud;

import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import java.util.*;

/** Advisory runtime evidence. Weak library identities never retain a server or world after unload. */
public final class CloudPatternDiagnostics {
    private static final int LIMIT = 4096;
    private record Key(UUID entryId, UUID nodeId) {}
    public record Diagnostic(UUID entryId, UUID nodeId, String reason) {}
    private static final Map<CloudPatternStore.Library, LinkedHashMap<Key, Diagnostic>> LIBRARIES = new WeakHashMap<>();
    private CloudPatternDiagnostics() {}
    public static synchronized void record(CloudPatternStore.Library library, UUID entryId, UUID nodeId, String reason) {
        Objects.requireNonNull(library); Objects.requireNonNull(entryId); Objects.requireNonNull(nodeId);
        if (reason == null || reason.isBlank()) { clear(library, entryId, nodeId); return; }
        String translation = reason.startsWith("gtl_enhancedcore.diagnostic.") ? reason : "gtl_enhancedcore.diagnostic." + reason;
        var rows = LIBRARIES.computeIfAbsent(library, ignored -> new LinkedHashMap<>(16, .75f, true));
        rows.put(new Key(entryId, nodeId), new Diagnostic(entryId, nodeId, translation));
        while (rows.size() > LIMIT) rows.remove(rows.keySet().iterator().next());
    }
    public static synchronized void clear(CloudPatternStore.Library library, UUID entryId, UUID nodeId) {
        var rows = LIBRARIES.get(library); if (rows == null) return;
        rows.remove(new Key(entryId, nodeId)); if (rows.isEmpty()) LIBRARIES.remove(library);
    }
    public static synchronized void clear(CloudPatternStore.Library library, UUID entryId) {
        var rows = LIBRARIES.get(library); if (rows == null) return;
        rows.keySet().removeIf(key -> key.entryId.equals(entryId)); if (rows.isEmpty()) LIBRARIES.remove(library);
    }
    /** Most recent classifier evidence; absence means unknown, never an inferred catalyst shortage. */
    public static synchronized Optional<Diagnostic> latest(CloudPatternStore.Library library, UUID entryId) {
        var rows = LIBRARIES.get(library); if (rows == null) return Optional.empty();
        Diagnostic found = null;
        for (var row : rows.values()) if (row.entryId.equals(entryId)) found = row;
        return Optional.ofNullable(found);
    }
}
