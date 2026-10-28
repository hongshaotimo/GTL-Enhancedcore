package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;

/** Headless authority-kernel scale probe; does not measure Forge TPS, original NBT or AE storage. */
public final class CloudLedgerCapacityProbe {
    public static void main(String[] arguments) throws Exception {
        long started = System.nanoTime();
        long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        UUID owner = UUID.randomUUID(), library = UUID.randomUUID(), machine = UUID.randomUUID();
        var ledger = new CloudLedger(); var nodes = new ArrayList<UUID>(); var types = new ArrayList<String>();
        for (int i = 0; i < 10; i++) types.add("mode_" + i);
        for (int i = 0; i < 10; i++) { UUID id = UUID.randomUUID(); ledger.addNode(id, owner, library); nodes.add(id); ledger.claimMode(id, machine, types); }
        for (int i = 0; i < 10000; i++) { UUID entry = UUID.randomUUID(); ledger.addEntry(entry); ledger.lease(nodes.get(i % 10), entry, i * 3, UUID.randomUUID()); }
        ledger.validate(); var leaseIds = new HashSet<UUID>();
        for (var entry : ledger.entries()) if (entry.lease() == null || !leaseIds.add(entry.lease().id())) throw new AssertionError("Missing or duplicate lease");
        var restored = new CloudLedger(); restored.restore(ledger.entries(), ledger.nodes(), ledger.receipts()); restored.validate();
        if (restored.entries().size() != 10000 || restored.nodes().stream().mapToInt(node -> node.slots().size()).sum() != 10000)
            throw new AssertionError("Snapshot model lost authority");
        double millis = (System.nanoTime() - started) / 1_000_000.0;
        long releaseStarted = System.nanoTime();
        for (var entry : restored.entries()) restored.releaseLease(entry.lease().nodeId(), entry.id(), UUID.randomUUID());
        restored.validate();
        if (restored.entries().stream().anyMatch(entry -> entry.status() != CloudLedger.Status.AVAILABLE)
                || restored.nodes().stream().anyMatch(node -> !node.slots().isEmpty())) throw new AssertionError("Release model lost authority");
        double releaseMillis = (System.nanoTime() - releaseStarted) / 1_000_000.0;
        long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        String report = "{\n  \"status\": \"PASS\",\n  \"entries\": 10000,\n  \"nodes\": 10,\n  \"uniqueLeaseIds\": " + leaseIds.size()
                + ",\n  \"insertAndRestoreMillis\": " + millis + ",\n  \"releaseAllMillis\": " + releaseMillis + ",\n  \"heapUsedBeforeBytes\": " + before + ",\n  \"heapUsedAfterBytes\": " + after
                + ",\n  \"scope\": \"Pure Java sparse authority kernel with snapshot restore model; memory samples are not peak usage; no Forge TPS, physical original NBT, disk restart or AE capacity claim\"\n}\n";
        Path output = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-pattern-store-20261023/ledger-capacity.json");
        Files.writeString(output, report); System.out.print(report);
    }
}
