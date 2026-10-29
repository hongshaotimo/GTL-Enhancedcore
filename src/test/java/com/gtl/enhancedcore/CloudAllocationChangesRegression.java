package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.store.CloudAllocationChanges;
import java.util.*;

/** A large unchanged directory cannot re-enter a scan for one new original. */
public final class CloudAllocationChangesRegression {
    static int run() {
        var changes = new CloudAllocationChanges();
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        check(changes.since(-1) == null, "Fresh endpoint requires full validation");
        changes.record(1, first); changes.record(2, first); changes.record(3, second);
        check(changes.since(0).equals(List.of(first, second)), "Upload and ACK coalesce while preserving every changed original");
        check(changes.since(2).equals(List.of(second)), "Completed endpoint examines only newer original");
        check(changes.since(3).isEmpty(), "No revision change produces no directory work");
        check(changes.recent().equals(List.of(second, first)), "Cold scan prioritizes newest original");
        changes.record(4, null);
        check(changes.since(3) == null && changes.since(4).isEmpty(), "Shared catalysts invalidate all previous matches");
        changes.record(5, first);
        check(changes.since(3) == null && changes.since(4).equals(List.of(first)), "New upload cannot hide an earlier full invalidation");
        changes.reset(100);
        check(changes.since(99) == null && changes.since(100).isEmpty(), "Restart requires full scan at persisted revision");
        for (int i = 101; i <= 4197; i++) changes.record(i, new UUID(0, i));
        check(changes.since(100) == null && changes.since(101).size() == 4096, "History stays bounded and old readers fall back to full scan");
        check(changes.since(4196).equals(List.of(new UUID(0, 4197))), "Newest delta remains constant-size after history eviction");
        return 10;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
