package com.gtl.enhancedcore.common.cloud;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
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
    private record DiagnosticKey(UUID library, UUID entry, UUID node) {}
    private record Ambiguity(List<String> candidates, int total) {}
    private static final class Queue {
        final Deque<CloudPatternBufferMachine> order = new ArrayDeque<>();
        final Map<CloudPatternBufferMachine, Scan> scans = new IdentityHashMap<>();
        final Set<CloudPatternBufferMachine> endpoints = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<DiagnosticKey, Ambiguity> ambiguities = new LinkedHashMap<>(16, .75f, true);
        void ambiguity(CloudPatternBufferMachine buffer, CloudPatternStore.Entry entry, CloudPatternMatching.MatchSet matches) {
            var key = new DiagnosticKey(buffer.cloudLibraryId(), entry.id(), buffer.cloudNodeId());
            var evidence = new Ambiguity(matches.candidateIds(), matches.candidateCount());
            if (!evidence.equals(ambiguities.put(key, evidence))) {
                var machine = IvBuffers.controller(buffer);
                com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.event(buffer, null, "CLOUD_CLASSIFY", "cloud_ambiguous",
                        "library", key.library, "entry", key.entry, "node", key.node, "mode", buffer.cloudMode().toString(),
                        "machine", machine.getDefinition().getId().toString(), "machinePos", machine.getPos().toShortString(),
                        "candidates", evidence.candidates, "candidateCount", evidence.total);
            }
            while (ambiguities.size() > 4096) ambiguities.remove(ambiguities.keySet().iterator().next());
        }
        void clearAmbiguity(CloudPatternBufferMachine buffer, CloudPatternStore.Entry entry) {
            ambiguities.remove(new DiagnosticKey(buffer.cloudLibraryId(), entry.id(), buffer.cloudNodeId()));
        }
    }
    private static final class Scan {
        final List<WorkableMultiblockMachine> controllers;
        WorkableMultiblockMachine machine;
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
        int cursor, recipeCursor, machineCursor;
        CloudPatternMatching.MatchSet matches;
        CloudPatternMatching.MatchSet ambiguousMatches;
        CloudPatternStore.Entry entry;
        CloudPatternMatching.Probe probe;
        Scan(CloudPatternBufferMachine buffer) {
            controllers = IvBuffers.cloudControllers(buffer); library = buffer.cloudStore().library(buffer.cloudLibraryId());
            nodeId = buffer.cloudNodeId(); libraryId = buffer.cloudLibraryId(); mode = buffer.cloudMode();
            allocationEpoch = buffer.cloudAllocationEpoch();
            observedRevision = library.allocationRevision(); entryCount = library.entryCount();
            var changes = library.allocationChangesSince(buffer.cloudScannedRevision());
            incremental = changes != null;
            priorityEntries = incremental ? changes : library.recentAllocationChanges();
            var type = Arrays.stream(controllers.getFirst().getDefinition().getRecipeTypes()).filter(t -> buffer.cloudAcceptsMode(t.registryName)).findFirst().orElseThrow();
            var level = (net.minecraft.server.level.ServerLevel)buffer.getLevel();
            index = CloudPatternMatching.index(level, type);
        }
        boolean step(CloudPatternBufferMachine buffer) {
            if (!IvBuffers.cloudControllers(buffer).equals(controllers) || !buffer.cloudReady(false)) return false;
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
                entry = library.entry(entryId); machineCursor = 0; machine = null; ambiguousMatches = null;
                boolean owned = entry.lease(buffer.cloudNodeId()) != null;
                if (entry.status() != CloudLedger.Status.AVAILABLE && entry.status() != CloudLedger.Status.LEASED && !owned) { entry = null; return true; }
                var types = PatternQuickUploadMetadata.readRecipeTypeIds(entry.original());
                if (!CloudChemicalModes.allows(types, buffer.cloudMode())) { reject(buffer, "cloud_type"); return true; }
            }
            if (machine == null) {
                if (machineCursor >= controllers.size()) {
                    if (ambiguousMatches != null) {
                        QUEUES.get(buffer.getLevel().getServer()).ambiguity(buffer, entry, ambiguousMatches);
                        reject(buffer, "cloud_ambiguous");
                    } else {
                        QUEUES.get(buffer.getLevel().getServer()).clearAmbiguity(buffer, entry);
                        reject(buffer, "iv_match");
                    }
                    return true;
                }
                machine = controllers.get(machineCursor++); recipeCursor = 0; matches = new CloudPatternMatching.MatchSet(index);
                probe = buffer.cloudProbe(entry, machine);
                if (probe == null) { reject(buffer, "cloud_decode"); return true; }
                recipes = index.candidates(probe);
            }
            if (recipeCursor < recipes.size()) {
                GTRecipe recipe = recipes.get(recipeCursor++);
                matches.add(recipe, machine, probe);
            }
            if (recipeCursor == recipes.size()) {
                if (matches.unique() == null) {
                    if (matches.ambiguous() && ambiguousMatches == null) ambiguousMatches = matches;
                    machine = null; return true;
                }
                QUEUES.get(buffer.getLevel().getServer()).clearAmbiguity(buffer, entry);
                if (entry.lease(buffer.cloudNodeId()) == null && buffer.iv$getState().accepting
                        && (entry.status() == CloudLedger.Status.AVAILABLE || entry.status() == CloudLedger.Status.LEASED)) {
                    buffer.cloudLeased(entry); CloudPatternDiagnostics.clear(library, entry.id(), buffer.cloudNodeId());
                }
                if (matches.unique() != null) { buffer.cloudValidateLease(entry); CloudPatternDiagnostics.clear(library, entry.id(), buffer.cloudNodeId()); }
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
    public static void originalRemoved(net.minecraft.server.level.ServerLevel level, UUID library, UUID entry, CloudLedger.Lease lease) {
        Queue queue = QUEUES.get(level.getServer()); if (queue == null) return;
        for (var endpoint : List.copyOf(queue.endpoints))
            if (library.equals(endpoint.cloudLibraryId()) && lease.nodeId().equals(endpoint.cloudNodeId()))
                endpoint.cloudOriginalRemoved(entry, lease.stableSlot());
    }
    /** AE rotates equal patterns between their providers. Clip a large smart batch to
     * one fair share so its first provider cannot reserve the entire plan. Read only. */
    public static long dispatchShare(CloudPatternBufferMachine buffer, appeng.api.crafting.IPatternDetails pattern, long requested) {
        if (requested <= 0 || !(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return requested;
        Queue queue = QUEUES.get(level.getServer()); if (queue == null) return requested;
        var tag = pattern.getDefinition().getTag();
        if (tag == null) return requested;
        var route = tag.getCompound(IvBuffers.ROUTE_KEY);
        if (!route.hasUUID("entry")) return requested;
        UUID entry = route.getUUID("entry");
        var backlogs = new ArrayList<java.math.BigInteger>(); var priorities = new ArrayList<Integer>(); int selected = -1;
        for (var peer : List.copyOf(queue.endpoints)) {
            if (!Objects.equals(buffer.cloudLibraryId(), peer.cloudLibraryId()) || peer.getGrid() != buffer.getGrid()
                    || !peer.iv$getState().accepting || peer.iv$getState().jobs.size() >= IvBuffers.MAX_QUEUED_DISPATCHES
                    || !peer.cloudReady(true) || !peer.getAvailablePatterns().contains(pattern)) continue;
            if (peer == buffer) selected = backlogs.size();
            var pending = java.math.BigInteger.ZERO;
            for (var job : peer.iv$getState().jobs) if (entry.equals(job.cloudPattern) && !job.cancelled)
                pending = pending.add(CloudDispatchShares.repetitions(job.remaining, job.parallel, job.cloudPatternOperations));
            backlogs.add(pending); priorities.add(peer.getPatternPriority());
        }
        return selected < 0 || backlogs.size() <= 1 ? requested : CloudDispatchShares.share(requested, backlogs, priorities, selected);
    }
    public static void enqueue(CloudPatternBufferMachine buffer) {
        if (!(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        Queue queue = QUEUES.computeIfAbsent(level.getServer(), ignored -> new Queue());
        try {
            if (!buffer.cloudReady(true) || queue.scans.containsKey(buffer)) return;
            queue.scans.put(buffer, new Scan(buffer)); queue.order.addLast(buffer);
        } catch (RuntimeException failure) {
            // Readiness and scan creation read durable authority too. A failed shard
            // must stop this endpoint, including callbacks from another node's finally.
            queue.scans.remove(buffer); queue.order.remove(buffer);
            buffer.cloudClassificationFailed();
            com.gtl.enhancedcore.common.recipe.iv.IvTaskLog.error(buffer, null, "CLOUD_CLASSIFY", failure);
        }
    }
    public static boolean isScanning(CloudPatternBufferMachine buffer) {
        if (!(buffer.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        Queue queue = QUEUES.get(level.getServer()); return queue != null && queue.scans.containsKey(buffer);
    }
    public static void remove(CloudPatternBufferMachine buffer) {
        for (Queue queue : QUEUES.values()) { queue.order.remove(buffer); queue.scans.remove(buffer); queue.endpoints.remove(buffer); }
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
        queue.scans.clear(); queue.order.clear();
        queue.endpoints.forEach(CloudPatternBufferMachine::cloudRecipesChanged);
    }
}
