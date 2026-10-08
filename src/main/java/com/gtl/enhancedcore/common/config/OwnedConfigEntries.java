package com.gtl.enhancedcore.common.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OwnedConfigEntries<Material> {
    private final Set<Material> addedEntries = new HashSet<>();
    private List<Material> trackedEntries;

    public synchronized void reconcile(List<Material> entries, Set<Material> wantedEntries) {
        if (this.trackedEntries != entries) {
            this.addedEntries.clear();
            this.trackedEntries = entries;
        }
        entries.removeIf(entry -> this.addedEntries.contains(entry) && !wantedEntries.contains(entry));
        this.addedEntries.retainAll(wantedEntries);
        for (Material entry : wantedEntries) {
            if (entry != null && !entries.contains(entry)) {
                entries.add(entry);
                this.addedEntries.add(entry);
            }
        }
    }
}
