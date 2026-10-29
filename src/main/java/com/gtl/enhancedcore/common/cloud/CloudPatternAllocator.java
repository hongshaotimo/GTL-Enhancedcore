package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** One fair, bounded server-wide queue; idle nodes never rescan an unchanged catalogue. */
@Mod.EventBusSubscriber(modid = "gtl_enhancedcore", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CloudPatternAllocator {
    private static final Map<MinecraftServer, Queue> QUEUES = new WeakHashMap<>();
    private static final int STEPS_PER_TICK = 1024;
    private static final long NANOS_PER_TICK = 3_000_000;
    private CloudPatternAllocator() {}
    private record Group(UUID library, ResourceLocation mode) {}
    private record Membership(Group group, long sequence) {}
    private record SelectionKey(Group group, UUID entry, long revision, CloudPatternMatching.RecipeIndex index, CloudPatternMatching.Probe probe) {}
    private static final class Queue {
        final Deque<CloudPatternBufferMachine> order = new ArrayDeque<>();
        final Map<CloudPatternBufferMachine, Scan> scans = new IdentityHashMap<>();
        final Set<CloudPatternBufferMachine> endpoints = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Group, NavigableMap<Long, CloudPatternBufferMachine>> groups = new HashMap<>();
        final Map<CloudPatternBufferMachine, Membership> memberships = new IdentityHashMap<>();
        final Map<SelectionKey, Selection> selections = new LinkedHashMap<>(32, .75f, true);
        long sequence;
        void invalidateSelections(UUID library) { selections.keySet().removeIf(key -> Objects.equals(key.group.library, library)); }
        Selection selection(SelectionKey key, List<GTRecipe> recipes) {
            var result = selections.computeIfAbsent(key, ignored -> new Selection(key, recipes));
            while (selections.size() > 4096) selections.remove(selections.keySet().iterator().next());
            return result;
        }
        void regroup(CloudPatternBufferMachine buffer) {
            var group = buffer.cloudLibraryId() == null || buffer.cloudMode() == null ? null : new Group(buffer.cloudLibraryId(), buffer.cloudMode());
            var old = memberships.get(buffer);
            if (old != null && old.group.equals(group)) return;
            ungroup(buffer);
            if (group != null) {
                invalidateSelections(group.library);
                long id = ++sequence; memberships.put(buffer, new Membership(group, id));
                groups.computeIfAbsent(group, ignored -> new TreeMap<>()).put(id, buffer);
            }
        }
        void ungroup(CloudPatternBufferMachine buffer) {
            var old = memberships.remove(buffer); if (old == null) return;
            invalidateSelections(old.group.library);
            var group = groups.get(old.group); if (group == null) return;
            group.remove(old.sequence); if (group.isEmpty()) groups.remove(old.group);
        }
    }
    /** Every equal probe shares one bounded contender walk, instead of N scans each
     * repeating the same N-machine election. Each step still tests at most one recipe. */
    private static final class Selection {
        final SelectionKey key;
        final List<GTRecipe> recipes;
        long cursor = -1;
        CloudPatternBufferMachine candidate, preferred;
        WorkableElectricMultiblockMachine candidateMachine, preferredMachine;
        UUID candidateNode, preferredNode;
        long candidateEpoch, preferredEpoch;
        int recipeCursor, matches;
        boolean complete;
        Selection(SelectionKey key, List<GTRecipe> recipes) { this.key = key; this.recipes = recipes; }
        void step(Queue queue, net.minecraft.server.level.ServerLevel level) {
            if (complete) return;
            if (candidate != null) {
                if (IvBuffers.controller(candidate) != candidateMachine || !Objects.equals(candidateNode, candidate.cloudNodeId())
                        || candidateEpoch != candidate.cloudAllocationEpoch()
                        || !key.group.library.equals(candidate.cloudLibraryId()) || !key.group.mode.equals(candidate.cloudMode())
                        || !candidate.cloudReady(false) || !candidate.iv$getState().accepting) { candidate = null; return; }
                if (recipeCursor < recipes.size()
                        && CloudPatternMatching.matches(recipes.get(recipeCursor++), candidateMachine, key.probe)) matches++;
                if (matches > 1) { candidate = null; return; }
                if (recipeCursor < recipes.size()) return;
                if (matches == 1 && (preferred == null || compare(candidate, preferred, level) < 0)) {
                    preferred = candidate; preferredMachine = candidateMachine; preferredNode = candidateNode;
                    preferredEpoch = candidateEpoch;
                }
                candidate = null; return;
            }
            var group = queue.groups.get(key.group);
            var next = group == null ? null : group.higherEntry(cursor);
            if (next == null) { complete = true; return; }
            cursor = next.getKey(); var endpoint = next.getValue();
            if (endpoint.cloudReady(true) && endpoint.iv$getState().accepting) {
                candidate = endpoint; candidateMachine = IvBuffers.controller(endpoint); candidateNode = endpoint.cloudNodeId();
                candidateEpoch = endpoint.cloudAllocationEpoch();
                recipeCursor = 0; matches = 0;
            }
        }
        boolean preferredValid() {
            return preferred == null || (IvBuffers.controller(preferred) == preferredMachine
                    && Objects.equals(preferredNode, preferred.cloudNodeId())
                    && preferredEpoch == preferred.cloudAllocationEpoch()
                    && key.group.library.equals(preferred.cloudLibraryId()) && key.group.mode.equals(preferred.cloudMode())
                    && preferred.cloudReady(true) && preferred.iv$getState().accepting);
        }
    }
    private static final class Scan {
        final WorkableElectricMultiblockMachine machine;
        final CloudPatternStore.Library library;
        final UUID nodeId, libraryId;
        final ResourceLocation mode;
        final long allocationEpoch;
        final int entryCount;
        final List<UUID> priorityEntries;
        final boolean incremental;
        int priorityCursor;
        List<UUID> page = List.of();
        int pageOffset;
        final CloudPatternMatching.RecipeIndex index;
        List<GTRecipe> recipes = List.of();
        long observedRevision;
        int cursor, recipeCursor, matches;
        CloudPatternStore.Entry entry;
        GTRecipe matchedRecipe;
        CloudPatternMatching.Probe probe;
        Scan(CloudPatternBufferMachine buffer) {
            machine = IvBuffers.controller(buffer); library = buffer.cloudStore().library(buffer.cloudLibraryId());
            nodeId = buffer.cloudNodeId(); libraryId = buffer.cloudLibraryId(); mode = buffer.cloudMode();
            allocationEpoch = buffer.cloudAllocationEpoch();
            observedRevision = library.allocationRevision(); entryCount = library.entryCount();
            var changes = library.allocationChangesSince(buffer.cloudScannedRevision());
            incremental = changes != null;
            priorityEntries = incremental ? changes : library.recentAllocationChanges();
            var type = Arrays.stream(machine.getDefinition().getRecipeTypes()).filter(t -> buffer.cloudAcceptsMode(t.registryName)).findFirst().orElseThrow();
            var level = (net.minecraft.server.level.ServerLevel)buffer.getLevel();
            index = CloudPatternMatching.index(level, type);
        }
        boolean step(CloudPatternBufferMachine buffer) {
            if (IvBuffers.controller(buffer) != machine || !buffer.cloudReady(false)) return false;
            if (!nodeId.equals(buffer.cloudNodeId()) || !libraryId.equals(buffer.cloudLibraryId()) || !mode.equals(buffer.cloudMode())) return false;
            if (allocationEpoch != buffer.cloudAllocationEpoch()) return false;
            // Pages are stable only for the observed catalogue. Successful leases do not
            // increment this revision; uploads, removals and catalyst edits do.
            if (library.allocationRevision() != observedRevision) return false;
            if (index.buildStep()) return true;
            if (entry == null) {
                UUID entryId;
                if (priorityCursor < priorityEntries.size()) entryId = priorityEntries.get(priorityCursor++);
                else {
                    if (incremental || cursor >= entryCount) { buffer.cloudScanFinished(observedRevision); return false; }
                    if (page.isEmpty() || cursor >= pageOffset + page.size()) { pageOffset = cursor; page = library.entryIdPage(cursor, 64); }
                    entryId = page.get(cursor++ - pageOffset);
                }
                entry = library.entry(entryId); recipeCursor = 0; matches = 0; matchedRecipe = null;
                boolean owned = entry.lease() != null && entry.lease().nodeId().equals(buffer.cloudNodeId());
                if (entry.status() != CloudLedger.Status.AVAILABLE && !owned) { entry = null; return true; }
                var types = PatternQuickUploadMetadata.readRecipeTypeIds(entry.original());
                if (!types.isEmpty() && !types.contains(buffer.cloudMode())) { reject(buffer, "cloud_type"); return true; }
                probe = buffer.cloudProbe(entry);
                if (probe == null) { reject(buffer, "cloud_decode"); return true; }
                recipes = index.candidates(probe);
            }
            if (recipeCursor < recipes.size()) {
                GTRecipe recipe = recipes.get(recipeCursor++);
                if (CloudPatternMatching.matches(recipe, machine, probe)) { matches++; matchedRecipe = recipe; }
                if (matches > 1) { reject(buffer, "cloud_ambiguous"); return true; }
            }
            if (recipeCursor == recipes.size()) {
                if (matches == 1 && entry.status() == CloudLedger.Status.AVAILABLE && buffer.iv$getState().accepting) {
                    // Same library/mode/original/probe shares one election across scans.
                    var level = (net.minecraft.server.level.ServerLevel)buffer.getLevel();
                    var queue = QUEUES.get(level.getServer());
                    var key = new SelectionKey(new Group(libraryId, mode), entry.id(), observedRevision, index, probe);
                    var selection = queue.selection(key, recipes);
                    selection.step(queue, level);
                    // Readiness checks can re-enter membership notifications. Never use
                    // a detached election or authority/endpoint epoch after that callback.
                    if (queue.selections.get(key) != selection || library.allocationRevision() != observedRevision
                            || buffer.cloudAllocationEpoch() != allocationEpoch) return true;
                    if (!selection.complete) return true;
                    if (!selection.preferredValid()) { queue.selections.remove(key); return true; }
                    if (queue.selections.get(key) != selection || library.allocationRevision() != observedRevision
                            || buffer.cloudAllocationEpoch() != allocationEpoch) return true;
                    if (selection.preferred == buffer && entry.status() == CloudLedger.Status.AVAILABLE) {
                        buffer.cloudLeased(entry); CloudPatternDiagnostics.clear(library, entry.id());
                    }
                }
                if (matches == 1) { buffer.cloudValidateLease(entry); CloudPatternDiagnostics.clear(library, entry.id(), buffer.cloudNodeId()); }
                if (matches != 1) { reject(buffer, "iv_match"); return true; }
                entry = null;
            }
            return true;
        }
        private void reject(CloudPatternBufferMachine buffer, String reason) {
            CloudPatternDiagnostics.record(library, entry.id(), buffer.cloudNodeId(), reason);
            buffer.cloudInvalidLease(entry); entry = null;
        }
    }
    public static void register(CloudPatternBufferMachine buffer) {
        if (buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            QUEUES.computeIfAbsent(level.getServer(), ignored -> new Queue()).endpoints.add(buffer);
            availabilityChanged(buffer);
        }
    }
    /** Readiness changes wake otherwise idle competitors, without rescanning every idle tick. */
    public static void availabilityChanged(CloudPatternBufferMachine buffer) {
        if (!(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Queue queue = QUEUES.get(level.getServer()); if (queue == null) return;
        queue.invalidateSelections(buffer.cloudLibraryId());
        var affected = Collections.newSetFromMap(new IdentityHashMap<CloudPatternBufferMachine, Boolean>());
        for (var peer : List.copyOf(queue.endpoints))
            if (Objects.equals(peer.cloudLibraryId(), buffer.cloudLibraryId())) affected.add(peer);
        // Enqueue must see the invalidated scan removed, rather than waiting for the
        // old epoch to exit and the next five-tick maintenance pass to enqueue again.
        queue.order.removeIf(affected::contains);
        affected.forEach(queue.scans::remove);
        for (var peer : affected) {
            try { peer.cloudAllocationChanged(); }
            catch (RuntimeException failure) {
                peer.cloudClassificationFailed();
                com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.error(peer, null, "CLOUD_CLASSIFY", failure);
            }
        }
    }
    /** Authority mutation notification. Only loaded registered endpoints are touched; no chunk or catalogue scan here. */
    public static void libraryChanged(net.minecraft.server.level.ServerLevel level, UUID libraryId) {
        if (libraryId == null || !level.getServer().isSameThread()) return;
        Queue queue = QUEUES.get(level.getServer()); if (queue == null) return;
        queue.invalidateSelections(libraryId);
        var affected = Collections.newSetFromMap(new IdentityHashMap<CloudPatternBufferMachine, Boolean>());
        for (var endpoint : List.copyOf(queue.endpoints)) {
            if (!libraryId.equals(endpoint.cloudLibraryId())) continue;
            Scan scan = queue.scans.get(endpoint);
            // Repeated notifications for the already queued authority revision coalesce.
            if (scan != null && scan.observedRevision == scan.library.allocationRevision()
                    && scan.allocationEpoch == endpoint.cloudAllocationEpoch()) continue;
            affected.add(endpoint);
        }
        if (affected.isEmpty()) return;
        // Retire stale scans before callbacks enqueue replacements. Iteration uses a
        // snapshot because readiness checks can indirectly update endpoint membership.
        queue.order.removeIf(affected::contains);
        affected.forEach(queue.scans::remove);
        for (var endpoint : affected) {
            try { endpoint.cloudLibraryChanged(); }
            catch (RuntimeException failure) {
                endpoint.cloudClassificationFailed();
                com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.error(endpoint, null, "CLOUD_CLASSIFY", failure);
            }
        }
    }
    private static int compare(CloudPatternBufferMachine candidate, CloudPatternBufferMachine other, net.minecraft.server.level.ServerLevel level) {
        return Comparator.comparingInt((CloudPatternBufferMachine b) -> -CloudLibraryService.priority(level, b.cloudLibraryId(), b.cloudNodeId()))
                .thenComparingLong(CloudPatternBufferMachine::cloudAllocationOrder)
                .thenComparing(b -> b.getLevel().dimension().location().toString()).thenComparingLong(b -> b.getPos().asLong()).compare(candidate, other);
    }
    public static void originalRemoved(net.minecraft.server.level.ServerLevel level, UUID library, UUID entry, CloudLedger.Lease lease) {
        Queue queue = QUEUES.get(level.getServer()); if (queue == null) return;
        for (var endpoint : List.copyOf(queue.endpoints))
            if (library.equals(endpoint.cloudLibraryId()) && lease.nodeId().equals(endpoint.cloudNodeId()))
                endpoint.cloudOriginalRemoved(entry, lease.stableSlot());
    }
    public static void enqueue(CloudPatternBufferMachine buffer) {
        if (!(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Queue queue = QUEUES.computeIfAbsent(level.getServer(), ignored -> new Queue());
        try {
            queue.regroup(buffer);
            if (!buffer.cloudReady(true) || queue.scans.containsKey(buffer)) return;
            queue.scans.put(buffer, new Scan(buffer)); queue.order.addLast(buffer);
        } catch (RuntimeException failure) {
            // Readiness and scan creation read durable authority too. A failed shard
            // must stop this endpoint, including callbacks from another node's finally.
            queue.scans.remove(buffer); queue.order.remove(buffer); queue.ungroup(buffer);
            buffer.cloudClassificationFailed();
            com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.error(buffer, null, "CLOUD_CLASSIFY", failure);
        }
    }
    public static boolean isScanning(CloudPatternBufferMachine buffer) {
        if (!(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        Queue queue = QUEUES.get(level.getServer()); return queue != null && queue.scans.containsKey(buffer);
    }
    public static void remove(CloudPatternBufferMachine buffer) {
        for (Queue queue : QUEUES.values()) { queue.order.remove(buffer); queue.scans.remove(buffer); queue.endpoints.remove(buffer); queue.ungroup(buffer); }
        availabilityChanged(buffer);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Queue queue = QUEUES.get(event.getServer()); if (queue == null) return;
        long end = System.nanoTime() + NANOS_PER_TICK;
        var touched = Collections.newSetFromMap(new IdentityHashMap<CloudPatternBufferMachine, Boolean>());
        for (int steps = 0; steps < STEPS_PER_TICK && !queue.order.isEmpty() && System.nanoTime() < end; steps++) {
            var buffer = queue.order.removeFirst(); Scan scan = queue.scans.get(buffer);
            touched.add(buffer);
            try {
                if (scan != null && scan.step(buffer)) queue.order.addLast(buffer); else queue.scans.remove(buffer);
            } catch (RuntimeException failure) {
                queue.scans.remove(buffer); buffer.cloudClassificationFailed();
                com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.error(buffer, null, "CLOUD_CLASSIFY", failure);
            }
        }
        for (var buffer : touched) {
            try { buffer.cloudPublishChanges(); }
            catch (RuntimeException failure) {
                buffer.cloudClassificationFailed();
                com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.error(buffer, null, "CLOUD_CLASSIFY", failure);
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { QUEUES.remove(event.getServer()); CloudPatternMatching.clear(event.getServer()); }
    @SubscribeEvent public static void reloaded(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        CloudPatternMatching.clear(event.getPlayerList().getServer());
        Queue queue = QUEUES.get(event.getPlayerList().getServer()); if (queue == null) return;
        queue.scans.clear(); queue.order.clear(); queue.selections.clear();
        queue.endpoints.forEach(CloudPatternBufferMachine::cloudRecipesChanged);
    }
}
