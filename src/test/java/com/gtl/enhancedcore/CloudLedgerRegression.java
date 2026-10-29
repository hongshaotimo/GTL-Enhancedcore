package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import java.util.*;

/** Pure Java ownership tests; does not claim Forge persistence or external-inventory crash atomicity. */
public final class CloudLedgerRegression {
    private static int assertions;
    public static void main(String[] args) { System.out.println("Cloud ledger assertions: " + run()); }
    static int run() {
        assertions = 0;
        assertions += CloudEndpointRegression.run();
        assertions += CloudOrderCapacityRegression.run();
        assertions += CloudMeTargetRegression.run();
        assertions += CloudPatternAccessRegression.run();
        assertions += CloudUploadSequenceRegression.run();
        assertions += CloudAllocationChangesRegression.run();
        assertions += IvWideEnergyRegression.run();
        var furnaceModes = List.of("furnace", "alloy_smelter");
        var smelterLedger = new CloudLedger();
        UUID smelterOwner = UUID.randomUUID(), smelterLibrary = UUID.randomUUID(), smelterInstance = UUID.randomUUID();
        UUID alloyEndpoint = UUID.randomUUID(), electricEndpoint = UUID.randomUUID();
        smelterLedger.addNode(alloyEndpoint, smelterOwner, smelterLibrary);
        smelterLedger.addNode(electricEndpoint, smelterOwner, smelterLibrary);
        check(smelterLedger.claimMode(alloyEndpoint, smelterInstance,
                com.gtl.enhancedcore.common.cloud.CloudEndpointPolicy.preferredModes(furnaceModes, "alloy_smelter")).equals("alloy_smelter"),
                "Single industrial alloy endpoint does not silently claim furnace first");
        check(smelterLedger.claimMode(electricEndpoint, smelterInstance,
                com.gtl.enhancedcore.common.cloud.CloudEndpointPolicy.preferredModes(furnaceModes, "alloy_smelter")).equals("furnace"),
                "Second industrial endpoint fills the unoccupied electric furnace mode");
        check(smelterLedger.claimMode(alloyEndpoint, smelterInstance, furnaceModes).equals("alloy_smelter"),
                "Controller mode toggle never reassigns existing cloud lease ownership");
        coreAuthorityExports();
        UUID owner = UUID.randomUUID(), library = UUID.randomUUID(), machine = UUID.randomUUID();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        CloudLedger ledger = new CloudLedger();
        for (UUID node : List.of(a, b, c, d)) ledger.addNode(node, owner, library);
        List<String> types = List.of("assembler", "assembler", "circuit", "arc");
        check(ledger.claimMode(a, machine, types).equals("assembler"), "First mode follows definition order with ID deduplication");
        check(ledger.claimMode(b, machine, types).equals("circuit"), "Second node never duplicates first mode");
        check(ledger.claimMode(c, machine, types).equals("arc"), "Third node claims third unique mode");
        check(ledger.claimMode(d, machine, types).isEmpty(), "Fourth node waits when every mode is occupied");
        check(ledger.claimMode(c, machine, List.of("arc", "assembler", "circuit")).equals("arc"), "Reordering never moves existing node");
        rejects(() -> ledger.claimMode(a, UUID.randomUUID(), types), "New machine cannot inherit old instance binding");
        UUID e = UUID.randomUUID(); ledger.addEntry(e); UUID leaseOperation = UUID.randomUUID();
        var lease = ledger.lease(b, e, 27, leaseOperation);
        check(lease.stableSlot() == 27, "Sparse stable slot is retained");
        check(ledger.lease(b, e, 27, leaseOperation).equals(lease), "Lease retry is idempotent");
        rejects(() -> ledger.lease(a, e, 0, UUID.randomUUID()), "One original cannot have two consumers");
        rejects(() -> ledger.export(e, ledger.entry(e).revision(), UUID.randomUUID()), "Leased original cannot be extracted");
        Object endpoint = new Object(), copiedEndpoint = new Object();
        check(ledger.activate(b, endpoint), "First endpoint activates");
        check(ledger.activate(b, endpoint), "Same runtime instance may retry activation");
        check(!ledger.activate(b, copiedEndpoint), "Copied endpoint cannot activate alongside original");
        ledger.deactivate(b, copiedEndpoint);
        check(ledger.active(b, endpoint), "Foreign endpoint cannot unload original");
        ledger.deactivate(b, endpoint);
        check(ledger.node(b).slots().get(27).equals(e), "Chunk unload preserves lease and stable mode");
        check(!ledger.active(b, null), "Missing endpoint never proves active ownership");
        UUID detach = UUID.randomUUID(); ledger.detach(b, detach); ledger.detach(b, detach);
        check(ledger.entry(e).status() == CloudLedger.Status.AVAILABLE, "Detach releases original");
        check(ledger.node(b).generation() == 2, "Detach increments generation once despite replay");
        check(!ledger.activate(b, endpoint), "Old detached endpoint cannot resume");
        check(ledger.node(a).mode().equals("assembler") && ledger.node(c).mode().equals("arc"), "Removing middle node never shuffles others");
        check(ledger.claimMode(d, machine, types).equals("circuit"), "Replacement fills the middle gap");
        long revision = ledger.entry(e).revision(); ledger.archive(e, revision, true);
        rejects(() -> ledger.export(e, revision, UUID.randomUUID()), "Stale and archived extraction rejected");
        ledger.archive(e, ledger.entry(e).revision(), false);
        UUID exportOperation = UUID.randomUUID(); ledger.export(e, ledger.entry(e).revision(), exportOperation);
        ledger.export(e, revision, exportOperation);
        check(ledger.entry(e).status() == CloudLedger.Status.EXPORTED, "Export retry never returns to available");
        check(ledger.receipt(exportOperation).status().equals("EXTERNAL_PENDING"), "External result remains pending until acknowledged");
        ledger.needsReview(exportOperation);
        check(ledger.receipt(exportOperation).status().equals("NEEDS_REVIEW"), "Ambiguous result is preserved for review");
        check(ledger.entry(e).status() == CloudLedger.Status.NEEDS_REVIEW, "Ambiguous external result quarantines original rights");
        rejects(() -> ledger.acknowledge(exportOperation), "An ambiguous external transfer cannot be blindly acknowledged");
        ledger.reconcile(exportOperation, true);
        rejects(() -> ledger.record(exportOperation, "UPLOAD", e, "COMMITTED"), "Reusing operation for another action is rejected");
        CloudLedger restored = new CloudLedger(); restored.restore(ledger.entries(), ledger.nodes(), ledger.receipts());
        check(restored.node(d).mode().equals("circuit"), "Restart preserves replacement mode");
        check(restored.entry(e).status() == CloudLedger.Status.EXPORTED, "Restart never recreates exported original");
        restored.validate();
        CloudLedger corrupt = new CloudLedger();
        var duplicateNodes = new ArrayList<>(ledger.nodes());
        duplicateNodes.add(new CloudLedger.NodeState(UUID.randomUUID(), owner, library, machine, "arc", 1, false, Map.of()));
        rejects(() -> corrupt.restore(ledger.entries(), duplicateNodes, ledger.receipts()), "Persisted duplicate mode is rejected, not silently reassigned");
        UUID pending = UUID.randomUUID(), upload = UUID.randomUUID(); ledger.addPendingEntry(pending);
        ledger.record(upload, "UPLOAD", pending, "EXTERNAL_PENDING");
        rejects(() -> ledger.lease(a, pending, 90, UUID.randomUUID()), "Unacknowledged import cannot be published or leased");
        ledger.acknowledge(upload);
        check(ledger.entry(pending).status() == CloudLedger.Status.AVAILABLE, "Acknowledged import becomes available exactly once");
        long acknowledgedRevision = ledger.entry(pending).revision(); ledger.acknowledge(upload);
        check(ledger.entry(pending).revision() == acknowledgedRevision, "Repeated acknowledgement never mutates stock revision");
        CloudLedger large = new CloudLedger(); UUID largeNode = UUID.randomUUID(); large.addNode(largeNode, owner, library);
        large.claimMode(largeNode, machine, List.of("assembly")); CloudLedger.NodeState oldEmpty = large.node(largeNode);
        for (int i = 0; i < 10000; i++) { UUID id = UUID.randomUUID(); large.addEntry(id); large.lease(largeNode, id, i * 2, UUID.randomUUID()); }
        check(large.node(largeNode).slots().size() == 10000, "Ten thousand leases retain distinct sparse stable slots");
        check(oldEmpty.slots().isEmpty(), "Structural sharing never mutates prior node snapshots"); large.validate();
        UUID released = large.node(largeNode).slots().get(10000), releaseOp = UUID.randomUUID();
        long beforeRelease = large.entry(released).revision(); var priorSlots = large.node(largeNode).slots();
        UUID foreignNode = UUID.randomUUID(); large.addNode(foreignNode, owner, library);
        rejects(() -> large.releaseLease(foreignNode, released, UUID.randomUUID()), "Foreign endpoint cannot release another lease");
        large.releaseLease(largeNode, released, releaseOp); large.releaseLease(largeNode, released, releaseOp);
        check(large.entry(released).status() == CloudLedger.Status.AVAILABLE && large.entry(released).revision() == beforeRelease + 1,
                "Single release and replay expose original once with one revision increment");
        check(!large.node(largeNode).slots().containsKey(10000) && large.node(largeNode).slots().get(10002).equals(priorSlots.get(10002)),
                "Single release leaves a stable hole without moving adjacent slots");
        check(priorSlots.size() == 10000 && !large.node(largeNode).detached() && large.node(largeNode).generation() == 1,
                "Single release preserves prior snapshots and the live endpoint generation");
        rejects(() -> large.releaseLease(largeNode, released, UUID.randomUUID()), "Fresh operation cannot release an already free original");
        large.claimMode(foreignNode, machine, List.of("other"));
        var reassigned = large.lease(foreignNode, released, 9, UUID.randomUUID()); large.releaseLease(largeNode, released, releaseOp);
        check(large.entry(released).lease().equals(reassigned), "Replaying old release cannot revoke a later owner's new lease"); large.validate();
        for (var entry : large.entries()) large.releaseLease(entry.lease().nodeId(), entry.id(), UUID.randomUUID());
        check(large.entries().stream().allMatch(entry -> entry.status() == CloudLedger.Status.AVAILABLE), "Ten thousand single releases return every original exactly once");
        check(large.nodes().stream().allMatch(node -> node.slots().isEmpty() && !node.detached() && node.generation() == 1),
                "Ten thousand sparse trie removals retain live endpoint identities without stale slots"); large.validate();
        for (int seed = 0; seed < 20; seed++) {
            CloudLedger random = new CloudLedger(); Random rng = new Random(seed); List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < 24; i++) { UUID id = UUID.randomUUID(); random.addNode(id, owner, library); ids.add(id); }
            Collections.shuffle(ids, rng);
            for (UUID id : ids) random.claimMode(id, machine, List.of("one", "two", "three"));
            check(random.nodes().stream().filter(n -> !n.mode().isEmpty()).count() == 3, "Only three ordered modes can ever be claimed");
            UUID middle = random.nodes().stream().filter(n -> n.mode().equals("two")).findFirst().orElseThrow().id();
            random.detach(middle, UUID.randomUUID()); random.validate();
            UUID replacement = ids.stream().filter(id -> random.node(id).machineInstance() == null).findFirst().orElseThrow();
            check(random.claimMode(replacement, machine, List.of("one", "two", "three")).equals("two"), "Every arrival permutation fills only released middle mode");
            random.validate();
        }
        return assertions;
    }
    private static void coreAuthorityExports() {
        var ledger = new CloudLedger();
        UUID owner = UUID.randomUUID(), library = UUID.randomUUID(), node = UUID.randomUUID(), machine = UUID.randomUUID();
        UUID exported = UUID.randomUUID(), sibling = UUID.randomUUID(), operation = UUID.randomUUID();
        ledger.addNode(node, owner, library); ledger.claimMode(node, machine, List.of("core"));
        ledger.addEntry(exported); ledger.addEntry(sibling);
        UUID oldLeaseOperation = UUID.randomUUID();
        var oldLease = ledger.lease(node, exported, 17, oldLeaseOperation);
        var siblingLease = ledger.lease(node, sibling, 49, UUID.randomUUID());
        var before = ledger.node(node); var endpoint = new Object(); ledger.activate(node, endpoint);
        long oldRevision = ledger.entry(exported).revision();
        rejects(() -> ledger.export(exported, oldRevision, UUID.randomUUID()), "Ordinary export still rejects an allocated original");
        ledger.exportFromCore(exported, operation);
        check(ledger.entry(exported).status() == CloudLedger.Status.EXPORTED && ledger.entry(exported).lease() == null,
                "Core authority revokes a live lease and exports its original");
        check(!ledger.node(node).slots().containsKey(oldLease.stableSlot()) && ledger.node(node).slots().get(49).equals(sibling),
                "Core export removes only its stable slot and preserves sibling leases");
        check(ledger.entry(sibling).lease().equals(siblingLease) && ledger.node(node).generation() == before.generation()
                        && ledger.node(node).machineInstance().equals(machine) && ledger.node(node).mode().equals("core")
                        && !ledger.node(node).detached() && ledger.active(node, endpoint),
                "Core export leaves endpoint execution identity and unrelated routes intact");
        check(before.slots().get(17).equals(exported) && before.slots().size() == 2,
                "Core export does not mutate prior persistent slot snapshots");
        check(ledger.entry(exported).revision() == oldRevision + 1 && ledger.receipt(operation).status().equals("EXTERNAL_PENDING"),
                "Core export increments ownership revision once and awaits external acknowledgment");
        ledger.exportFromCore(exported, operation);
        check(ledger.entry(exported).revision() == oldRevision + 1, "Same-operation core export replay cannot produce another original");
        rejects(() -> ledger.exportFromCore(exported, UUID.randomUUID()), "Fresh operation cannot export an already exported original");
        rejects(() -> ledger.exportFromCore(sibling, operation), "Core export operation cannot be replayed against a different original");
        rejects(() -> ledger.lease(node, exported, 17, UUID.randomUUID()), "Exported original cannot regain a cached route lease");
        check(ledger.lease(node, exported, 17, oldLeaseOperation) == null, "Replaying the original lease cannot restore exported rights");
        ledger.acknowledge(operation);
        ledger.exportFromCore(exported, operation);
        check(ledger.entry(exported).revision() == oldRevision + 1 && ledger.receipt(operation).status().equals("ACKNOWLEDGED"),
                "Acknowledged core export replay leaves ownership and receipt unchanged");
        var restored = new CloudLedger(); restored.restore(ledger.entries(), ledger.nodes(), ledger.receipts());
        check(restored.entry(exported).status() == CloudLedger.Status.EXPORTED && !restored.node(node).slots().containsKey(17)
                        && restored.entry(sibling).lease().equals(siblingLease),
                "Restart retains exported ownership and unrelated live leases");
        UUID archived = UUID.randomUUID(); ledger.addEntry(archived); ledger.archive(archived, 1, true);
        ledger.exportFromCore(archived, UUID.randomUUID());
        check(ledger.entry(archived).status() == CloudLedger.Status.EXPORTED, "Core authority can also retrieve archived originals");
        UUID pending = UUID.randomUUID(); ledger.addPendingEntry(pending);
        rejects(() -> ledger.exportFromCore(pending, UUID.randomUUID()), "Core authority cannot duplicate an unacknowledged upload");
        ledger.needsReview(operation);
        rejects(() -> ledger.exportFromCore(exported, UUID.randomUUID()), "Core authority cannot bypass ambiguous external transfer quarantine");
        ledger.validate(); restored.validate();
    }
    private static void rejects(Runnable work, String message) {
        boolean rejected = false;
        try { work.run(); } catch (IllegalStateException | IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
