package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.recipe.iv.IvBuffers;
import com.gtl.enhancedcore.common.recipe.iv.IvRecipeInputs;
import appeng.api.stacks.AEKey;
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
    private static final class Queue {
        final Deque<CloudPatternBufferMachine> order = new ArrayDeque<>();
        final Map<CloudPatternBufferMachine, Scan> scans = new IdentityHashMap<>();
        final Set<CloudPatternBufferMachine> endpoints = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<ResourceLocation, RecipeIndex> indexes = new HashMap<>();
        final Map<Group, NavigableMap<Long, CloudPatternBufferMachine>> groups = new HashMap<>();
        final Map<CloudPatternBufferMachine, Membership> memberships = new IdentityHashMap<>();
        long sequence;
        void regroup(CloudPatternBufferMachine buffer) {
            var group = buffer.cloudLibraryId() == null || buffer.cloudMode() == null ? null : new Group(buffer.cloudLibraryId(), buffer.cloudMode());
            var old = memberships.get(buffer);
            if (old != null && old.group.equals(group)) return;
            ungroup(buffer);
            if (group != null) {
                long id = ++sequence; memberships.put(buffer, new Membership(group, id));
                groups.computeIfAbsent(group, ignored -> new TreeMap<>()).put(id, buffer);
            }
        }
        void ungroup(CloudPatternBufferMachine buffer) {
            var old = memberships.remove(buffer); if (old == null) return;
            var group = groups.get(old.group); if (group == null) return;
            group.remove(old.sequence); if (group.isEmpty()) groups.remove(old.group);
        }
    }
    private static final class RecipeIndex {
        final List<GTRecipe> all;
        final Map<AEKey, List<GTRecipe>> byOutput = new HashMap<>();
        int cursor;
        RecipeIndex(List<GTRecipe> recipes) { all = recipes; }
        boolean buildStep() {
            if (cursor >= all.size()) return false;
            var recipe = all.get(cursor++);
            try { IvRecipeInputs.outputs(recipe.outputs).keySet().forEach(key -> byOutput.computeIfAbsent(key, ignored -> new ArrayList<>()).add(recipe)); }
            catch (IllegalArgumentException | ArithmeticException unsupported) { /* Unsupported capabilities fail admission too. */ }
            return true;
        }
        List<GTRecipe> candidates(CloudPatternMatching.Probe probe) {
            List<GTRecipe> smallest = all;
            for (AEKey output : probe.outputs().keySet()) {
                List<GTRecipe> group = byOutput.getOrDefault(output, List.of());
                if (group.size() < smallest.size()) smallest = group;
            }
            return smallest;
        }
    }
    private static final class Scan {
        final WorkableElectricMultiblockMachine machine;
        final CloudPatternStore.Library library;
        final UUID nodeId, libraryId;
        final ResourceLocation mode;
        final long allocationEpoch;
        final int entryCount;
        List<UUID> page = List.of();
        int pageOffset;
        final RecipeIndex index;
        List<GTRecipe> recipes = List.of();
        long observedRevision;
        int cursor, recipeCursor, matches;
        CloudPatternStore.Entry entry;
        GTRecipe matchedRecipe;
        CloudPatternMatching.Probe probe;
        long endpointCursor;
        CloudPatternBufferMachine preferred;
        CloudPatternBufferMachine selectionCandidate;
        int selectionRecipeCursor, selectionMatches;
        Scan(CloudPatternBufferMachine buffer) {
            machine = IvBuffers.controller(buffer); library = buffer.cloudStore().library(buffer.cloudLibraryId());
            nodeId = buffer.cloudNodeId(); libraryId = buffer.cloudLibraryId(); mode = buffer.cloudMode();
            allocationEpoch = buffer.cloudAllocationEpoch();
            observedRevision = library.allocationRevision(); entryCount = library.entryCount();
            var type = Arrays.stream(machine.getDefinition().getRecipeTypes()).filter(t -> buffer.cloudAcceptsMode(t.registryName)).findFirst().orElseThrow();
            var level = (net.minecraft.server.level.ServerLevel)buffer.getLevel();
            index = QUEUES.computeIfAbsent(level.getServer(), ignored -> new Queue()).indexes.computeIfAbsent(type.registryName,
                    ignored -> new RecipeIndex(CloudPatternMatching.recipes(machine.getLevel(), type)));
        }
        boolean step(CloudPatternBufferMachine buffer) {
            if (!buffer.cloudReady(true) || IvBuffers.controller(buffer) != machine) return false;
            if (!nodeId.equals(buffer.cloudNodeId()) || !libraryId.equals(buffer.cloudLibraryId()) || !mode.equals(buffer.cloudMode())) return false;
            if (allocationEpoch != buffer.cloudAllocationEpoch()) return false;
            // Pages are stable only for the observed catalogue. Successful leases do not
            // increment this revision; uploads, removals and catalyst edits do.
            if (library.allocationRevision() != observedRevision) return false;
            if (index.buildStep()) return true;
            if (entry == null) {
                if (cursor >= entryCount) { buffer.cloudScanFinished(observedRevision); return false; }
                if (page.isEmpty() || cursor >= pageOffset + page.size()) { pageOffset = cursor; page = library.entryIdPage(cursor, 64); }
                entry = library.entry(page.get(cursor++ - pageOffset)); recipeCursor = 0; matches = 0; matchedRecipe = null;
                endpointCursor = -1; preferred = null; selectionCandidate = null;
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
                    // One endpoint per budget step. Never scan every machine inside one step.
                    var level = (net.minecraft.server.level.ServerLevel)buffer.getLevel();
                    var queue = QUEUES.get(level.getServer());
                    var group = queue.groups.get(new Group(libraryId, mode));
                    if (selectionCandidate != null) {
                        var candidate = selectionCandidate;
                        if (!candidate.cloudReady(true) || !candidate.iv$getState().accepting) { selectionCandidate = null; return true; }
                        // A contender must have its own unique match. In particular a native
                        // CWU-capable machine may be ambiguous where an own IV machine is not.
                        if (selectionRecipeCursor < recipes.size()
                                && CloudPatternMatching.matches(recipes.get(selectionRecipeCursor++), IvBuffers.controller(candidate), probe)) selectionMatches++;
                        if (selectionMatches > 1) { selectionCandidate = null; return true; }
                        if (selectionRecipeCursor < recipes.size()) return true;
                        if (selectionMatches == 1 && (preferred == null || compare(candidate, preferred, level) < 0)) preferred = candidate;
                        selectionCandidate = null; return true;
                    }
                    var next = group == null ? null : group.higherEntry(endpointCursor);
                    if (next != null) {
                        endpointCursor = next.getKey(); var candidate = next.getValue();
                        if (candidate.cloudReady(true) && candidate.iv$getState().accepting) {
                            selectionCandidate = candidate; selectionRecipeCursor = 0; selectionMatches = 0;
                        }
                        return true;
                    }
                    if (preferred == buffer) { buffer.cloudLeased(entry); CloudPatternDiagnostics.clear(library, entry.id()); }
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
        queue.regroup(buffer);
        for (var peer : queue.endpoints) if (Objects.equals(peer.cloudLibraryId(), buffer.cloudLibraryId())) peer.cloudAllocationChanged();
    }
    /** Authority mutation notification. Only loaded registered endpoints are touched; no chunk or catalogue scan here. */
    public static void libraryChanged(net.minecraft.server.level.ServerLevel level, UUID libraryId) {
        if (libraryId == null || !level.getServer().isSameThread()) return;
        Queue queue = QUEUES.get(level.getServer()); if (queue == null) return;
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
            try { endpoint.cloudAllocationChanged(); }
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
        if (!buffer.cloudReady(true) || !(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Queue queue = QUEUES.computeIfAbsent(level.getServer(), ignored -> new Queue());
        if (queue.scans.containsKey(buffer)) return;
        queue.scans.put(buffer, new Scan(buffer)); queue.order.addLast(buffer);
    }
    public static boolean isScanning(CloudPatternBufferMachine buffer) {
        if (!(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        Queue queue = QUEUES.get(level.getServer()); return queue != null && queue.scans.containsKey(buffer);
    }
    public static void remove(CloudPatternBufferMachine buffer) {
        availabilityChanged(buffer);
        for (Queue queue : QUEUES.values()) { queue.order.remove(buffer); queue.scans.remove(buffer); queue.endpoints.remove(buffer); queue.ungroup(buffer); }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Queue queue = QUEUES.get(event.getServer()); if (queue == null) return;
        long end = System.nanoTime() + NANOS_PER_TICK;
        for (int steps = 0; steps < STEPS_PER_TICK && !queue.order.isEmpty() && System.nanoTime() < end; steps++) {
            var buffer = queue.order.removeFirst(); Scan scan = queue.scans.get(buffer);
            try {
                if (scan != null && scan.step(buffer)) queue.order.addLast(buffer); else queue.scans.remove(buffer);
            } catch (RuntimeException failure) {
                queue.scans.remove(buffer); buffer.cloudClassificationFailed();
                com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.error(buffer, null, "CLOUD_CLASSIFY", failure);
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { QUEUES.remove(event.getServer()); }
    @SubscribeEvent public static void reloaded(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        Queue queue = QUEUES.get(event.getPlayerList().getServer()); if (queue == null) return;
        queue.indexes.clear(); queue.scans.clear(); queue.order.clear();
        queue.endpoints.forEach(CloudPatternBufferMachine::cloudRecipesChanged);
    }
}
