package com.gtl.enhancedcore.common.cloud.store;

import java.util.*;

/** Server-thread ownership kernel. Payloads and external inventories are deliberately outside this kernel. */
public final class CloudLedger {
    public enum Status { AVAILABLE, LEASED, ARCHIVED, EXPORTED, EXTERNAL_PENDING, NEEDS_REVIEW }
    public record Lease(UUID id, UUID entryId, UUID nodeId, int stableSlot, long generation) {}
    public record Receipt(UUID operation, String kind, UUID subject, String status) {}
    public record EntryState(UUID id, long revision, Status status, Lease lease) {}
    public record NodeState(UUID id, UUID owner, UUID library, UUID machineInstance, String mode,
                            long generation, boolean detached, Map<Integer, UUID> slots) {
        public NodeState { slots = slots instanceof PersistentSlots ? slots : Map.copyOf(slots); }
    }
    private final Map<UUID, EntryState> entries = new LinkedHashMap<>();
    private final Map<UUID, NodeState> nodes = new LinkedHashMap<>();
    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();
    private final Map<UUID, Object> activeEndpoints = new HashMap<>();
    public Collection<EntryState> entries() { return List.copyOf(entries.values()); }
    public Collection<NodeState> nodes() { return List.copyOf(nodes.values()); }
    public Collection<Receipt> receipts() { return List.copyOf(receipts.values()); }
    public EntryState entry(UUID id) { return Objects.requireNonNull(entries.get(id), "Unknown entry"); }
    public NodeState node(UUID id) { return Objects.requireNonNull(nodes.get(id), "Unknown node"); }
    public Receipt receipt(UUID operation) { return receipts.get(operation); }
    public void addEntry(UUID id) {
        if (entries.putIfAbsent(id, new EntryState(id, 1, Status.AVAILABLE, null)) != null)
            throw new IllegalStateException("Duplicate entry");
    }
    public void addPendingEntry(UUID id) {
        addEntry(id); entries.put(id, new EntryState(id, 1, Status.EXTERNAL_PENDING, null));
    }
    public void addNode(UUID id, UUID owner, UUID library) {
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(library);
        if (nodes.putIfAbsent(id, new NodeState(id, owner, library, null, "", 1, false, Map.of())) != null)
            throw new IllegalStateException("Duplicate node");
    }
    /** Existing claims survive list reordering; an unsupported old mode requires explicit release. */
    public String claimMode(UUID nodeId, UUID machine, List<String> orderedTypes) {
        Objects.requireNonNull(machine);
        NodeState node = node(nodeId);
        if (node.detached) throw new IllegalStateException("Detached node");
        var types = new LinkedHashSet<>(orderedTypes);
        types.removeIf(String::isBlank);
        if (node.machineInstance != null) {
            if (!node.machineInstance.equals(machine)) throw new IllegalStateException("Machine instance changed");
            return node.mode; // Never silently shuffle a live binding after a definition reload.
        }
        Set<String> occupied = new HashSet<>();
        nodes.values().stream().filter(n -> !n.detached && machine.equals(n.machineInstance))
                .forEach(n -> occupied.add(n.mode));
        for (String type : types) if (!occupied.contains(type)) {
            nodes.put(nodeId, new NodeState(node.id, node.owner, node.library, machine, type,
                    node.generation, false, node.slots));
            return type;
        }
        return "";
    }
    public Lease lease(UUID nodeId, UUID entryId, int slot, UUID operation) {
        if (slot < 0) throw new IllegalArgumentException("Negative stable slot");
        Receipt previous = receipts.get(operation);
        if (previous != null) {
            requireReplay(previous, "LEASE", entryId);
            EntryState existing = entry(entryId);
            return existing.lease != null && existing.lease.nodeId.equals(nodeId)
                    && existing.lease.stableSlot == slot ? existing.lease : null;
        }
        NodeState node = node(nodeId); EntryState entry = entry(entryId);
        if (node.detached || node.mode.isEmpty() || entry.status != Status.AVAILABLE || node.slots.containsKey(slot))
            throw new IllegalStateException("Unavailable node, entry or stable slot");
        Lease lease = new Lease(UUID.randomUUID(), entryId, nodeId, slot, node.generation);
        var slots = PersistentSlots.copyOf(node.slots).with(slot, entryId);
        nodes.put(nodeId, new NodeState(node.id, node.owner, node.library, node.machineInstance,
                node.mode, node.generation, false, slots));
        entries.put(entryId, new EntryState(entryId, Math.addExact(entry.revision, 1), Status.LEASED, lease));
        record(operation, "LEASE", entryId, "COMMITTED"); return lease;
    }
    /** Caller proves the slot has no outstanding dispatch before releasing a stale match. */
    public void releaseLease(UUID nodeId, UUID entryId, UUID operation) {
        Receipt previous = receipts.get(operation);
        if (previous != null) { requireReplay(previous, "RELEASE_LEASE", entryId); return; }
        NodeState node = node(nodeId); EntryState entry = entry(entryId);
        if (node.detached || entry.lease == null || !entry.lease.nodeId.equals(nodeId))
            throw new IllegalStateException("Lease belongs to another endpoint or was already released");
        var slots = PersistentSlots.copyOf(node.slots).without(entry.lease.stableSlot);
        nodes.put(nodeId, new NodeState(node.id, node.owner, node.library, node.machineInstance,
                node.mode, node.generation, false, slots));
        entries.put(entryId, new EntryState(entryId, Math.addExact(entry.revision, 1), Status.AVAILABLE, null));
        record(operation, "RELEASE_LEASE", entryId, "COMMITTED");
    }
    public void archive(UUID entryId, long revision, boolean archived) {
        EntryState entry = entry(entryId);
        if (entry.revision != revision || entry.lease != null
                || entry.status != (archived ? Status.AVAILABLE : Status.ARCHIVED))
            throw new IllegalStateException("Stale or unavailable entry");
        entries.put(entryId, new EntryState(entryId, Math.addExact(revision, 1),
                archived ? Status.ARCHIVED : Status.AVAILABLE, null));
    }
    /** Central authority may revoke the lease; outstanding jobs live in their independent node partition. */
    public void exportFromCore(UUID entryId, UUID operation) {
        if (receipts.containsKey(operation)) { requireReplay(receipts.get(operation), "EXPORT", entryId); return; }
        EntryState entry = entry(entryId);
        if (entry.status != Status.AVAILABLE && entry.status != Status.LEASED && entry.status != Status.ARCHIVED)
            throw new IllegalStateException("Original is not held by the library");
        if (entry.lease != null) {
            NodeState node = node(entry.lease.nodeId);
            nodes.put(node.id, new NodeState(node.id, node.owner, node.library, node.machineInstance,
                    node.mode, node.generation, node.detached, PersistentSlots.copyOf(node.slots).without(entry.lease.stableSlot)));
        }
        entries.put(entryId, new EntryState(entryId, Math.addExact(entry.revision, 1), Status.EXPORTED, null));
        record(operation, "EXPORT", entryId, "EXTERNAL_PENDING");
    }
    public void export(UUID entryId, long revision, UUID operation) {
        if (receipts.containsKey(operation)) { requireReplay(receipts.get(operation), "EXPORT", entryId); return; }
        EntryState entry = entry(entryId);
        if (entry.revision != revision || entry.status != Status.AVAILABLE || entry.lease != null)
            throw new IllegalStateException("Stale, leased or archived entry");
        entries.put(entryId, new EntryState(entryId, Math.addExact(revision, 1), Status.EXPORTED, null));
        record(operation, "EXPORT", entryId, "EXTERNAL_PENDING");
    }
    /** Caller must first persist the recoverable node snapshot. Unload must never call this. */
    public void detach(UUID nodeId, UUID operation) {
        if (receipts.containsKey(operation)) { requireReplay(receipts.get(operation), "DETACH", nodeId); return; }
        NodeState node = node(nodeId);
        if (node.detached) throw new IllegalStateException("Already detached");
        for (UUID id : node.slots.values()) {
            EntryState entry = entry(id);
            if (entry.lease == null || !entry.lease.nodeId.equals(nodeId))
                throw new IllegalStateException("Broken lease ownership");
        }
        for (UUID id : node.slots.values()) {
            EntryState entry = entry(id);
            entries.put(id, new EntryState(id, Math.addExact(entry.revision, 1), Status.AVAILABLE, null));
        }
        nodes.put(nodeId, new NodeState(node.id, node.owner, node.library, node.machineInstance,
                node.mode, Math.addExact(node.generation, 1), true, Map.of()));
        activeEndpoints.remove(nodeId); record(operation, "DETACH", nodeId, "COMMITTED");
    }
    /** Identity comparison prevents a copied block at the same coordinates from sharing execution. */
    public boolean activate(UUID nodeId, Object endpoint) {
        if (node(nodeId).detached) return false;
        Object old = activeEndpoints.putIfAbsent(nodeId, Objects.requireNonNull(endpoint));
        return old == null || old == endpoint;
    }
    public boolean active(UUID nodeId, Object endpoint) { return endpoint != null && activeEndpoints.get(nodeId) == endpoint; }
    public boolean isActive(UUID nodeId) { return activeEndpoints.containsKey(nodeId); }
    public void deactivate(UUID nodeId, Object endpoint) { if (active(nodeId, endpoint)) activeEndpoints.remove(nodeId); }
    public void record(UUID operation, String kind, UUID subject, String status) {
        Objects.requireNonNull(operation); Objects.requireNonNull(subject);
        Receipt prior = receipts.get(operation);
        if (prior != null) requireReplay(prior, kind, subject);
        else receipts.put(operation, new Receipt(operation, kind, subject, status));
    }
    public void acknowledge(UUID operation) {
        Receipt old = Objects.requireNonNull(receipts.get(operation), "Unknown operation");
        if (old.status.equals("NEEDS_REVIEW")) throw new IllegalStateException("Ambiguous external result requires explicit reconciliation");
        if (old.kind.equals("UPLOAD") && entry(old.subject).status == Status.EXTERNAL_PENDING) {
            EntryState entry = entry(old.subject);
            entries.put(entry.id, new EntryState(entry.id, Math.addExact(entry.revision, 1), Status.AVAILABLE, null));
        }
        receipts.put(operation, new Receipt(operation, old.kind, old.subject, "ACKNOWLEDGED"));
    }
    public void needsReview(UUID operation) {
        Receipt old = Objects.requireNonNull(receipts.get(operation), "Unknown operation");
        if (old.kind.equals("UPLOAD") || old.kind.equals("EXPORT")) {
            EntryState entry = entry(old.subject);
            if (entry.lease != null) throw new IllegalStateException("Cannot isolate a leased external transaction");
            entries.put(entry.id, new EntryState(entry.id, Math.addExact(entry.revision, 1), Status.NEEDS_REVIEW, null));
        }
        receipts.put(operation, new Receipt(operation, old.kind, old.subject, "NEEDS_REVIEW"));
    }
    public void reconcile(UUID operation, boolean externallyCommitted) {
        Receipt old = Objects.requireNonNull(receipts.get(operation), "Unknown operation");
        if (!old.status.equals("NEEDS_REVIEW") || !(old.kind.equals("UPLOAD") || old.kind.equals("EXPORT")))
            throw new IllegalStateException("Not an ambiguous external transfer");
        EntryState entry = entry(old.subject);
        boolean belongsInLibrary = old.kind.equals("UPLOAD") == externallyCommitted;
        entries.put(entry.id, new EntryState(entry.id, Math.addExact(entry.revision, 1),
                belongsInLibrary ? Status.AVAILABLE : Status.EXPORTED, null));
        receipts.put(operation, new Receipt(operation, old.kind, old.subject, externallyCommitted ? "ACKNOWLEDGED" : "ROLLED_BACK"));
    }
    public void restore(Collection<EntryState> restoredEntries, Collection<NodeState> restoredNodes,
                        Collection<Receipt> restoredReceipts) {
        if (!entries.isEmpty() || !nodes.isEmpty() || !receipts.isEmpty()) throw new IllegalStateException("Nonempty ledger");
        for (EntryState entry : restoredEntries) if (entries.putIfAbsent(entry.id, entry) != null) throw new IllegalArgumentException("Duplicate entry");
        for (NodeState node : restoredNodes) if (nodes.putIfAbsent(node.id, node) != null) throw new IllegalArgumentException("Duplicate node");
        for (Receipt receipt : restoredReceipts) if (receipts.putIfAbsent(receipt.operation, receipt) != null) throw new IllegalArgumentException("Duplicate operation");
        validate();
    }
    public void validate() {
        Set<String> claims = new HashSet<>();
        for (NodeState node : nodes.values()) {
            if (node.generation < 1 || node.mode == null || node.owner == null || node.library == null)
                throw new IllegalStateException("Invalid node");
            if (!node.detached && node.machineInstance != null && !node.mode.isEmpty()
                    && !claims.add(node.machineInstance + "/" + node.mode)) throw new IllegalStateException("Duplicate mode claim");
            if (node.detached && !node.slots.isEmpty()) throw new IllegalStateException("Detached leases");
            for (var slot : node.slots.entrySet()) {
                EntryState entry = entry(slot.getValue());
                if (slot.getKey() < 0 || entry.lease == null || !entry.lease.nodeId.equals(node.id)
                        || entry.lease.stableSlot != slot.getKey() || entry.lease.generation != node.generation)
                    throw new IllegalStateException("Slot ownership mismatch");
            }
        }
        for (EntryState entry : entries.values()) {
            if (entry.revision < 1 || (entry.status == Status.LEASED) != (entry.lease != null))
                throw new IllegalStateException("Invalid entry state");
            if (entry.lease != null) {
                NodeState node = node(entry.lease.nodeId);
                if (!entry.id.equals(node.slots.get(entry.lease.stableSlot))) throw new IllegalStateException("Orphan lease");
            }
        }
    }
    private static void requireReplay(Receipt receipt, String kind, UUID subject) {
        if (!receipt.kind.equals(kind) || !receipt.subject.equals(subject))
            throw new IllegalStateException("Operation identity collision");
    }
    /** Immutable binary trie: sparse slot insertion shares paths instead of copying the complete map. */
    private static final class PersistentSlots extends AbstractMap<Integer, UUID> {
        private record Branch(Branch zero, Branch one, UUID value) {}
        private final Branch root;
        private final int size;
        private PersistentSlots(Branch root, int size) { this.root = root; this.size = size; }
        static PersistentSlots copyOf(Map<Integer, UUID> map) {
            if (map instanceof PersistentSlots slots) return slots;
            PersistentSlots result = new PersistentSlots(null, 0);
            for (var entry : map.entrySet()) result = result.with(entry.getKey(), entry.getValue());
            return result;
        }
        PersistentSlots with(int key, UUID value) {
            return new PersistentSlots(insert(root, key, 30, value), containsKey(key) ? size : Math.addExact(size, 1));
        }
        PersistentSlots without(int key) {
            return containsKey(key) ? new PersistentSlots(remove(root, key, 30), size - 1) : this;
        }
        private static Branch remove(Branch branch, int key, int bit) {
            if (branch == null || bit < 0) return null;
            Branch zero = branch.zero, one = branch.one;
            if ((key & (1 << bit)) == 0) zero = remove(zero, key, bit - 1);
            else one = remove(one, key, bit - 1);
            return zero == null && one == null ? null : new Branch(zero, one, null);
        }
        private static Branch insert(Branch branch, int key, int bit, UUID value) {
            if (bit < 0) return new Branch(null, null, value);
            Branch zero = branch == null ? null : branch.zero, one = branch == null ? null : branch.one;
            if ((key & (1 << bit)) == 0) zero = insert(zero, key, bit - 1, value);
            else one = insert(one, key, bit - 1, value);
            return new Branch(zero, one, null);
        }
        @Override public UUID get(Object key) {
            if (!(key instanceof Integer number) || number < 0) return null;
            Branch branch = root;
            for (int bit = 30; bit >= 0 && branch != null; bit--)
                branch = (number & (1 << bit)) == 0 ? branch.zero : branch.one;
            return branch == null ? null : branch.value;
        }
        @Override public boolean containsKey(Object key) { return get(key) != null; }
        @Override public int size() { return size; }
        @Override public Set<Map.Entry<Integer, UUID>> entrySet() {
            LinkedHashSet<Map.Entry<Integer, UUID>> result = new LinkedHashSet<>(); collect(root, 30, 0, result);
            return Collections.unmodifiableSet(result);
        }
        private static void collect(Branch branch, int bit, int key, Set<Map.Entry<Integer, UUID>> target) {
            if (branch == null) return;
            if (bit < 0) { target.add(Map.entry(key, branch.value)); return; }
            collect(branch.zero, bit - 1, key, target); collect(branch.one, bit - 1, key | (1 << bit), target);
        }
    }
}
