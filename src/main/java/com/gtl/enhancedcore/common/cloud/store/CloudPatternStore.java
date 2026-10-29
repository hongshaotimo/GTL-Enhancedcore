package com.gtl.enhancedcore.common.cloud.store;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportIo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** World-scoped authority. SavedData snapshots do not form a transaction with AE or player saves. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CloudPatternStore {
    public static final int ENTRIES_PER_SHARD = 256;
    private static final String INDEX = "gtl_cloud_patterns_index";
    private final Index index;
    private final DimensionDataStorage storage;
    private final MinecraftServer server;
    private final Map<UUID, Library> libraries = new LinkedHashMap<>();
    private final Map<UUID, Node> nodes = new LinkedHashMap<>();
    /** Mechanical disks get one coalesced background flush at most per ten seconds. */
    private static final long SAVE_INTERVAL_NANOS = 10_000_000_000L;
    private long nextScheduledSave;
    private volatile boolean scheduledSavePending;
    private boolean stopping;

    /** Vanilla may request SavedData saves frequently. Coalesce only these requests;
     * explicit transaction checkpoints retain their immediate durability boundary. */
    private void saveScheduled() {
        if (stopping) { checkpoint(); return; }
        long now = System.nanoTime();
        if (scheduledSavePending || now < nextScheduledSave) return;
        scheduledSavePending = true; nextScheduledSave = now + SAVE_INTERVAL_NANOS;
        checkpointAsync().whenComplete((ignored, failure) -> {
            scheduledSavePending = false;
            if (failure != null) com.mojang.logging.LogUtils.getLogger().error("Cloud background save failed; dirty partitions retained", failure);
        });
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        var store = get(event.getServer().overworld()); store.stopping = true;
        CloudImportIo.drain(); store.checkpoint();
    }
    private CloudPatternStore(Index index, DimensionDataStorage storage, MinecraftServer server) {
        this.index = index; this.storage = storage; this.server = server;
    }
    public static CloudPatternStore get(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Cloud store requires server thread");
        DimensionDataStorage data = level.getServer().overworld().getDataStorage();
        Index index = data.computeIfAbsent(tag -> { Index value = new Index(); value.readChecked(tag); return value; }, () -> {
            Index value = new Index();
            if (Files.exists(level.getServer().getWorldPath(LevelResource.ROOT).resolve("data").resolve(INDEX + ".dat")))
                value.quarantine("Existing cloud index could not be read; original disk file preserved");
            return value;
        }, INDEX);
        if (index.runtime == null) index.runtime = new CloudPatternStore(index, data, level.getServer());
        return index.runtime;
    }
    private void check() {
        if (!server.isSameThread()) throw new IllegalStateException("Cloud mutation outside server thread");
        index.requireHealthy();
    }
    public boolean healthy() { return index.healthy(); }
    public String diagnostic() { return index.diagnostic(); }
    /** Only cloud records are flushed. This is not a commit of any AE/player/chunk save. */
    public void checkpoint() {
        List<PartitionSnapshot> snapshots = captureCheckpoint();
        try {
            persistSnapshots(snapshots);
            snapshots.forEach(snapshot -> snapshot.data.finishSnapshot(snapshot.write, null));
        } catch (RuntimeException failure) {
            snapshots.forEach(snapshot -> snapshot.data.finishSnapshot(snapshot.write, failure));
            throw failure;
        }
    }
    /** Captures live records on the server thread; completion also returns to that thread. Never join here. */
    public CompletableFuture<Void> checkpointAsync() {
        return checkpointAsync(null);
    }
    /** Upload scope excludes unrelated libraries. Dirty nodes of this authority remain mandatory:
     * its snapshot revision table must never get ahead of the node payloads on disk. */
    public CompletableFuture<Void> checkpointLibraryAsync(UUID libraryId) {
        Objects.requireNonNull(libraryId); return checkpointAsync(libraryId);
    }
    public void checkpointLibrary(UUID libraryId) {
        var snapshots = captureCheckpoint(Objects.requireNonNull(libraryId));
        try { persistSnapshots(snapshots); snapshots.forEach(s -> s.data.finishSnapshot(s.write, null)); }
        catch (RuntimeException failure) { snapshots.forEach(s -> s.data.finishSnapshot(s.write, failure)); throw failure; }
    }
    /** Retry retained dirty snapshots after an I/O failure; corrupt/unknown data is never unquarantined. */
    public void retryFailedWrites() {
        if (!server.isSameThread()) throw new IllegalStateException("Cloud retry outside server thread");
        var partitions = new ArrayList<CheckedData>();
        libraries.values().forEach(library -> partitions.addAll(library.shards.values()));
        partitions.addAll(nodes.values()); partitions.addAll(libraries.values()); partitions.add(index);
        if (partitions.stream().noneMatch(data -> data.ioFailed)) return;
        if (partitions.stream().anyMatch(data -> data.quarantined != null))
            throw new IllegalStateException("Corrupt cloud partition requires review");
        var directory = server.getWorldPath(LevelResource.ROOT).resolve("data");
        var snapshots = new ArrayList<PartitionSnapshot>();
        for (var library : libraries.values()) for (var shard : library.shards.entrySet())
            capturePartition(snapshots, shard.getValue(), directory.resolve(shardName(library.id, shard.getKey()) + ".dat"));
        for (var node : nodes.values()) capturePartition(snapshots, node, directory.resolve(nodeName(node.id) + ".dat"));
        for (var library : libraries.values()) capturePartition(snapshots, library, directory.resolve(libraryName(library.id) + ".dat"));
        capturePartition(snapshots, index, directory.resolve(INDEX + ".dat"));
        try { persistSnapshots(snapshots); snapshots.forEach(s -> s.data.finishSnapshot(s.write, null)); }
        catch (RuntimeException failure) { snapshots.forEach(s -> s.data.finishSnapshot(s.write, failure)); throw failure; }
    }
    private CompletableFuture<Void> checkpointAsync(UUID libraryId) {
        final List<PartitionSnapshot> snapshots;
        try { snapshots = captureCheckpoint(libraryId); }
        catch (RuntimeException failure) { return CompletableFuture.failedFuture(failure); }
        if (snapshots.isEmpty()) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> completed = new CompletableFuture<>();
        // The worker receives only detached NBT, paths and independent file-write sequencing state.
        List<CheckedData.FileSnapshot> writes = snapshots.stream().map(PartitionSnapshot::write).toList();
        CloudImportIo.run(() -> CheckedData.persistAll(writes)).whenComplete((ignored, failure) -> {
            try {
                server.execute(() -> {
                    try {
                        snapshots.forEach(snapshot -> snapshot.data.finishSnapshot(snapshot.write, failure));
                        if (failure == null) completed.complete(null); else completed.completeExceptionally(failure);
                    } catch (Throwable callbackFailure) { completed.completeExceptionally(callbackFailure); }
                });
            } catch (Throwable schedulingFailure) { completed.completeExceptionally(schedulingFailure); }
        });
        return completed;
    }
    private record PartitionSnapshot(CheckedData data, CheckedData.FileSnapshot write) {}
    private static void persistSnapshots(List<PartitionSnapshot> snapshots) {
        CheckedData.persistAll(snapshots.stream().map(PartitionSnapshot::write).toList());
    }
    private List<PartitionSnapshot> captureCheckpoint() {
        return captureCheckpoint(null);
    }
    private List<PartitionSnapshot> captureCheckpoint(UUID libraryId) {
        // The shared index can reference newly created libraries/nodes outside this scope.
        // Flush their dependencies too rather than publish an index ahead of its authority.
        if (libraryId != null && index.isDirty()) return captureCheckpoint(null);
        check(); Path directory = server.getWorldPath(LevelResource.ROOT).resolve("data");
        List<PartitionSnapshot> snapshots = new ArrayList<>();
        for (Library library : libraries.values()) {
            if (libraryId != null && !libraryId.equals(library.id)) continue;
            library.requireHealthy();
            for (var shard : library.shards.entrySet()) capturePartition(snapshots, shard.getValue(), directory.resolve(shardName(library.id, shard.getKey()) + ".dat"));
        }
        for (Node node : nodes.values()) if (libraryId == null || libraryId.equals(node.libraryId))
            capturePartition(snapshots, node, directory.resolve(nodeName(node.id) + ".dat"));
        for (Library library : libraries.values()) if (libraryId == null || libraryId.equals(library.id))
            capturePartition(snapshots, library, directory.resolve(libraryName(library.id) + ".dat"));
        capturePartition(snapshots, index, directory.resolve(INDEX + ".dat"));
        return snapshots;
    }
    private static void capturePartition(List<PartitionSnapshot> snapshots, CheckedData data, Path target) {
        CheckedData.FileSnapshot write = data.captureSnapshot(target);
        if (write != null) snapshots.add(new PartitionSnapshot(data, write));
    }
    public UUID defaultLibrary(UUID owner) {
        check(); Objects.requireNonNull(owner);
        UUID existing = index.defaults.get(owner);
        if (existing != null) return existing;
        UUID id = UUID.randomUUID(); Library library = new Library(id, owner);
        libraries.put(id, library); storage.set(libraryName(id), library); library.setDirty();
        index.owners.put(id, owner); index.defaults.put(owner, id); index.setDirty(); return id;
    }
    public List<Library> libraries(UUID actor) {
        check(); return index.owners.keySet().stream().map(this::library).filter(l -> l.authorized(actor)).toList();
    }
    public Library library(UUID id) {
        check(); UUID owner = Objects.requireNonNull(index.owners.get(id), "Unknown library");
        return libraries.computeIfAbsent(id, ignored -> storage.computeIfAbsent(tag -> {
            Library value = new Library(id, owner); value.readChecked(tag); return value;
        }, () -> { Library missing = new Library(id, owner); missing.quarantine("Missing library shard"); return missing; }, libraryName(id)));
    }
    public Node createNode(UUID owner, UUID libraryId, ResourceLocation dimension, long position) {
        check(); Library library = library(libraryId); library.requireActor(owner);
        UUID id = UUID.randomUUID(); library.ledger.addNode(id, owner, libraryId);
        Node node = new Node(id, libraryId, dimension, position);
        nodes.put(id, node); storage.set(nodeName(id), node); node.setDirty();
        index.nodeLibraries.put(id, libraryId);
        String address = dimension + "/" + position; index.nodeAddresses.put(id, address);
        index.endpointIndex.computeIfAbsent(address, ignored -> new LinkedHashSet<>()).add(id);
        index.setDirty(); library.changed(); return node;
    }
    public Node node(UUID id) {
        check(); UUID libraryId = Objects.requireNonNull(index.nodeLibraries.get(id), "Unknown node");
        Library library = library(libraryId); library.requireHealthy(); library.ledger.node(id);
        return nodes.computeIfAbsent(id, ignored -> storage.computeIfAbsent(tag -> {
            Node value = new Node(id, libraryId, new ResourceLocation("minecraft", "overworld"), 0);
            value.readChecked(tag); return value;
        }, () -> { Node missing = new Node(id, libraryId, new ResourceLocation("minecraft", "overworld"), 0);
            missing.quarantine("Missing node partition"); return missing; }, nodeName(id)));
    }
    public List<Node> nodesAt(ResourceLocation dimension, long position) {
        check(); return index.endpointIndex.getOrDefault(dimension + "/" + position, new LinkedHashSet<>()).stream().map(this::node).toList();
    }
    public boolean isNodeActive(UUID nodeId) { Node node = node(nodeId); return library(node.libraryId).ledger.isActive(nodeId); }
    /** A successful call owns exactly one supplied ItemStack; caller must persist its external debit. */
    public Entry upload(UUID libraryId, UUID actor, ItemStack source, CompoundTag slotConfig, UUID operation) {
        check(); Library library = library(libraryId); library.requireActor(actor);
        CloudLedger.Receipt prior = library.ledger.receipt(operation);
        if (prior != null) {
            if (!prior.kind().equals("UPLOAD")) throw new IllegalStateException("Operation collision");
            Entry existing = library.entry(prior.subject());
            if (!source.isEmpty() && (!ItemStack.isSameItemSameTags(source, existing.original)
                    || !slotConfig.equals(existing.configuration))) throw new IllegalStateException("Upload operation payload collision");
            return existing;
        }
        if (source.isEmpty()) throw new IllegalArgumentException("Empty pattern source");
        if (library.containsOriginal(source)) throw new DuplicatePatternException();
        if (com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.reservedForOtherOperation(libraryId, source, operation))
            throw new IllegalStateException("This original is reserved by an in-progress network import");
        UUID id = UUID.randomUUID(); int ordinal = library.nextOrdinal;
        Entry entry = new Entry(library, id, source.copyWithCount(1), slotConfig.copy(), Set.of());
        EntryShard shard = library.shard(ordinal / ENTRIES_PER_SHARD, true);
        shard.requireHealthy(); shard.entries.put(id, entry); shard.setDirty();
        library.entryPages.put(id, ordinal / ENTRIES_PER_SHARD); library.nextOrdinal = Math.addExact(ordinal, 1);
        library.orderedEntries.add(id);
        library.ledger.addPendingEntry(id); library.ledger.record(operation, "UPLOAD", id, "EXTERNAL_PENDING");
        library.indexOriginal(entry);
        library.catalogChanged(id); source.shrink(1); return entry;
    }
    /** Replay returns EMPTY, never another entity. Pending external receipts remain visible for reconciliation. */
    public ItemStack extract(UUID libraryId, UUID actor, UUID entryId, long expectedRevision, UUID operation) {
        check(); Library library = library(libraryId); library.requireActor(actor);
        CloudLedger.Receipt prior = library.ledger.receipt(operation);
        if (prior != null) {
            if (!prior.kind().equals("EXPORT") || !prior.subject().equals(entryId)) throw new IllegalStateException("Operation collision");
            return ItemStack.EMPTY;
        }
        Entry entry = library.entry(entryId); ItemStack result = entry.original();
        if (!com.gtl.enhancedcore.common.cloud.CloudLibraryService.catalysts(entry).isEmpty())
            throw new IllegalStateException("Remove this pattern's catalysts before exporting its original");
        library.ledger.export(entryId, expectedRevision, operation); library.unindexOriginal(entry); library.catalogChanged(entryId); return result;
    }
    public ItemStack extractFromCore(UUID libraryId, UUID actor, UUID entryId, UUID operation) {
        check(); Library library = library(libraryId); library.requireActor(actor);
        var prior = library.ledger.receipt(operation);
        if (prior != null) {
            if (!prior.kind().equals("EXPORT") || !prior.subject().equals(entryId)) throw new IllegalStateException("Operation collision");
            return ItemStack.EMPTY;
        }
        Entry entry = library.entry(entryId); var lease = entry.lease();
        library.ledger.exportFromCore(entryId, operation); library.unindexOriginal(entry);
        library.catalogChanged(entryId);
        if (lease != null) {
            try { com.gtl.enhancedcore.common.cloud.CloudPatternAllocator.originalRemoved(server.overworld(), libraryId, entryId, lease); }
            catch (RuntimeException notificationFailure) {
                org.slf4j.LoggerFactory.getLogger(CloudPatternStore.class).warn("Core withdrew pattern {}; provider notification failed after authority was revoked", entryId, notificationFailure);
            }
        }
        return entry.original();
    }
    public void acknowledge(UUID libraryId, UUID actor, UUID operation) {
        Library library = library(libraryId); library.requireActor(actor);
        var receipt = library.ledger.receipt(operation);
        library.ledger.acknowledge(operation);
        if (receipt != null && !receipt.status().equals("ACKNOWLEDGED")) {
            if (receipt.kind().equals("UPLOAD")) {
                library.catalogChanged(receipt.subject());
                // Prepare this one new row at upload time, never decode the entire library on reopen.
                // A derived display failure must not turn an already acknowledged transfer into a retry.
                try { com.gtl.enhancedcore.common.cloud.CloudLibraryService.prepareDisplay(library.entry(receipt.subject()), server.overworld()); }
                catch (RuntimeException failure) {
                    org.slf4j.LoggerFactory.getLogger(CloudPatternStore.class).warn("Display cache for uploaded pattern {} will be retried on demand", receipt.subject(), failure);
                }
            } else library.changed();
        }
    }
    public void needsReview(UUID libraryId, UUID actor, UUID operation) {
        Library library = library(libraryId); library.requireActor(actor); library.ledger.needsReview(operation);
        var receipt = library.ledger.receipt(operation);
        if (receipt != null && (receipt.kind().equals("UPLOAD") || receipt.kind().equals("EXPORT"))) library.indexOriginal(library.entry(receipt.subject()));
        library.catalogChanged(receipt == null ? null : receipt.subject());
    }
    /** Internal cancellation is legal only before any delivery; the batch caller proves that boundary. */
    public void cancelUndeliveredExport(UUID libraryId, UUID actor, UUID operation) {
        check(); Library library = library(libraryId); library.requireActor(actor);
        var receipt = library.ledger.receipt(operation);
        if (receipt == null || !receipt.kind().equals("EXPORT") || !receipt.status().equals("EXTERNAL_PENDING"))
            throw new IllegalStateException("Only an undelivered pending export may be cancelled");
        library.ledger.needsReview(operation); library.ledger.reconcile(operation, false);
        library.indexOriginal(library.entry(receipt.subject())); library.catalogChanged(receipt.subject());
    }
    public void resolveExternal(UUID libraryId, UUID actor, UUID operation, boolean externallyCommitted) {
        Library library = library(libraryId); library.requireActor(actor);
        if (!library.owner.equals(actor)) throw new SecurityException("External reconciliation requires owner");
        library.ledger.reconcile(operation, externallyCommitted);
        var entry = library.entry(library.ledger.receipt(operation).subject());
        if (entry.status() == CloudLedger.Status.EXPORTED) library.unindexOriginal(entry); else library.indexOriginal(entry);
        library.catalogChanged(entry.id());
    }
    /** Server-thread preflight; callers must perform the subsequent switch without yielding. */
    public boolean canClaimMode(UUID nodeId, UUID machineInstance, ResourceLocation requested) {
        check(); Node node = node(nodeId); node.requireHealthy();
        Objects.requireNonNull(machineInstance); Objects.requireNonNull(requested);
        UUID claimant = index.machineClaims.getOrDefault(machineInstance, Map.of()).get(requested.toString());
        return claimant == null || claimant.equals(nodeId);
    }
    public String claimMode(UUID nodeId, UUID machineInstance, List<ResourceLocation> orderedTypes) {
        check(); Node node = node(nodeId); node.requireHealthy(); Library library = library(node.libraryId);
        Map<String, UUID> claims = index.machineClaims.computeIfAbsent(machineInstance, ignored -> new LinkedHashMap<>());
        List<String> available = orderedTypes.stream().map(ResourceLocation::toString)
                .filter(type -> !claims.containsKey(type) || claims.get(type).equals(nodeId)).toList();
        String result = library.ledger.claimMode(nodeId, machineInstance, available);
        if (!result.isEmpty()) {
            UUID previous = claims.putIfAbsent(result, nodeId);
            if (previous != null && !previous.equals(nodeId)) throw new IllegalStateException("Cross-library mode claim conflict");
            if (previous == null) { index.setDirty(); library.changed(); }
        }
        return result;
    }
    public CloudLedger.Lease lease(UUID nodeId, UUID entryId, int stableSlot, UUID operation) {
        check(); Node node = node(nodeId); node.requireHealthy(); Library library = library(node.libraryId);
        library.entry(entryId); boolean replay = library.ledger.receipt(operation) != null;
        CloudLedger.Lease result = library.ledger.lease(nodeId, entryId, stableSlot, operation);
        if (!replay) library.locationChanged(); return result;
    }
    /** Executor service must prove this lease has no outstanding job before calling. */
    public void releaseLease(UUID nodeId, UUID entryId, UUID operation) {
        check(); Node node = node(nodeId); node.requireHealthy(); Library library = library(node.libraryId);
        boolean replay = library.ledger.receipt(operation) != null;
        library.ledger.releaseLease(nodeId, entryId, operation);
        if (!replay) library.allocationAndLocationChanged();
    }
    public boolean activate(UUID nodeId, Object endpointToken, ResourceLocation dimension, long position) {
        check(); Node node = node(nodeId); node.requireHealthy();
        if (node.detached()) return false;
        if (!node.dimension.equals(dimension) || node.position != position) return false;
        for (Node other : nodesAt(dimension, position))
            if (!other.id.equals(nodeId) && isNodeActive(other.id)) return false;
        verifySnapshot(node, library(node.libraryId));
        if (node.mode() != null && !nodeId.equals(index.machineClaims.getOrDefault(node.machineInstance(), Map.of()).get(node.mode().toString())))
            throw new IllegalStateException("Mode claim index mismatch; needs review");
        return library(node.libraryId).ledger.activate(nodeId, endpointToken);
    }
    public boolean active(UUID nodeId, Object endpointToken) {
        if (!healthy()) return false;
        UUID libraryId = index.nodeLibraries.get(nodeId);
        if (libraryId == null) return false;
        Library library = library(libraryId);
        return library.healthy() && node(nodeId).healthy() && library.ledger.active(nodeId, endpointToken);
    }
    public void deactivate(UUID nodeId, Object endpointToken) {
        // Unload only drops the live token. Failed I/O must still detach the
        // suppliers while retaining their last exact payload for a later save.
        Node loaded = nodes.get(nodeId);
        Library authority = loaded == null ? null : libraries.get(loaded.libraryId);
        if (loaded != null && authority != null && (!healthy() || !authority.healthy() || !loaded.healthy())) {
            if (authority.ledger.active(nodeId, endpointToken)) {
                loaded.capture();
                loaded.stateSupplier = null; loaded.inventorySupplier = null; loaded.originSupplier = null;
                loaded.setDirty(); authority.ledger.deactivate(nodeId, endpointToken);
            }
            return;
        }
        Node node = node(nodeId);
        if (library(node.libraryId).ledger.active(nodeId, endpointToken)) {
            unbindNodeSnapshot(node); library(node.libraryId).ledger.deactivate(nodeId, endpointToken);
        }
    }
    public void bindNodeSnapshot(Node node, Supplier<CompoundTag> ivState, Supplier<CompoundTag> inventory,
                                 Supplier<CompoundTag> sourceRoute) {
        check(); node.requireHealthy();
        if (node.detached()) throw new IllegalStateException("Detached snapshot source");
        node.stateSupplier = Objects.requireNonNull(ivState); node.inventorySupplier = Objects.requireNonNull(inventory);
        node.originSupplier = Objects.requireNonNull(sourceRoute); markNodeDirty(node);
    }
    public void markNodeDirty(Node node) {
        check(); node.requireHealthy();
        if (node.detached()) throw new IllegalStateException("Detached executor cannot dirty ledger");
        node.setDirty(); Library library = library(node.libraryId);
        if (library.snapshotRevisions.getOrDefault(node.id, -1L) != node.snapshotRevision) {
            library.snapshotRevisions.put(node.id, node.snapshotRevision); library.changed();
        }
    }
    /** Solidify while the executor still exists, then sever world/machine suppliers on unload. */
    public void unbindNodeSnapshot(Node node) {
        check(); node.requireHealthy();
        if (node.stateSupplier != null && !node.detached()) markNodeDirty(node);
        node.capture();
        node.stateSupplier = null; node.inventorySupplier = null; node.originSupplier = null;
        node.setDirty();
    }
    public void saveNodeSnapshot(UUID nodeId, CompoundTag ivState, CompoundTag inventory, CompoundTag sourceRoute) {
        check(); Node node = node(nodeId); node.requireHealthy();
        if (node.detached()) throw new IllegalStateException("Detached executor cannot update ledger");
        node.ivState = ivState.copy(); node.inventory = inventory.copy(); node.sourceRoute = sourceRoute.copy();
        node.snapshotRevision = Math.addExact(node.snapshotRevision, 1); node.setDirty();
        Library library = library(node.libraryId); library.snapshotRevisions.put(nodeId, node.snapshotRevision); library.changed();
    }
    public Recovery detach(UUID nodeId, UUID operation) {
        check(); Node node = node(nodeId); node.requireHealthy(); Library library = library(node.libraryId);
        CloudLedger.Receipt prior = library.ledger.receipt(operation);
        if (prior != null) {
            if (!prior.kind().equals("DETACH") || !prior.subject().equals(nodeId)) throw new IllegalStateException("Operation collision");
            return library.recoveries.get(nodeId);
        }
        if (node.stateSupplier != null) markNodeDirty(node);
        node.capture(); verifySnapshot(node, library);
        String recoveryStatus = emptyIvState(node.ivState) && node.inventory.isEmpty() ? "EMPTY" : "FROZEN";
        Recovery recovery = new Recovery(nodeId, node.owner(), node.machineInstance(), node.mode(), node.snapshotRevision, recoveryStatus, null);
        library.ledger.detach(nodeId, operation); library.recoveries.put(nodeId, recovery);
        if (recovery.machineInstance != null && recovery.mode != null) {
            Map<String, UUID> claims = index.machineClaims.get(recovery.machineInstance);
            if (claims != null) claims.remove(recovery.mode.toString(), nodeId);
            index.setDirty();
        }
        node.stateSupplier = null; node.inventorySupplier = null; node.originSupplier = null; node.setDirty();
        library.allocationAndLocationChanged(); return recovery;
    }
    /** Compatibility and source authorization are supplied by the server service, never client booleans. */
    public Node restore(UUID recoveryId, UUID targetNodeId, UUID actor, UUID expectedMachine,
                        ResourceLocation expectedMode, CompoundTag authorizedSourceRoute, UUID operation) {
        check(); Node source = node(recoveryId), target = node(targetNodeId);
        Library library = library(source.libraryId); library.requireActor(actor);
        if (!target.libraryId.equals(source.libraryId)) throw new IllegalStateException("Cross-library recovery");
        Recovery recovery = Objects.requireNonNull(library.recoveries.get(recoveryId), "Unknown recovery");
        CloudLedger.Receipt prior = library.ledger.receipt(operation);
        if (prior != null) {
            if (!prior.kind().equals("RESTORE") || !prior.subject().equals(recoveryId)) throw new IllegalStateException("Operation collision");
            if (!targetNodeId.equals(recovery.restoredTo)) throw new IllegalStateException("Recovery target changed");
            return target;
        }
        source.requireHealthy(); target.requireHealthy(); verifySnapshot(source, library);
        target.capture(); verifySnapshot(target, library);
        if (!recovery.status.equals("FROZEN") || target.detached() || !emptyIvState(target.ivState) || !target.inventory.isEmpty()
                || !Objects.equals(target.machineInstance(), expectedMachine) || !expectedMode.equals(target.mode())
                || !expectedMode.equals(recovery.mode) || !source.sourceRoute.equals(authorizedSourceRoute))
            throw new IllegalStateException("Recovery compatibility or original source route rejected");
        target.ivState = source.ivState.copy(); target.inventory = source.inventory.copy(); target.sourceRoute = source.sourceRoute.copy();
        target.snapshotRevision = Math.addExact(target.snapshotRevision, 1); target.setDirty();
        library.snapshotRevisions.put(target.id, target.snapshotRevision);
        library.recoveries.put(recoveryId, new Recovery(recovery.nodeId, recovery.owner, recovery.machineInstance,
                recovery.mode, recovery.snapshotRevision, "RESTORED", targetNodeId));
        library.ledger.record(operation, "RESTORE", recoveryId, "COMMITTED"); library.changed(); return target;
    }
    private void verifySnapshot(Node node, Library library) {
        if (library.snapshotRevisions.getOrDefault(node.id, 0L) != node.snapshotRevision)
            throw new IllegalStateException("Node partition revision mismatch; needs review");
    }
    public static boolean emptyIvState(CompoundTag state) {
        return state.isEmpty() || state.getInt("version") == 1 && state.contains("jobs", Tag.TAG_LIST)
                && state.getList("jobs", Tag.TAG_COMPOUND).isEmpty();
    }
    public record Recovery(UUID nodeId, UUID owner, UUID machineInstance, ResourceLocation mode,
                           long snapshotRevision, String status, UUID restoredTo) {}

    /** A normal player rejection, never an uncertain external-transfer failure. */
    public static final class DuplicatePatternException extends IllegalArgumentException {
        public DuplicatePatternException() { super("Identical pattern already exists in this library"); }
    }
    private static CompoundTag originalKey(ItemStack stack) { return stack.copyWithCount(1).save(new CompoundTag()); }

    public final class Library extends CheckedData {
        @Override protected CloudPatternStore ownerStore() { return CloudPatternStore.this; }
        private final UUID id;
        private UUID owner;
        private CompoundTag meAnchor = new CompoundTag();
        private CompoundTag openingCatalog = new CompoundTag();
        private long revision, catalogRevision, allocationRevision, locationRevision;
        private final CloudAllocationChanges allocationChanges = new CloudAllocationChanges();
        private int nextOrdinal;
        private final Set<UUID> delegates = new LinkedHashSet<>();
        private final Map<UUID, Integer> entryPages = new LinkedHashMap<>();
        private final List<UUID> orderedEntries = new ArrayList<>();
        private final Map<UUID, Integer> originalHashes = new HashMap<>();
        private final Map<Integer, Set<UUID>> originalIndex = new HashMap<>();
        private boolean originalIndexReady;
        private final Map<Integer, EntryShard> shards = new HashMap<>();
        private final Map<UUID, Long> snapshotRevisions = new LinkedHashMap<>();
        private final Map<UUID, Recovery> recoveries = new LinkedHashMap<>();
        private CloudLedger ledger = new CloudLedger();
        private CompoundTag catalysts = new CompoundTag();
        private CompoundTag importedCatalystGroups = new CompoundTag();
        private UUID importBatchActor;
        private int importBatchDepth, importBatchTick;
        private boolean importBatchChanged;
        private Library(UUID id, UUID owner) { this.id = id; this.owner = owner; }
        /** Use only within one server tick and always close in finally. Revisions still change per mutation. */
        public void beginImportBatch(UUID actor) {
            requireActor(actor); int tick = server.getTickCount();
            if (importBatchDepth != 0 && (importBatchTick != tick || !actor.equals(importBatchActor)))
                throw new IllegalStateException("Import batch crossed tick or actor boundary");
            if (importBatchDepth == 0) { importBatchActor = actor; importBatchTick = tick; }
            importBatchDepth = Math.addExact(importBatchDepth, 1);
        }
        public void endImportBatch() {
            check(); if (importBatchDepth == 0) throw new IllegalStateException("No import batch is open");
            boolean crossedTick = importBatchTick != server.getTickCount();
            if (--importBatchDepth == 0) {
                importBatchActor = null; boolean notify = importBatchChanged; importBatchChanged = false;
                if (notify) com.gtl.enhancedcore.common.cloud.CloudPatternAllocator.libraryChanged(server.overworld(), id);
            }
            if (crossedTick) throw new IllegalStateException("Import batch crossed server tick");
        }
        public UUID id() { return id; }
        public UUID owner() { return owner; }
        /** Derived first-page directory only, never inventory, permission or a live connection. */
        public CompoundTag openingCatalog() {
            requireHealthy();
            return hasOpeningCatalog() ? openingCatalog.copy() : new CompoundTag();
        }
        public boolean hasOpeningCatalog() { return openingCatalog.getInt("schema") == 1 && openingCatalog.getLong("catalogRevision") == catalogRevision; }
        public void cacheOpeningCatalog(CompoundTag value) {
            check(); requireHealthy();
            openingCatalog = value.copy(); openingCatalog.putInt("schema", 1);
            openingCatalog.putLong("catalogRevision", catalogRevision); setDirty();
        }
        /** Physical AE node address used only to recover this library's live network association. */
        public CompoundTag meAnchor() { requireHealthy(); return meAnchor.copy(); }
        public boolean bindMeAnchor(CompoundTag anchor) {
            check(); requireHealthy();
            if (anchor == null || anchor.isEmpty()) return false;
            if (!meAnchor.isEmpty()) return meAnchor.equals(anchor);
            meAnchor = anchor.copy(); changed(); return true;
        }
        public long revision() { return revision; }
        public long catalogRevision() { return catalogRevision; }
        public long allocationRevision() { return allocationRevision; }
        public List<UUID> allocationChangesSince(long revision) { return allocationChanges.since(revision); }
        public List<UUID> recentAllocationChanges() { return allocationChanges.recent(); }
        public long locationRevision() { return locationRevision; }
        private long countRevision = Long.MIN_VALUE;
        private int storedCount;
        public int storedCount() {
            requireHealthy();
            if (countRevision != catalogRevision) {
                storedCount = (int)ledger.entries().stream().filter(e -> e.status() != CloudLedger.Status.EXPORTED).count();
                countRevision = catalogRevision;
            }
            return storedCount;
        }
        public int entryCount() { requireHealthy(); return orderedEntries.size(); }
        /** Hashes only select candidates. Exact complete serialized stacks decide equality. */
        public boolean containsOriginal(ItemStack pattern) {
            check(); requireHealthy();
            if (pattern.isEmpty()) return false;
            ensureOriginalIndex();
            CompoundTag key = originalKey(pattern);
            for (UUID candidate : originalIndex.getOrDefault(key.hashCode(), Set.of())) {
                if (ledger.entry(candidate).status() != CloudLedger.Status.EXPORTED
                        && key.equals(originalKey(entry(candidate).original))) return true;
            }
            return false;
        }
        public void requireUniqueOriginal(ItemStack pattern) {
            if (containsOriginal(pattern)) throw new DuplicatePatternException();
            if (com.gtl.enhancedcore.common.cloud.transfer.CloudNetworkPatternImport.reservedForOtherOperation(id, pattern, null))
                throw new IllegalStateException("This original is reserved by an in-progress network import");
        }
        private void ensureOriginalIndex() {
            if (originalIndexReady) return;
            for (UUID entryId : orderedEntries) {
                int hash = originalHashes.computeIfAbsent(entryId, id -> originalKey(entry(id).original).hashCode());
                if (ledger.entry(entryId).status() != CloudLedger.Status.EXPORTED)
                    originalIndex.computeIfAbsent(hash, ignored -> new LinkedHashSet<>()).add(entryId);
            }
            originalIndexReady = true;
        }
        private void indexOriginal(Entry entry) {
            int hash = originalKey(entry.original).hashCode(); originalHashes.put(entry.id, hash);
            if (originalIndexReady) originalIndex.computeIfAbsent(hash, ignored -> new LinkedHashSet<>()).add(entry.id);
        }
        private void unindexOriginal(Entry entry) {
            if (!originalIndexReady) return;
            int hash = originalHashes.getOrDefault(entry.id, originalKey(entry.original).hashCode());
            Set<UUID> bucket = originalIndex.get(hash);
            if (bucket != null) { bucket.remove(entry.id); if (bucket.isEmpty()) originalIndex.remove(hash); }
        }
        /** A bounded directory window; no ItemStack decode or complete directory copy. */
        public List<UUID> entryIdPage(int offset, int limit) {
            requireHealthy();
            if (offset < 0 || limit < 0 || limit > 4096) throw new IllegalArgumentException("Invalid directory window");
            int from = Math.min(offset, orderedEntries.size());
            int to = (int)Math.min(orderedEntries.size(), (long)from + limit);
            return List.copyOf(orderedEntries.subList(from, to));
        }
        public Set<UUID> delegates() { return Set.copyOf(delegates); }
        public boolean authorized(UUID actor) { return owner.equals(actor) || delegates.contains(actor); }
        public void grant(UUID actor, UUID delegate, boolean allowed) {
            check(); requireHealthy(); if (!owner.equals(actor)) throw new SecurityException("Library owner required");
            boolean updated = allowed ? delegates.add(delegate) : delegates.remove(delegate);
            if (updated) changed();
        }
        private void requireActor(UUID actor) { check(); requireHealthy(); if (!authorized(actor)) throw new SecurityException("Cloud library denied"); }
        public CompoundTag catalysts() { requireHealthy(); return catalysts.copy(); }
        /** Imported source groups own physical stock once; entries contain only a group UUID reference. */
        public CompoundTag importedCatalystGroups(UUID actor) { requireActor(actor); return importedCatalystGroups.copy(); }
        public CompoundTag getImportedCatalysts(UUID actor, UUID group) {
            requireActor(actor); return importedCatalystGroups.getCompound(Objects.requireNonNull(group).toString()).copy();
        }
        public void setImportedCatalysts(UUID actor, UUID group, CompoundTag contents) {
            requireActor(actor); String key = Objects.requireNonNull(group).toString(); Objects.requireNonNull(contents);
            if (importedCatalystGroups.getCompound(key).equals(contents)) return;
            if (contents.isEmpty()) importedCatalystGroups.remove(key); else importedCatalystGroups.put(key, contents.copy());
            catalogChanged();
        }
        public void setCatalysts(UUID actor, CompoundTag contents) {
            requireActor(actor); if (catalysts.equals(contents)) return; catalysts = contents.copy(); allocationChanged();
        }
        public List<Entry> entries() { requireHealthy(); return entryPages.keySet().stream().map(this::entry).toList(); }
        public List<UUID> entryIds() { requireHealthy(); return List.copyOf(entryPages.keySet()); }
        public Entry entry(UUID entryId) {
            requireHealthy(); Integer page = Objects.requireNonNull(entryPages.get(entryId), "Unknown entry");
            EntryShard shard = shard(page, false); shard.requireHealthy();
            Entry entry = shard.entries.get(entryId);
            if (entry == null) { quarantine("Directory refers to missing entry payload"); throw new IllegalStateException(diagnostic()); }
            ledger.entry(entryId); return entry;
        }
        public Collection<Recovery> recoveries() { requireHealthy(); return List.copyOf(recoveries.values()); }
        public CloudLedger.Receipt receipt(UUID operation) { return ledger.receipt(operation); }
        public Collection<CloudLedger.Receipt> receipts() { return ledger.receipts(); }
        public void archive(UUID actor, UUID entryId, long expectedRevision, boolean archived) {
            requireActor(actor); ledger.archive(entryId, expectedRevision, archived); catalogChanged(entryId);
        }
        private void changed() { requireHealthy(); revision = Math.addExact(revision, 1); setDirty(); }
        private void allocationChanged() { allocationChanged(null); }
        private void allocationChanged(UUID entryId) {
            allocationRevision = Math.addExact(allocationRevision, 1); changed();
            allocationChanges.record(allocationRevision, entryId);
            if (importBatchDepth != 0) importBatchChanged = true;
            else com.gtl.enhancedcore.common.cloud.CloudPatternAllocator.libraryChanged(server.overworld(), id);
        }
        private void locationChanged() { locationRevision = Math.addExact(locationRevision, 1); changed(); }
        private void allocationAndLocationChanged() { locationRevision = Math.addExact(locationRevision, 1); allocationChanged(); }
        private void catalogChanged() { catalogChanged(null); }
        private void catalogChanged(UUID entryId) {
            catalogRevision = Math.addExact(catalogRevision, 1);
            com.gtl.enhancedcore.common.cloud.CloudLibraryService.catalogChanged(this, entryId);
            allocationChanged(entryId);
        }
        private EntryShard shard(int page, boolean create) {
            return shards.computeIfAbsent(page, ignored -> {
                if (create && !entryPages.containsValue(page)) {
                    EntryShard value = new EntryShard(this); storage.set(shardName(id, page), value); value.setDirty(); return value;
                }
                return storage.computeIfAbsent(tag -> { EntryShard value = new EntryShard(this); value.readChecked(tag); return value; },
                        () -> { EntryShard value = new EntryShard(this); value.quarantine("Missing original payload shard"); return value; }, shardName(id, page));
            });
        }
        @Override protected CompoundTag writeData() {
            ledger.validate(); CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putUUID("owner", owner);
            tag.putLong("revision", revision); tag.putLong("catalogRevision", catalogRevision); tag.putLong("allocationRevision", allocationRevision);
            tag.putLong("locationRevision", locationRevision);
            if (!meAnchor.isEmpty()) tag.put("meAnchor", meAnchor.copy());
            if (!openingCatalog.isEmpty()) tag.put("openingCatalog", openingCatalog.copy());
            tag.putInt("nextOrdinal", nextOrdinal); tag.put("catalysts", catalysts.copy());
            tag.put("importedCatalystGroups", importedCatalystGroups.copy());
            tag.put("delegates", uuidList(delegates)); ListTag directory = new ListTag();
            entryPages.forEach((entry, page) -> {
                CompoundTag row = new CompoundTag(); row.putUUID("id", entry); row.putInt("page", page);
                Integer hash = originalHashes.get(entry); if (hash != null) row.putInt("originalHash", hash); directory.add(row);
            });
            tag.put("directory", directory); tag.put("ledger", writeLedger(ledger)); ListTag snapshots = new ListTag();
            snapshotRevisions.forEach((node, version) -> { CompoundTag row = new CompoundTag(); row.putUUID("node", node); row.putLong("revision", version); snapshots.add(row); });
            tag.put("snapshots", snapshots); ListTag records = new ListTag();
            for (Recovery r : recoveries.values()) {
                CompoundTag row = new CompoundTag(); row.putUUID("node", r.nodeId); row.putUUID("owner", r.owner);
                if (r.machineInstance != null) row.putUUID("machine", r.machineInstance);
                row.putString("mode", r.mode == null ? "" : r.mode.toString()); row.putLong("snapshotRevision", r.snapshotRevision);
                row.putString("status", r.status); if (r.restoredTo != null) row.putUUID("target", r.restoredTo); records.add(row);
            }
            tag.put("recoveries", records); return tag;
        }
        @Override protected void readData(CompoundTag tag) {
            if (!id.equals(tag.getUUID("id")) || !owner.equals(tag.getUUID("owner"))) throw new IllegalArgumentException("Library identity mismatch");
            revision = tag.getLong("revision"); catalogRevision = tag.getLong("catalogRevision"); allocationRevision = tag.getLong("allocationRevision");
            allocationChanges.reset(allocationRevision);
            locationRevision = tag.getLong("locationRevision");
            meAnchor = tag.getCompound("meAnchor").copy();
            openingCatalog = tag.getCompound("openingCatalog").copy();
            if (!meAnchor.isEmpty() && (!meAnchor.contains("dimension", Tag.TAG_STRING)
                    || !meAnchor.contains("position", Tag.TAG_LONG) || !meAnchor.contains("blockEntity", Tag.TAG_STRING)
                    || !meAnchor.contains("side", Tag.TAG_INT))) throw new IllegalArgumentException("Invalid ME network anchor");
            nextOrdinal = tag.getInt("nextOrdinal"); catalysts = tag.getCompound("catalysts").copy();
            importedCatalystGroups = tag.getCompound("importedCatalystGroups").copy();
            for (String group : importedCatalystGroups.getAllKeys()) {
                UUID.fromString(group);
                if (!importedCatalystGroups.contains(group, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid imported catalyst group");
            }
            if (revision < 0 || catalogRevision < 0 || allocationRevision < 0 || locationRevision < 0 || nextOrdinal < 0)
                throw new IllegalArgumentException("Invalid library counters");
            delegates.addAll(readUuids(tag.getList("delegates", Tag.TAG_COMPOUND)));
            for (Tag value : tag.getList("directory", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)value; UUID entry = row.getUUID("id"); int page = row.getInt("page");
                if (page < 0 || page >= (nextOrdinal + (long)ENTRIES_PER_SHARD - 1) / ENTRIES_PER_SHARD
                        || entryPages.putIfAbsent(entry, page) != null) throw new IllegalArgumentException("Invalid entry directory");
                orderedEntries.add(entry);
                if (row.contains("originalHash", Tag.TAG_INT)) originalHashes.put(entry, row.getInt("originalHash"));
            }
            ledger = readLedger(tag.getCompound("ledger"));
            if (ledger.entries().size() != entryPages.size() || ledger.entries().stream().anyMatch(e -> !entryPages.containsKey(e.id())))
                throw new IllegalArgumentException("Ledger and payload directory disagree");
            for (Tag value : tag.getList("snapshots", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)value; if (snapshotRevisions.putIfAbsent(row.getUUID("node"), row.getLong("revision")) != null)
                    throw new IllegalArgumentException("Duplicate snapshot pointer");
            }
            for (Tag value : tag.getList("recoveries", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)value; UUID node = row.getUUID("node");
                Recovery recovery = new Recovery(node, row.getUUID("owner"), row.hasUUID("machine") ? row.getUUID("machine") : null,
                        row.getString("mode").isEmpty() ? null : new ResourceLocation(row.getString("mode")), row.getLong("snapshotRevision"),
                        row.getString("status"), row.hasUUID("target") ? row.getUUID("target") : null);
                if (!Set.of("EMPTY", "FROZEN", "RESTORED", "NEEDS_REVIEW").contains(recovery.status) || recoveries.putIfAbsent(node, recovery) != null)
                    throw new IllegalArgumentException("Invalid recovery record");
                ledger.node(node);
            }
        }
    }

    public final class Entry {
        private final Library library;
        private final UUID id;
        private final ItemStack original;
        private final CompoundTag configuration;
        private CompoundTag catalysts = new CompoundTag();
        private long catalystRevision;
        // Derived display data only: never used for authority, inventory or admission.
        private CompoundTag displayMetadata = new CompoundTag();
        private Set<ResourceLocation> recipeTypes;
        private Entry(Library library, UUID id, ItemStack original, CompoundTag configuration, Set<ResourceLocation> types) {
            this.library = library; this.id = id; this.original = original.copy(); this.configuration = configuration.copy(); this.recipeTypes = Set.copyOf(types);
        }
        public UUID id() { return id; }
        public ItemStack original() { return original.copy(); }
        public CompoundTag configuration() { return configuration.copy(); }
        public CompoundTag displayMetadata() { library.requireHealthy(); return displayMetadata.copy(); }
        public void cacheDisplayMetadata(CompoundTag value) {
            check(); library.requireHealthy();
            if (displayMetadata.equals(value)) return;
            displayMetadata = value.copy();
            library.shard(library.entryPages.get(id), false).setDirty();
        }
        public CompoundTag importedSharedCatalysts() {
            library.requireHealthy();
            return configuration.hasUUID("importCatalystGroup")
                    ? library.importedCatalystGroups.getCompound(configuration.getUUID("importCatalystGroup").toString()).copy()
                    : new CompoundTag();
        }
        public CompoundTag catalysts() { library.requireHealthy(); return catalysts.copy(); }
        public long catalystRevision() { return catalystRevision; }
        /** The upload receipt is still unpublished; assign escrow before ACK makes the original leasable. */
        public void initializeImportedCatalysts(UUID actor, UUID operation, CompoundTag state) {
            library.requireActor(actor); var receipt = library.ledger.receipt(operation);
            if (receipt == null || !receipt.kind().equals("UPLOAD") || !receipt.subject().equals(id)
                    || status() != CloudLedger.Status.EXTERNAL_PENDING || !receipt.status().equals("EXTERNAL_PENDING"))
                throw new IllegalStateException("Catalyst initialization requires this unpublished upload receipt");
            if (catalysts.equals(state)) return;
            if (!catalysts.isEmpty()) throw new IllegalStateException("Imported catalyst initialization payload differs");
            catalysts = state.copy(); catalystRevision = Math.addExact(catalystRevision, 1);
            library.shard(library.entryPages.get(id), false).setDirty(); library.catalogChanged(id);
        }
        public void setCatalysts(UUID actor, CompoundTag state) {
            library.requireActor(actor);
            if (status() != CloudLedger.Status.AVAILABLE && status() != CloudLedger.Status.LEASED && status() != CloudLedger.Status.ARCHIVED && status() != CloudLedger.Status.EXPORTED)
                throw new IllegalStateException("Pattern is not available for catalyst changes");
            if (catalysts.equals(state)) return;
            catalysts = state.copy(); catalystRevision = Math.addExact(catalystRevision, 1);
            library.shard(library.entryPages.get(id), false).setDirty(); library.catalogChanged(id);
        }
        public long revision() { return library.ledger.entry(id).revision(); }
        public CloudLedger.Status status() { return library.ledger.entry(id).status(); }
        public boolean archived() { return status() == CloudLedger.Status.ARCHIVED; }
        public CloudLedger.Lease lease() { return library.ledger.entry(id).lease(); }
        public Set<ResourceLocation> recipeTypes() { return recipeTypes; }
        public List<Integer> stableSlots() { CloudLedger.Lease lease = lease(); return lease == null ? List.of() : List.of(lease.stableSlot()); }
        public void setRecipeTypes(UUID actor, Set<ResourceLocation> types) {
            library.requireActor(actor); Set<ResourceLocation> next = Set.copyOf(types);
            if (recipeTypes.equals(next)) return;
            recipeTypes = next;
            displayMetadata = new CompoundTag();
            library.shard(library.entryPages.get(id), false).setDirty(); library.catalogChanged(id);
        }
    }
    public final class Node extends CheckedData {
        @Override protected CloudPatternStore ownerStore() { return CloudPatternStore.this; }
        private final UUID id, libraryId;
        private ResourceLocation dimension;
        private long position, snapshotRevision;
        private CompoundTag ivState = new CompoundTag(), inventory = new CompoundTag(), sourceRoute = new CompoundTag();
        private Supplier<CompoundTag> stateSupplier, inventorySupplier, originSupplier;
        private Node(UUID id, UUID libraryId, ResourceLocation dimension, long position) { this.id = id; this.libraryId = libraryId; this.dimension = dimension; this.position = position; }
        private CloudLedger.NodeState state() { return library(libraryId).ledger.node(id); }
        public UUID id() { return id; }
        public UUID libraryId() { return libraryId; }
        public UUID owner() { return state().owner(); }
        public UUID machineInstance() { return state().machineInstance(); }
        public ResourceLocation mode() { return state().mode().isEmpty() ? null : new ResourceLocation(state().mode()); }
        public long generation() { return state().generation(); }
        public boolean detached() { return state().detached(); }
        public ResourceLocation dimension() { return dimension; }
        public long position() { return position; }
        public long snapshotRevision() { return snapshotRevision; }
        public Map<Integer, UUID> slots() { return state().slots(); }
        public List<CloudLedger.Lease> leases() { return slots().values().stream().map(entry -> library(libraryId).ledger.entry(entry).lease()).toList(); }
        public CompoundTag ivState() { requireHealthy(); verifySnapshot(this, library(libraryId)); return ivState.copy(); }
        public CompoundTag inventory() { requireHealthy(); verifySnapshot(this, library(libraryId)); return inventory.copy(); }
        public CompoundTag sourceRoute() { requireHealthy(); return sourceRoute.copy(); }
        public CompoundTag snapshot() { requireHealthy(); verifySnapshot(this, library(libraryId)); return writeData(); }
        private void capture() {
            if (stateSupplier != null) {
                CompoundTag nextState = Objects.requireNonNull(stateSupplier.get()).copy();
                CompoundTag nextInventory = Objects.requireNonNull(inventorySupplier.get()).copy();
                CompoundTag nextOrigin = Objects.requireNonNull(originSupplier.get()).copy();
                if (!ivState.equals(nextState) || !inventory.equals(nextInventory) || !sourceRoute.equals(nextOrigin)) {
                    ivState = nextState; inventory = nextInventory; sourceRoute = nextOrigin;
                    snapshotRevision = Math.addExact(snapshotRevision, 1); setDirty();
                    // Capture is also used while unloading an I/O-failed partition. Preserve
                    // its newest exact payload without re-entering health-gated lookup APIs.
                    var library = Objects.requireNonNull(libraries.get(libraryId));
                    library.snapshotRevisions.put(id, snapshotRevision);
                    library.revision = Math.addExact(library.revision, 1); library.setDirty();
                }
            }
        }
        @Override protected CompoundTag writeData() {
            capture();
            CompoundTag tag = new CompoundTag(); tag.putUUID("id", id); tag.putUUID("library", libraryId);
            tag.putString("dimension", dimension.toString()); tag.putLong("position", position); tag.putLong("snapshotRevision", snapshotRevision);
            tag.put("ivState", ivState.copy()); tag.put("inventory", inventory.copy()); tag.put("sourceRoute", sourceRoute.copy()); return tag;
        }
        @Override protected void readData(CompoundTag tag) {
            if (!id.equals(tag.getUUID("id")) || !libraryId.equals(tag.getUUID("library"))) throw new IllegalArgumentException("Node identity mismatch");
            dimension = new ResourceLocation(tag.getString("dimension")); position = tag.getLong("position"); snapshotRevision = tag.getLong("snapshotRevision");
            if (snapshotRevision < 0) throw new IllegalArgumentException("Negative snapshot revision");
            ivState = tag.getCompound("ivState").copy(); inventory = tag.getCompound("inventory").copy(); sourceRoute = tag.getCompound("sourceRoute").copy();
        }
    }
    private final class EntryShard extends CheckedData {
        @Override protected CloudPatternStore ownerStore() { return CloudPatternStore.this; }
        private final Library library;
        private final Map<UUID, Entry> entries = new LinkedHashMap<>();
        private EntryShard(Library library) { this.library = library; }
        @Override protected CompoundTag writeData() {
            CompoundTag tag = new CompoundTag(); ListTag rows = new ListTag();
            for (Entry entry : entries.values()) {
                CompoundTag row = new CompoundTag(); row.putUUID("id", entry.id); row.put("original", entry.original.save(new CompoundTag()));
                row.put("configuration", entry.configuration.copy()); row.put("catalysts", entry.catalysts.copy());
                if (!entry.displayMetadata.isEmpty()) row.put("displayMetadata", entry.displayMetadata.copy());
                row.putLong("catalystRevision", entry.catalystRevision); ListTag types = new ListTag();
                entry.recipeTypes.stream().sorted().forEach(type -> types.add(StringTag.valueOf(type.toString()))); row.put("types", types); rows.add(row);
            }
            tag.put("entries", rows); return tag;
        }
        @Override protected void readData(CompoundTag tag) {
            ListTag rows = tag.getList("entries", Tag.TAG_COMPOUND);
            if (rows.size() > ENTRIES_PER_SHARD) throw new IllegalArgumentException("Oversized payload shard");
            for (Tag value : rows) {
                CompoundTag row = (CompoundTag)value; UUID id = row.getUUID("id"); ItemStack stack = ItemStack.of(row.getCompound("original"));
                if (stack.isEmpty() || stack.getCount() != 1) throw new IllegalArgumentException("Invalid original stack");
                Set<ResourceLocation> types = new LinkedHashSet<>();
                row.getList("types", Tag.TAG_STRING).forEach(type -> types.add(new ResourceLocation(type.getAsString())));
                Entry entry = new Entry(library, id, stack, row.getCompound("configuration"), types);
                entry.displayMetadata = row.getCompound("displayMetadata").copy();
                entry.catalysts = row.getCompound("catalysts").copy(); entry.catalystRevision = row.getLong("catalystRevision");
                if (entry.catalystRevision < 0) throw new IllegalArgumentException("Negative catalyst revision");
                if (entries.putIfAbsent(id, entry) != null)
                    throw new IllegalArgumentException("Duplicate original payload");
            }
        }
    }
    private static final class Index extends CheckedData {
        @Override protected CloudPatternStore ownerStore() { return runtime; }
        private final Map<UUID, UUID> owners = new LinkedHashMap<>(), defaults = new LinkedHashMap<>(), nodeLibraries = new LinkedHashMap<>();
        private final Map<UUID, Map<String, UUID>> machineClaims = new LinkedHashMap<>();
        private final Map<UUID, String> nodeAddresses = new LinkedHashMap<>();
        private final Map<String, LinkedHashSet<UUID>> endpointIndex = new HashMap<>();
        private CloudPatternStore runtime;
        @Override protected CompoundTag writeData() {
            CompoundTag tag = new CompoundTag(); tag.put("owners", uuidMap(owners)); tag.put("defaults", uuidMap(defaults)); tag.put("nodes", uuidMap(nodeLibraries));
            ListTag claims = new ListTag(); machineClaims.forEach((machine, modes) -> modes.forEach((mode, node) -> {
                CompoundTag row = new CompoundTag(); row.putUUID("machine", machine); row.putString("mode", mode); row.putUUID("node", node); claims.add(row);
            })); tag.put("claims", claims);
            ListTag addresses = new ListTag(); nodeAddresses.forEach((node, address) -> { CompoundTag row = new CompoundTag(); row.putUUID("node", node); row.putString("address", address); addresses.add(row); });
            tag.put("addresses", addresses); return tag;
        }
        @Override protected void readData(CompoundTag tag) {
            readUuidMap(tag.getList("owners", Tag.TAG_COMPOUND), owners); readUuidMap(tag.getList("defaults", Tag.TAG_COMPOUND), defaults);
            readUuidMap(tag.getList("nodes", Tag.TAG_COMPOUND), nodeLibraries);
            for (Tag value : tag.getList("addresses", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)value; UUID node = row.getUUID("node"); String address = row.getString("address");
                if (!nodeLibraries.containsKey(node) || nodeAddresses.putIfAbsent(node, address) != null) throw new IllegalArgumentException("Invalid endpoint address index");
                endpointIndex.computeIfAbsent(address, ignored -> new LinkedHashSet<>()).add(node);
            }
            if (nodeAddresses.size() != nodeLibraries.size()) throw new IllegalArgumentException("Incomplete node address index");
            for (Tag value : tag.getList("claims", Tag.TAG_COMPOUND)) {
                CompoundTag row = (CompoundTag)value; UUID node = row.getUUID("node"); String mode = row.getString("mode");
                if (!nodeLibraries.containsKey(node) || ResourceLocation.tryParse(mode) == null
                        || machineClaims.computeIfAbsent(row.getUUID("machine"), ignored -> new LinkedHashMap<>()).putIfAbsent(mode, node) != null)
                    throw new IllegalArgumentException("Invalid global machine mode claim");
            }
            if (defaults.values().stream().anyMatch(id -> !owners.containsKey(id)) || nodeLibraries.values().stream().anyMatch(id -> !owners.containsKey(id)))
                throw new IllegalArgumentException("Invalid root references");
        }
    }
    public abstract static class CheckedData extends SavedData {
        /** Entire batches share this gate, including vanilla synchronous partition saves. */
        private static final Object FILE_COMMIT_LOCK = new Object();
        /** File sequencing is independent of live SavedData; only this state is touched by the worker. */
        private static final class WriteState {
            private long nextSequence;
            private volatile long persistedSequence;
        }
        private record FileSnapshot(Path target, CompoundTag outer, WriteState state, long sequence, long dirtyEpoch) {}
        private final WriteState writes = new WriteState();
        private long dirtyEpoch, completedSequence;
        private String persistedChecksum;
        private CompoundTag quarantined;
        private String diagnostic = "";
        private boolean ioFailed;
        protected abstract CompoundTag writeData();
        protected abstract void readData(CompoundTag tag);
        protected CloudPatternStore ownerStore() { return null; }
        @Override public final void setDirty(boolean dirty) {
            if (dirty) dirtyEpoch = Math.addExact(dirtyEpoch, 1);
            super.setDirty(dirty);
        }
        public final boolean healthy() { return quarantined == null && !ioFailed; }
        public final String diagnostic() { return diagnostic; }
        public final CompoundTag quarantinedData() { return quarantined == null ? new CompoundTag() : quarantined.copy(); }
        protected final void requireHealthy() { if (!healthy()) throw new IllegalStateException(diagnostic); }
        protected final void quarantine(String reason) { quarantined = new CompoundTag(); diagnostic = reason; }
        protected final void readChecked(CompoundTag raw) {
            try {
                if (raw.getInt("formatVersion") != 1 || !raw.contains("data", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Unknown or missing cloud format");
                CompoundTag data = raw.getCompound("data");
                if (!checksum(data).equals(raw.getString("sha256"))) throw new IllegalArgumentException("Cloud shard checksum mismatch");
                readData(data); persistedChecksum = raw.getString("sha256");
            } catch (RuntimeException failure) { quarantined = raw.copy(); diagnostic = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(); }
        }
        @Override public final CompoundTag save(CompoundTag tag) {
            if (quarantined != null) return quarantined.copy(); // Preserve the complete original, including unknown fields.
            CompoundTag data = writeData(); tag.putInt("formatVersion", 1); tag.put("data", data); tag.putString("sha256", checksum(data)); return tag;
        }
        /** Vanilla clears dirty after IOException; cloud partitions retain dirty and fail closed instead. */
        @Override public final void save(File file) {
            FileSnapshot snapshot = null;
            try {
                var owner = ownerStore();
                if (owner != null) { owner.saveScheduled(); return; }
                snapshot = captureSnapshot(file.toPath());
                if (snapshot == null) return;
                persistAll(List.of(snapshot)); finishSnapshot(snapshot, null);
            } catch (RuntimeException failure) {
                if (snapshot != null) finishSnapshot(snapshot, failure);
                else setDirty(true);
                com.mojang.logging.LogUtils.getLogger().error("Cloud partition save failed; retained dirty data and previous snapshot: {}", file, failure);
            }
        }
        /** Server-thread capture; NBT is detached before the worker can see it. */
        private FileSnapshot captureSnapshot(Path target) {
            if (!isDirty() || quarantined != null) return null;
            CompoundTag outer = new CompoundTag(); outer.put("data", save(new CompoundTag()).copy()); NbtUtils.addCurrentDataVersion(outer);
            // A generic markDirty is not evidence of an order/inventory mutation.
            // Never elide a snapshot while an older in-flight write can still replace it.
            if (writes.nextSequence == completedSequence && writes.persistedSequence == completedSequence
                    && outer.getCompound("data").getString("sha256").equals(persistedChecksum)) { super.setDirty(false); return null; }
            long sequence = Math.addExact(writes.nextSequence, 1); writes.nextSequence = sequence;
            return new FileSnapshot(target, outer, writes, sequence, dirtyEpoch);
        }
        /** Server-thread completion only. A newer mutation must remain dirty even if this write succeeded. */
        private void finishSnapshot(FileSnapshot snapshot, Throwable failure) {
            if (snapshot.sequence < completedSequence) return;
            completedSequence = snapshot.sequence;
            if (writes.persistedSequence >= snapshot.sequence) {
                persistedChecksum = snapshot.outer.getCompound("data").getString("sha256");
                ioFailed = false; diagnostic = "";
                if (dirtyEpoch == snapshot.dirtyEpoch) super.setDirty(false);
            } else if (failure != null) {
                Throwable cause = failure;
                while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                ioFailed = true; diagnostic = "Cloud snapshot write failed: " + snapshot.target + " / "
                        + cause.getClass().getSimpleName() + ": " + cause.getMessage(); setDirty(true);
                com.mojang.logging.LogUtils.getLogger().error(diagnostic, failure);
            }
        }
        private static void persistAll(List<FileSnapshot> snapshots) {
            synchronized (FILE_COMMIT_LOCK) {
                for (FileSnapshot snapshot : snapshots) {
                    if (snapshot.sequence <= snapshot.state.persistedSequence) continue;
                    try {
                        persistSnapshot(snapshot); snapshot.state.persistedSequence = snapshot.sequence;
                    } catch (IOException failure) { throw new UncheckedIOException("Cloud snapshot write failed", failure); }
                }
            }
        }
        /** I/O only: no world, ledger, suppliers, ItemStack or SavedData access. */
        private static void persistSnapshot(FileSnapshot snapshot) throws IOException {
            Path target = snapshot.target;
            Path temporary = target.resolveSibling(target.getFileName() + ".pending-" + UUID.randomUUID());
            Path previousPending = target.resolveSibling(target.getFileName() + ".previous-pending-" + UUID.randomUUID());
            try {
                Files.createDirectories(target.toAbsolutePath().getParent());
                NbtIo.writeCompressed(snapshot.outer, temporary.toFile()); force(temporary);
                if (!NbtIo.readCompressed(temporary.toFile()).equals(snapshot.outer)) throw new IOException("Cloud snapshot verification failed");
                if (Files.exists(target)) {
                    Files.copy(target, previousPending, StandardCopyOption.REPLACE_EXISTING); force(previousPending);
                    Files.move(previousPending, target.resolveSibling(target.getFileName() + ".previous"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Unique incomplete file is never treated as a committed partition. */ }
                try { Files.deleteIfExists(previousPending); } catch (IOException ignored) { /* Previous committed backup is retained. */ }
            }
        }
        private static void force(Path file) throws IOException {
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
        }
    }
    public static String checksum(CompoundTag tag) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(tag).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String canonical(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            StringBuilder result = new StringBuilder("{");
            compound.getAllKeys().stream().sorted().forEach(key -> result.append(key.length()).append(':').append(key).append('=').append(canonical(Objects.requireNonNull(compound.get(key)))).append(';'));
            return result.append('}').toString();
        }
        if (tag instanceof ListTag list) { StringBuilder result = new StringBuilder("["); list.forEach(value -> result.append(canonical(value)).append(';')); return result.append(']').toString(); }
        return tag.getId() + ":" + tag;
    }
    private static String libraryName(UUID id) { return "gtl_cloud_library_" + id; }
    private static String nodeName(UUID id) { return "gtl_cloud_node_" + id; }
    private static String shardName(UUID id, int page) { return "gtl_cloud_originals_" + id + "_" + page; }
    private static ListTag uuidList(Collection<UUID> ids) { ListTag rows = new ListTag(); ids.forEach(id -> { CompoundTag row = new CompoundTag(); row.putUUID("id", id); rows.add(row); }); return rows; }
    private static Set<UUID> readUuids(ListTag rows) { Set<UUID> ids = new LinkedHashSet<>(); for (Tag value : rows) if (!ids.add(((CompoundTag)value).getUUID("id"))) throw new IllegalArgumentException("Duplicate identity"); return ids; }
    private static ListTag uuidMap(Map<UUID, UUID> map) { ListTag rows = new ListTag(); map.forEach((key, value) -> { CompoundTag row = new CompoundTag(); row.putUUID("key", key); row.putUUID("value", value); rows.add(row); }); return rows; }
    private static void readUuidMap(ListTag rows, Map<UUID, UUID> map) { for (Tag value : rows) { CompoundTag row = (CompoundTag)value; if (map.putIfAbsent(row.getUUID("key"), row.getUUID("value")) != null) throw new IllegalArgumentException("Duplicate mapping"); } }
    private static CompoundTag writeLedger(CloudLedger ledger) {
        CompoundTag tag = new CompoundTag(); ListTag entries = new ListTag(), nodes = new ListTag(), receipts = new ListTag();
        for (CloudLedger.EntryState entry : ledger.entries()) {
            CompoundTag row = new CompoundTag(); row.putUUID("id", entry.id()); row.putLong("revision", entry.revision()); row.putString("status", entry.status().name());
            if (entry.lease() != null) { CloudLedger.Lease lease = entry.lease(); CompoundTag l = new CompoundTag(); l.putUUID("id", lease.id()); l.putUUID("node", lease.nodeId()); l.putInt("slot", lease.stableSlot()); l.putLong("generation", lease.generation()); row.put("lease", l); }
            entries.add(row);
        }
        for (CloudLedger.NodeState node : ledger.nodes()) {
            CompoundTag row = new CompoundTag(); row.putUUID("id", node.id()); row.putUUID("owner", node.owner()); row.putUUID("library", node.library());
            if (node.machineInstance() != null) row.putUUID("machine", node.machineInstance()); row.putString("mode", node.mode()); row.putLong("generation", node.generation()); row.putBoolean("detached", node.detached());
            ListTag slots = new ListTag(); node.slots().forEach((slot, entry) -> { CompoundTag s = new CompoundTag(); s.putInt("slot", slot); s.putUUID("entry", entry); slots.add(s); }); row.put("slots", slots); nodes.add(row);
        }
        for (CloudLedger.Receipt receipt : ledger.receipts()) { CompoundTag row = new CompoundTag(); row.putUUID("operation", receipt.operation()); row.putString("kind", receipt.kind()); row.putUUID("subject", receipt.subject()); row.putString("status", receipt.status()); receipts.add(row); }
        tag.put("entries", entries); tag.put("nodes", nodes); tag.put("receipts", receipts); return tag;
    }
    private static CloudLedger readLedger(CompoundTag tag) {
        List<CloudLedger.EntryState> entries = new ArrayList<>(); List<CloudLedger.NodeState> nodes = new ArrayList<>(); List<CloudLedger.Receipt> receipts = new ArrayList<>();
        for (Tag value : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag)value; UUID id = row.getUUID("id"); CloudLedger.Lease lease = null;
            if (row.contains("lease", Tag.TAG_COMPOUND)) { CompoundTag l = row.getCompound("lease"); lease = new CloudLedger.Lease(l.getUUID("id"), id, l.getUUID("node"), l.getInt("slot"), l.getLong("generation")); }
            entries.add(new CloudLedger.EntryState(id, row.getLong("revision"), CloudLedger.Status.valueOf(row.getString("status")), lease));
        }
        for (Tag value : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag)value; Map<Integer, UUID> slots = new LinkedHashMap<>();
            for (Tag slotValue : row.getList("slots", Tag.TAG_COMPOUND)) { CompoundTag slot = (CompoundTag)slotValue; if (slots.putIfAbsent(slot.getInt("slot"), slot.getUUID("entry")) != null) throw new IllegalArgumentException("Duplicate stable slot"); }
            nodes.add(new CloudLedger.NodeState(row.getUUID("id"), row.getUUID("owner"), row.getUUID("library"), row.hasUUID("machine") ? row.getUUID("machine") : null,
                    row.getString("mode"), row.getLong("generation"), row.getBoolean("detached"), slots));
        }
        for (Tag value : tag.getList("receipts", Tag.TAG_COMPOUND)) { CompoundTag row = (CompoundTag)value; receipts.add(new CloudLedger.Receipt(row.getUUID("operation"), row.getString("kind"), row.getUUID("subject"), row.getString("status"))); }
        CloudLedger result = new CloudLedger(); result.restore(entries, nodes, receipts); return result;
    }
}
