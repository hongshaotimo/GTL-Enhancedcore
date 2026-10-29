package com.gtl.enhancedcore.common.cloud.transfer;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Small durable intent files plus source-BE markers. Never infer an ambiguous debit as success. */
public final class CloudImportJournal {
    private CloudImportJournal() {}
    private static final java.util.Map<net.minecraft.server.MinecraftServer, java.util.Set<UUID>> ACTIVE_UPLOADS = new java.util.WeakHashMap<>();
    private static java.util.Set<UUID> activeUploads(ServerLevel level) {
        return ACTIVE_UPLOADS.computeIfAbsent(level.getServer(), unused -> new java.util.HashSet<>());
    }
    /** The original has already been debited behind a forced PREPARED record. No success
     * is reported until payload, COMMITTED journal and ACK authority are all durable. */
    public static java.util.concurrent.CompletableFuture<Void> commitUploadAsync(ServerLevel level, UUID transaction, ItemStack actual) {
        CompoundTag tag = find(level, transaction);
        if (!ItemStack.matches(ItemStack.of(tag.getCompound("expected")), actual))
            throw new IllegalStateException("Unexpected import debit");
        UUID library = tag.getUUID("library"), actor = tag.getUUID("actor");
        Path target = paths.get(transaction);
        if (target == null) target = path(level, tag);
        final Path journal = target;
        // Reserve the exact original synchronously before yielding; another core cannot
        // accept a duplicate while this upload waits for its first worker completion.
        var store = CloudPatternStore.get(level);
        var entry = store.upload(library, actor, actual.copy(), tag.getCompound("configuration"), transaction);
        CloudLibraryService.identify(entry, actor); tag.putUUID("entry", entry.id());
        activeUploads(level).add(transaction);
        tag.putString("status", "DEBITED");
        CompoundTag debited = tag.copy();
        return CloudUploadSequence.run(level.getServer()::execute, () -> CloudImportIo.run(() -> writeFile(journal, debited)),
                () -> store.checkpointLibraryAsync(library), () -> {
            CompoundTag committed = tag.copy(); committed.putString("status", "COMMITTED");
            return CloudImportIo.run(() -> writeFile(journal, committed));
        }, () -> {
            store.acknowledge(library, actor, transaction);
            tag.putString("status", "COMMITTED");
            REVIEWS.computeIfAbsent(root(level), unused -> new ReviewCatalog()).remember(tag);
            // COMMITTED WAL already proves this upload. ACK may now publish; a later
            // checkpoint failure retains dirty data and fails closed at the partition.
            // Never roll back a confirmed upload that may already own a live lease/job.
            return store.checkpointLibraryAsync(library);
        }).whenComplete((ignored, failure) -> level.getServer().execute(() -> activeUploads(level).remove(transaction)));
    }
    public static CloudBufferImport.ImportSink sink(ServerLevel level, UUID library, UUID actor) {
        return new CloudBufferImport.ImportSink() {
            private boolean batching;
            private final java.util.List<CompoundTag> pending = new java.util.ArrayList<>();
            public void beginBatch() { if (batching) throw new IllegalStateException("Nested import batch"); batching = true; }
            public void endBatch() {
                try {
                    if (pending.isEmpty()) return;
                    var store = CloudPatternStore.get(level);
                    store.checkpoint(); // Flush payloads before directory publication; this is ordered persistence, not a multi-file atomic write.
                    for (CompoundTag tag : pending) { tag.putString("status", "COMMITTED"); write(level, tag); }
                    for (CompoundTag tag : pending) store.acknowledge(library, actor, tag.getUUID("operation"));
                    store.checkpoint();
                } finally { batching = false; pending.clear(); }
            }
            public boolean canAccept(ItemStack pattern, CompoundTag config) {
                var target = CloudPatternStore.get(level).library(library);
                return pattern.getCount() == 1 && target.authorized(actor) && !target.containsOriginal(pattern);
            }
            public UUID prepare(CloudBufferImport.Source source, int slot, ItemStack expected, CompoundTag config) {
                if (expected.getCount() != 1) throw new IllegalArgumentException("One original per import receipt");
                var target = CloudPatternStore.get(level).library(library);
                if (!target.authorized(actor)) throw new SecurityException("Library denied");
                target.requireUniqueOriginal(expected);
                UUID operation = UUID.randomUUID(); CompoundTag tag = new CompoundTag();
                tag.putInt("schema", 1); tag.putUUID("operation", operation); tag.putUUID("library", library); tag.putUUID("actor", actor);
                tag.putString("dimension", source.dimension()); tag.putLong("position", source.position()); tag.putUUID("identity", source.identity());
                tag.putInt("slot", slot); tag.put("expected", expected.save(new CompoundTag())); tag.put("configuration", config.copy());
                tag.putString("status", "PREPARED"); write(level, tag); return operation;
            }
            public void commit(UUID transaction, ItemStack actual) {
                CompoundTag tag = find(level, transaction);
                if (!ItemStack.matches(ItemStack.of(tag.getCompound("expected")), actual)) throw new IllegalStateException("Unexpected import debit");
                tag.putString("status", "DEBITED"); write(level, tag);
                if (batching) {
                    var entry = CloudPatternStore.get(level).upload(library, actor, actual, tag.getCompound("configuration"), transaction);
                    CloudLibraryService.identify(entry, actor); tag.putUUID("entry", entry.id()); pending.add(tag);
                } else complete(level, tag);
            }
            public void abort(UUID transaction) { CompoundTag tag = find(level, transaction); tag.putString("status", "ABORTED"); write(level, tag); }
            public void uncertain(UUID transaction, RuntimeException failure) {
                pending.removeIf(tag -> tag.getUUID("operation").equals(transaction));
                CompoundTag tag = find(level, transaction); tag.putString("status", "NEEDS_REVIEW"); write(level, tag);
                var store = CloudPatternStore.get(level);
                if (store.library(library).receipt(transaction) != null) { store.needsReview(library, actor, transaction); store.checkpoint(); }
            }
        };
    }
    /** Removal completion addresses this transaction only, never a replacement block at its old position. */
    public static void retireCompletedUpload(ServerLevel level, UUID operation) {
        CompoundTag tag = find(level, operation);
        var receipt = CloudPatternStore.get(level).library(tag.getUUID("library")).receipt(operation);
        if (receipt == null || !receipt.status().equals("ACKNOWLEDGED") || !tag.getString("status").equals("COMMITTED"))
            throw new IllegalStateException("Upload is not durably acknowledged");
        tag.putString("status", "RETIRED"); write(level, tag);
    }
    // Operations originate on the server thread; the runtime lookup is only a path cache, never ownership state.
    private static final java.util.Map<UUID, Path> paths = new java.util.HashMap<>();
    private static final class ReviewCatalog {
        final java.util.Map<UUID, CompoundTag> pending = new java.util.LinkedHashMap<>();
        java.util.stream.Stream<Path> scan;
        java.util.Iterator<Path> iterator;
        boolean started, complete;
        long tick = Long.MIN_VALUE;
        void remember(CompoundTag tag) {
            UUID operation = tag.getUUID("operation");
            if (java.util.Set.of("PREPARED", "DEBITED", "NEEDS_REVIEW").contains(tag.getString("status"))) pending.put(operation, tag.copy());
            else pending.remove(operation);
        }
    }
    private static final java.util.Map<Path, ReviewCatalog> REVIEWS = new java.util.HashMap<>();
    private static ReviewCatalog reviewCatalog(ServerLevel level) {
        Path location = root(level); ReviewCatalog catalog = REVIEWS.computeIfAbsent(location, ignored -> new ReviewCatalog());
        if (catalog.tick == level.getGameTime()) return catalog;
        catalog.tick = level.getGameTime();
        try {
            if (!catalog.started) {
                catalog.started = true;
                if (Files.isDirectory(location)) { catalog.scan = Files.walk(location, 2); catalog.iterator = catalog.scan.iterator(); }
                else catalog.complete = true;
            }
            int visited = 0;
            while (catalog.iterator != null && catalog.iterator.hasNext() && visited++ < 128) {
                Path file = catalog.iterator.next();
                if (!file.getFileName().toString().endsWith(".dat") || !Files.isRegularFile(file)) continue;
                for (CompoundTag tag : records(NbtIo.readCompressed(file.toFile()))) { paths.put(tag.getUUID("operation"), file); catalog.remember(tag); }
            }
            if (catalog.iterator != null && !catalog.iterator.hasNext()) {
                catalog.scan.close(); catalog.scan = null; catalog.iterator = null; catalog.complete = true;
            }
        } catch (IOException | RuntimeException failure) {
            if (catalog.scan != null) catalog.scan.close(); catalog.scan = null; catalog.iterator = null;
            throw new IllegalStateException("Import evidence catalog could not be read", failure);
        }
        return catalog;
    }
    public static void closeCatalogs() {
        REVIEWS.values().forEach(c -> { if (c.scan != null) c.scan.close(); }); REVIEWS.clear(); paths.clear();
    }
    private static Path root(ServerLevel level) { return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_import"); }
    private static Path sourceDirectory(ServerLevel level, String dimension, long position) {
        return root(level).resolve(UUID.nameUUIDFromBytes(dimension.getBytes(StandardCharsets.UTF_8)) + "_" + position);
    }
    private static Path path(ServerLevel level, CompoundTag tag) {
        Path file = sourceDirectory(level, tag.getString("dimension"), tag.getLong("position")).resolve(tag.getUUID("operation") + ".dat");
        paths.put(tag.getUUID("operation"), file); return file;
    }
    private static CompoundTag find(ServerLevel level, UUID operation) {
        Path file = paths.get(operation);
        if (file == null || !file.normalize().startsWith(root(level).normalize())) throw new IllegalStateException("Unknown import transaction");
        try { return records(NbtIo.readCompressed(file.toFile())).stream().filter(tag -> tag.getUUID("operation").equals(operation)).findFirst().orElseThrow(); } catch (IOException failure) { throw new IllegalStateException("Unreadable import journal", failure); }
    }
    static java.util.List<CompoundTag> records(CompoundTag raw) {
        if (raw.getInt("schema") != 2) return java.util.List.of(raw);
        CompoundTag checked = raw.copy(); checked.remove("sha256");
        if (!CloudPatternStore.checksum(checked).equals(raw.getString("sha256"))) throw new IllegalStateException("Import batch checksum mismatch");
        java.util.List<CompoundTag> rows = new java.util.ArrayList<>();
        for (Tag row : raw.getList("rows", Tag.TAG_COMPOUND)) rows.add((CompoundTag)row);
        if (rows.isEmpty()) throw new IllegalStateException("Empty import batch journal");
        return rows;
    }
    /** Immutable pages are forced on the IO queue before their first physical debit. */
    public static java.util.concurrent.CompletableFuture<Void> persistBatchAsync(ServerLevel level, UUID batch, java.util.List<CompoundTag> rows) {
        if (rows.isEmpty()) return java.util.concurrent.CompletableFuture.completedFuture(null);
        CompoundTag first = rows.get(0);
        Path file = sourceDirectory(level, first.getString("dimension"), first.getLong("position")).resolve(batch + ".dat");
        CompoundTag wrapper = new CompoundTag(); wrapper.putInt("schema", 2); ListTag saved = new ListTag();
        for (CompoundTag row : rows) {
            if (!row.getString("dimension").equals(first.getString("dimension")) || row.getLong("position") != first.getLong("position"))
                throw new IllegalArgumentException("Mixed sources in one import page");
            paths.put(row.getUUID("operation"), file); saved.add(row.copy());
        }
        wrapper.put("rows", saved);
        return CloudImportIo.run(() -> writeFile(file, wrapper));
    }
    private static void write(ServerLevel level, CompoundTag tag) {
        Path target = paths.get(tag.getUUID("operation"));
        if (target == null) target = path(level, tag);
        CompoundTag payload = tag;
        if (!target.getFileName().toString().equals(tag.getUUID("operation") + ".dat")) {
            try {
                CompoundTag wrapper = NbtIo.readCompressed(target.toFile()); ListTag rows = wrapper.getList("rows", Tag.TAG_COMPOUND);
                boolean found = false;
                for (int i = 0; i < rows.size(); i++) if (rows.getCompound(i).getUUID("operation").equals(tag.getUUID("operation"))) { rows.set(i, tag.copy()); found = true; break; }
                if (!found) throw new IllegalStateException("Import batch does not contain operation");
                payload = wrapper;
            } catch (IOException failure) { throw new IllegalStateException("Unreadable import batch", failure); }
        }
        writeFile(target, payload);
        REVIEWS.computeIfAbsent(root(level), ignored -> new ReviewCatalog()).remember(tag);
    }
    private static void writeFile(Path target, CompoundTag tag) {
        if (tag.getInt("schema") == 2) {
            tag = tag.copy(); tag.remove("sha256"); String checksum = CloudPatternStore.checksum(tag); tag.putString("sha256", checksum);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".pending");
        try {
            Files.createDirectories(target.getParent()); NbtIo.writeCompressed(tag, temporary.toFile());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            if (!NbtIo.readCompressed(temporary.toFile()).equals(tag)) throw new IOException("Import journal verification failed");
            if (Files.exists(target)) Files.copy(target, target.resolveSibling(target.getFileName() + ".previous"), StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) { throw new IllegalStateException("Import journal could not commit atomically", failure); }
    }
    private static void complete(ServerLevel level, CompoundTag tag) {
        UUID library = tag.getUUID("library"), actor = tag.getUUID("actor"), operation = tag.getUUID("operation");
        var store = CloudPatternStore.get(level); ItemStack actual = ItemStack.of(tag.getCompound("expected"));
        boolean first = store.library(library).receipt(operation) == null;
        if (tag.hasUUID("sharedGroup") && !store.library(library).importedCatalystGroups(actor).contains(tag.getUUID("sharedGroup").toString()))
            store.library(library).setImportedCatalysts(actor, tag.getUUID("sharedGroup"), tag.getCompound("sharedCatalysts"));
        var entry = store.upload(library, actor, actual, tag.getCompound("configuration"), operation);
        if (first && tag.contains("catalysts", Tag.TAG_COMPOUND)) entry.initializeImportedCatalysts(actor, operation, tag.getCompound("catalysts"));
        CloudLibraryService.identify(entry, actor); store.checkpoint();
        tag.putUUID("entry", entry.id()); tag.putString("status", "COMMITTED"); write(level, tag);
        store.acknowledge(library, actor, operation); store.checkpoint();
    }
    /** Called only from actual machine removal, never chunk unload or drop-NBT preview. */
    public static void retire(MetaMachine source) {
        if (!(source.getLevel() instanceof ServerLevel level) || !(source instanceof CloudImportSourceAccess access)) return;
        if (access.cloud$importQuarantined()) throw new IllegalStateException("Quarantined import source cannot retire");
        Path directory = sourceDirectory(level, level.dimension().location().toString(), source.getPos().asLong());
        if (!Files.isDirectory(directory)) return;
        try (var stream = Files.newDirectoryStream(directory, "*.dat")) {
            var committed = new java.util.ArrayList<CompoundTag>();
            for (Path file : stream) {
                for (CompoundTag tag : records(NbtIo.readCompressed(file.toFile()))) {
                paths.put(tag.getUUID("operation"), file);
                if (!tag.getUUID("identity").equals(access.cloud$sourceIdentity())) continue;
                String status = tag.getString("status");
                if (status.equals("ABORTED") || status.equals("RETIRED")) continue;
                if (!status.equals("COMMITTED") || !access.cloud$processed(tag.getUUID("operation")))
                    throw new IllegalStateException("Import source has unresolved transactions");
                committed.add(tag);
                }
            }
            for (CompoundTag tag : committed) { tag.putString("status", "RETIRED"); write(level, tag); }
        } catch (RuntimeException | IOException failure) {
            access.cloud$quarantineImport(failure.getMessage());
            throw new IllegalStateException("Import source retirement failed", failure);
        }
    }
    public static void reconcile(MetaMachine source) {
        if (!(source.getLevel() instanceof ServerLevel level) || !(source instanceof CloudImportSourceAccess access)) return;
        ItemStackTransfer inventory = inventory(source);
        if (inventory == null) return;
        CloudImportInventoryGuard.register(inventory, access);
        Path directory = sourceDirectory(level, level.dimension().location().toString(), source.getPos().asLong());
        if (!Files.isDirectory(directory)) return;
        try (var stream = Files.newDirectoryStream(directory, "*.dat")) {
            for (Path file : stream) {
                for (CompoundTag tag : records(NbtIo.readCompressed(file.toFile()))) {
                UUID operation = tag.getUUID("operation"); paths.put(operation, file);
                if (tag.getInt("schema") != 1 || !tag.getString("dimension").equals(level.dimension().location().toString())
                        || tag.getLong("position") != source.getPos().asLong()) throw new IllegalStateException("Import source address mismatch");
                String status = tag.getString("status");
                if (status.equals("ABORTED")) continue;
                // A cleanly removed source leaves a tombstone for rollback detection, not a lock on the address.
                if (status.equals("RETIRED") && !access.cloud$sourceIdentity().equals(tag.getUUID("identity"))) continue;
                if (!access.cloud$sourceIdentity().equals(tag.getUUID("identity"))) throw new IllegalStateException("Import source was replaced or not yet durably identified");
                if (activeUploads(level).contains(operation)) continue;
                if (tag.hasUUID("returnOperation") && CloudNetworkPatternImport.reconcileReturn(source, tag)) continue;
                if (status.equals("RETIRED")) {
                    if (!access.cloud$processed(operation)) {
                        int retiredSlot = tag.getInt("slot"); ItemStack expected = ItemStack.of(tag.getCompound("expected"));
                        CloudImportInventoryGuard.recover(inventory, () -> {
                            if (retiredSlot >= 0 && retiredSlot < inventory.getSlots() && expected.getCount() == 1
                                    && ItemStack.matches(inventory.getStackInSlot(retiredSlot), expected)) {
                                ItemStack removed = inventory.extractItem(retiredSlot, 1, false, true);
                                if (ItemStack.matches(removed, expected)) access.cloud$markProcessed(operation);
                            }
                            return null;
                        });
                        throw new IllegalStateException("Retired import source rolled back before its debit");
                    }
                    continue;
                }
                if (status.equals("NEEDS_REVIEW")) throw new IllegalStateException("Import requires manual review");
                if (source instanceof MEPatternBufferPartMachine buffer && tag.hasUUID("sharedGroup") && !access.cloud$processed(tag.getUUID("sharedGroup"))) {
                    CloudImportedCatalysts.drainShared(buffer, tag.getCompound("sharedCatalysts"));
                    access.cloud$markProcessed(tag.getUUID("sharedGroup")); source.markDirty();
                }
                if (!access.cloud$processed(operation)) {
                    int slot = tag.getInt("slot"); ItemStack expected = ItemStack.of(tag.getCompound("expected"));
                    if (slot < 0 || slot >= inventory.getSlots() || expected.getCount() != 1 || !ItemStack.matches(inventory.getStackInSlot(slot), expected))
                        throw new IllegalStateException("Import source differs from durable intent");
                    ItemStack actual = inventory.extractItem(slot, 1, false, true);
                    if (!ItemStack.matches(actual, expected)) throw new IllegalStateException("Import source debit could not be confirmed");
                    if (source instanceof MEPatternBufferPartMachine buffer && tag.contains("catalysts", Tag.TAG_COMPOUND))
                        CloudImportedCatalysts.drainPattern(buffer, slot, tag.getCompound("catalysts"));
                    access.cloud$markProcessed(operation); source.markDirty();
                }
                if (!status.equals("COMMITTED")) complete(level, tag);
                else {
                    var store = CloudPatternStore.get(level);
                    store.acknowledge(tag.getUUID("library"), tag.getUUID("actor"), operation); store.checkpoint();
                }
                }
            }
            CloudNetworkPatternImport.sourceReconciled(level);
        } catch (RuntimeException | IOException failure) { access.cloud$quarantineImport(failure.getMessage()); }
    }
    private static CompoundTag locate(ServerLevel level, UUID operation) {
        Path cached = paths.get(operation);
        if (cached != null && cached.normalize().startsWith(root(level).normalize()) && Files.isRegularFile(cached)) return find(level, operation);
        if (!Files.isDirectory(root(level))) throw new IllegalStateException("No durable import evidence");
        try (var files = Files.walk(root(level), 2)) {
            Path file = files.filter(p -> p.getFileName().toString().endsWith(".dat")).filter(p -> {
                try { return records(NbtIo.readCompressed(p.toFile())).stream().anyMatch(t -> t.hasUUID("operation") && t.getUUID("operation").equals(operation)); }
                catch (IOException malformed) { return false; }
            }).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No durable import evidence"));
            paths.put(operation, file); return records(NbtIo.readCompressed(file.toFile())).stream().filter(tag -> tag.getUUID("operation").equals(operation)).findFirst().orElseThrow();
        } catch (IOException failure) { throw new IllegalStateException("Unreadable import evidence", failure); }
    }
    private static MetaMachine loadedSource(ServerLevel level, CompoundTag tag) {
        if (!level.dimension().location().toString().equals(tag.getString("dimension"))) return null;
        var position = net.minecraft.core.BlockPos.of(tag.getLong("position"));
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return null;
        return chunk.getBlockEntity(position) instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder ? holder.getMetaMachine() : null;
    }
    /** Only four records cross the menu boundary; no item originals from unrelated entries are synchronized. */
    public static CompoundTag reviewPage(ServerLevel level, UUID libraryId, int requestedPage) {
        var library = CloudPatternStore.get(level).library(libraryId);
        ReviewCatalog catalog = reviewCatalog(level);
        java.util.Map<UUID, com.gtl.enhancedcore.common.cloud.store.CloudLedger.Receipt> operations = new java.util.LinkedHashMap<>();
        library.receipts().stream().filter(r -> (r.kind().equals("UPLOAD") || r.kind().equals("EXPORT"))
                && !r.status().equals("ACKNOWLEDGED") && !r.status().equals("ROLLED_BACK")).forEach(r -> operations.put(r.operation(), r));
        catalog.pending.forEach((id, tag) -> {
            if (tag.getUUID("library").equals(libraryId)) operations.putIfAbsent(id,
                    new com.gtl.enhancedcore.common.cloud.store.CloudLedger.Receipt(id, "UPLOAD", id, tag.getString("status")));
        });
        var pending = java.util.List.copyOf(operations.values());
        var orders = library.recoveries().stream().filter(r -> r.status().equals("FROZEN") || r.status().equals("NEEDS_REVIEW")).toList();
        int total = pending.size() + orders.size(), pages = Math.max(1, (total + 3) / 4), page = Math.max(0, Math.min(requestedPage, pages - 1));
        ListTag rows = new ListTag();
        for (int i = page * 4; i < Math.min(total, (page + 1) * 4); i++) {
            CompoundTag row = new CompoundTag();
            if (i >= pending.size()) {
                var order = orders.get(i - pending.size());
                row.putString("id", order.nodeId().toString()); row.putString("operation", order.nodeId().toString());
                row.putString("name", order.nodeId().toString()); row.putString("target", order.nodeId().toString());
                row.putString("kind", "order"); row.putString("status", order.status());
                row.putString("evidence", "gtl_enhancedcore.cloud.recovery.visit_node");
            } else {
                var receipt = pending.get(i); UUID operation = receipt.operation();
                row.putString("id", operation.toString()); row.putString("operation", operation.toString());
                row.putString("name", operation.toString()); row.putString("kind", "transfer"); row.putString("status", receipt.status());
                row.putString("transferKind", receipt.kind());
                try {
                    CompoundTag tag = find(level, operation);
                    if (!tag.getUUID("library").equals(libraryId)) throw new SecurityException("Evidence belongs to another library");
                    row.putString("sourceDimension", tag.getString("dimension"));
                    row.putString("sourcePos", net.minecraft.core.BlockPos.of(tag.getLong("position")).toShortString());
                    row.putString("journalStatus", tag.getString("status")); row.put("expected", tag.getCompound("expected").copy());
                    var source = loadedSource(level, tag); ItemStackTransfer inventory = source == null ? null : inventory(source);
                    boolean identity = source instanceof CloudImportSourceAccess access && access.cloud$sourceIdentity().equals(tag.getUUID("identity"));
                    boolean processed = identity && ((CloudImportSourceAccess)source).cloud$processed(operation);
                    int slot = tag.getInt("slot"); ItemStack actual = inventory != null && slot >= 0 && slot < inventory.getSlots()
                            ? CloudImportInventoryGuard.recover(inventory, () -> inventory.getStackInSlot(slot).copy()) : ItemStack.EMPTY;
                    boolean exact = ItemStack.matches(actual, ItemStack.of(tag.getCompound("expected"))) && actual.getCount() == 1;
                    row.putString("expectedName", ItemStack.of(tag.getCompound("expected")).getHoverName().getString());
                    row.putString("actualName", actual.isEmpty() ? "" : actual.getHoverName().getString());
                    row.put("actual", actual.save(new CompoundTag())); row.putBoolean("loaded", source != null); row.putBoolean("identityMatches", identity);
                    row.putBoolean("processed", processed); row.putBoolean("exactExpected", exact);
                    row.putString("evidence", !identity ? "gtl_enhancedcore.cloud.recovery.no_source" : processed ? "gtl_enhancedcore.cloud.recovery.marker" : exact ? "gtl_enhancedcore.cloud.recovery.exact" : "gtl_enhancedcore.cloud.recovery.uncertain");
                } catch (RuntimeException missing) { row.putString("evidence", "gtl_enhancedcore.cloud.recovery.uncertain"); }
            }
            rows.add(row);
        }
        CompoundTag result = new CompoundTag(); result.put("recoveryEntries", rows); result.put("recoveries", rows.copy());
        result.putBoolean("recoveryIndexing", !catalog.complete);
        result.putInt("recoveryPage", page); result.putInt("recoveryPages", pages); result.putInt("recovery", total); return result;
    }
    /** Owner-only retry proves the debit against the exact source incarnation before publishing an original. */
    public static void retry(ServerLevel level, UUID libraryId, net.minecraft.server.level.ServerPlayer player, UUID operation) {
        var store = CloudPatternStore.get(level); var library = store.library(libraryId);
        if (!library.owner().equals(player.getUUID()) || player.serverLevel() != level) throw new SecurityException("Owner reconciliation required");
        CompoundTag tag = locate(level, operation);
        if (tag.getInt("schema") != 1 || !tag.getUUID("library").equals(libraryId) || !tag.getUUID("operation").equals(operation))
            throw new IllegalStateException("Import evidence identity mismatch");
        if (!java.util.Set.of("PREPARED", "DEBITED", "NEEDS_REVIEW", "COMMITTED").contains(tag.getString("status")))
            throw new IllegalStateException("Unknown or closed import cannot be retried");
        MetaMachine source = loadedSource(level, tag);
        if (!(source instanceof CloudImportSourceAccess access) || !access.cloud$sourceIdentity().equals(tag.getUUID("identity"))
                || player.distanceToSqr(source.getPos().getX()+.5, source.getPos().getY()+.5, source.getPos().getZ()+.5) > 64
                || !level.mayInteract(player, source.getPos()) || !CloudLibraryService.claimAllows(player, level, source.getPos()))
            throw new SecurityException("Exact loaded source and local access required");
        ItemStackTransfer inventory = inventory(source); int slot = tag.getInt("slot");
        if (source instanceof CloudPatternLibraryMachine central && !central.isFormed())
            throw new IllegalStateException("Import source library must be formed before manual reconciliation");
        if (source instanceof MEPatternBufferPartMachine buffer && !CloudImportSourceAddress.authorized(player, buffer))
            throw new SecurityException("Current source ownership denies reconciliation");
        ItemStack expected = ItemStack.of(tag.getCompound("expected"));
        if (inventory == null || slot < 0 || slot >= inventory.getSlots() || expected.getCount() != 1) throw new IllegalStateException("Invalid import debit evidence");
        var priorReceipt = library.receipt(operation);
        if (priorReceipt != null) {
            if (!priorReceipt.kind().equals("UPLOAD")) throw new IllegalStateException("Only import receipts can use source debit evidence");
            var priorEntry = library.entry(priorReceipt.subject());
            if (!ItemStack.matches(priorEntry.original(), expected) || !priorEntry.configuration().equals(tag.getCompound("configuration")))
                throw new IllegalStateException("Import receipt payload disagrees with journal");
        }
        try {
        if (!access.cloud$processed(operation)) {
            ItemStack actual = CloudImportInventoryGuard.recover(inventory, () -> {
                if (!ItemStack.matches(inventory.getStackInSlot(slot), expected)) throw new IllegalStateException("Import debit remains uncertain");
                return inventory.extractItem(slot, 1, false, true);
            });
            if (!ItemStack.matches(actual, expected)) throw new IllegalStateException("Import debit could not be proven");
            access.cloud$markProcessed(operation); source.markDirty();
        }
        tag.putUUID("actor", player.getUUID()); tag.putString("status", "DEBITED"); write(level, tag);
        var receipt = library.receipt(operation);
        if (receipt != null && receipt.status().equals("NEEDS_REVIEW")) store.resolveExternal(libraryId, player.getUUID(), operation, true);
        complete(level, tag);
        CompoundTag markers = access.cloud$saveImportMarkers(); markers.remove("quarantine"); access.cloud$loadImportMarkers(markers); source.markDirty();
        reconcile(source); // Any other unresolved operation restores quarantine immediately.
        if (access.cloud$importQuarantined()) throw new IllegalStateException("Selected import reconciled; other source evidence still needs review");
        } catch (RuntimeException failure) { access.cloud$quarantineImport("Import reconciliation requires review"); source.markDirty(); throw failure; }
    }
    private static ItemStackTransfer inventory(MetaMachine source) {
        if (source instanceof MEPatternBufferPartMachine buffer) return buffer.getPatternInventory();
        return source instanceof CloudPatternLibraryMachine library ? library.importInventory() : null;
    }
}
