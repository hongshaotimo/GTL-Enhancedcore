package com.gtl.enhancedcore.common.cloud;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEKey;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.transfer.CloudCatalystSource;
import com.gtl.enhancedcore.common.cloud.ui.CloudPatternSearch;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.misc.FluidStorage;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.integration.ae2.pattern.PatternQuickUploadMetadata;

/** Authority shared by the central menu and cloud executors; never called from client rendering. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "gtl_enhancedcore")
public final class CloudLibraryService {
    public static final String ADDRESS = "gtl_cloud_library";
    public static final int PAGE_SIZE = 36;
    public static final long FLUID_CAPACITY = 16_000;
    private CloudLibraryService() {}

    public static boolean authorized(ServerPlayer player, UUID libraryId, MetaMachine endpoint) {
        if (!(endpoint.getLevel() instanceof ServerLevel level) || player.serverLevel() != level || libraryId == null) return false;
        if (!level.getServer().isSameThread()) return false;
        var pos = endpoint.getPos();
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(pos) != endpoint.getHolder().getSelf() || !level.mayInteract(player, pos)) return false;
        try {
            return runtimeAuthorized(player.getUUID(), libraryId, level, pos);
        } catch (RuntimeException invalid) { return false; }
    }

    public static boolean runtimeAuthorized(UUID actor, UUID libraryId, ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (actor == null || libraryId == null || !level.getServer().isSameThread()) return false;
        try {
            var library = CloudPatternStore.get(level).library(libraryId);
            if (!library.authorized(actor) || !sameTeam(library.owner(), actor)) return false;
            if (!FTBChunksAPI.api().isManagerLoaded()) return false;
            var claim = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4));
            if (claim == null) return true;
            var team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(actor);
            return team.isPresent() && team.get().getId().equals(claim.getTeamData().getTeam().getId());
        } catch (RuntimeException failure) { return false; }
    }

    public static int priority(ServerLevel level, UUID libraryId, UUID nodeId) {
        return CloudPatternStore.get(level).library(libraryId).catalysts().getCompound("nodePriorities").getInt(nodeId.toString());
    }
    public static void adjustPriority(ServerLevel level, UUID libraryId, UUID actor, UUID entryId, int direction) {
        var library = CloudPatternStore.get(level).library(libraryId);
        if (!library.authorized(actor)) throw new SecurityException("Library denied");
        var lease = library.entry(entryId).lease();
        if (lease == null) throw new IllegalStateException("Select a pattern leased to the target node");
        CompoundTag saved = library.catalysts(), priorities = saved.getCompound("nodePriorities");
        String node = lease.nodeId().toString(); int previous = priorities.getInt(node);
        priorities.putInt(node, Math.max(-1_000_000, Math.min(1_000_000, previous + Integer.signum(direction))));
        saved.put("nodePriorities", priorities); library.setCatalysts(actor, saved); CloudPatternStore.get(level).checkpoint();
    }

    public static boolean sameTeam(UUID owner, UUID player) {
        if (owner.equals(player)) return true;
        var manager = FTBTeamsAPI.api().getManager();
        var a = manager.getTeamForPlayerID(owner);
        var b = manager.getTeamForPlayerID(player);
        return a.isPresent() && b.isPresent() && a.get().getId().equals(b.get().getId());
    }

    public static boolean claimAllows(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (!FTBChunksAPI.api().isManagerLoaded()) return false;
        var claim = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(level.dimension(), pos.getX() >> 4, pos.getZ() >> 4));
        if (claim == null) return true;
        var team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(player.getUUID());
        return team.isPresent() && team.get().getId().equals(claim.getTeamData().getTeam().getId());
    }

    public static ItemStackTransfer itemContents(CompoundTag state) {
        var inventory = new ItemStackTransfer(9);
        if (state.contains("items", Tag.TAG_COMPOUND)) inventory.deserializeNBT(state.getCompound("items"));
        if (inventory.getSlots() != 9) throw new IllegalArgumentException("Invalid central catalyst inventory");
        return inventory;
    }
    public static FluidTransferList fluidContents(CompoundTag state) {
        FluidStorage[] tanks = new FluidStorage[9];
        for (int i = 0; i < tanks.length; i++) tanks[i] = new FluidStorage(FLUID_CAPACITY);
        var fluids = new FluidTransferList(tanks);
        if (state.contains("fluids", Tag.TAG_COMPOUND)) fluids.deserializeNBT(state.getCompound("fluids"));
        return fluids;
    }
    public static Map<AEKey, Long> catalysts(ServerLevel level, UUID libraryId) {
        CompoundTag saved = CloudPatternStore.get(level).library(libraryId).catalysts();
        return CloudCatalystSource.snapshot(itemContents(saved), fluidContents(saved));
    }
    public static Map<AEKey, Long> catalysts(CloudPatternStore.Entry entry) {
        CompoundTag saved = entry.catalysts();
        boolean imported = saved.getInt("schema") == 1 && saved.getString("kind").equals("PATTERN");
        Map<AEKey, Long> result = new LinkedHashMap<>(imported
                ? com.gtl.enhancedcore.common.cloud.transfer.CloudImportedCatalysts.snapshot(saved)
                : CloudCatalystSource.snapshot(itemContents(saved), fluidContents(saved)));
        com.gtl.enhancedcore.common.cloud.transfer.CloudImportedCatalysts.snapshot(entry.importedSharedCatalysts())
                .forEach((key, amount) -> result.merge(key, amount, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b));
        return Map.copyOf(result);
    }

    public static void writeAddress(ItemStack stick, UUID libraryId, UUID owner) {
        CompoundTag address = new CompoundTag(); address.putUUID("library", libraryId); address.putUUID("owner", owner);
        stick.getOrCreateTag().put(ADDRESS, address);
    }

    private record TerminalSide(String keys, int[] items, int[] fluids) {
        boolean matches(String query, BitSet itemMatches, BitSet fluidMatches) {
            if(query.isEmpty() || keys.contains(query)) return true;
            for(int id:items) if(itemMatches.get(id)) return true;
            for(int id:fluids) if(fluidMatches.get(id)) return true;
            return false;
        }
    }
    private record CatalogRow(UUID id, String name, Set<String> types, int[] items, int[] fluids, boolean exported,
            boolean terminalVisible, TerminalSide input, TerminalSide output) {}
    private record TerminalQuery(String input,String output,BitSet inputItems,BitSet inputFluids,BitSet outputItems,BitSet outputFluids) {
        TerminalQuery {
            inputItems=(BitSet)inputItems.clone(); inputFluids=(BitSet)inputFluids.clone();
            outputItems=(BitSet)outputItems.clone(); outputFluids=(BitSet)outputFluids.clone();
        }
    }
    private record Query(String text, String type, CloudPatternSearch localized) {}
    private static final class Catalog {
        long revision = Long.MIN_VALUE, locationRevision = Long.MIN_VALUE;
        boolean rebuild = true;
        LinkedHashMap<UUID, CatalogRow> rows = new LinkedHashMap<>();
        final Set<UUID> dirty = new LinkedHashSet<>();
        Set<String> types = new TreeSet<>();
        List<UUID> visible = List.of();
        long[] searchItems = new long[0], searchFluids = new long[0];
        final Map<UUID, String> targets = new HashMap<>();
        final LinkedHashMap<Query, List<UUID>> searches = new LinkedHashMap<>(32, .75f, true);
        final LinkedHashMap<TerminalQuery,List<UUID>> terminalSearches = new LinkedHashMap<>(32,.75f,true);
        CatalogBuild build;
        long failureRevision = Long.MIN_VALUE;
        String failure = "";
        java.lang.ref.WeakReference<ServerLevel> level = new java.lang.ref.WeakReference<>(null);
    }
    // A menu never owns an index. World-library identity keeps results stable across opens.
    private static final Map<CloudPatternStore.Library, Catalog> CATALOGS = new WeakHashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, Deque<CatalogBuild>> BUILDS = new WeakHashMap<>();

    /** 山海：decode and aggregate one row per step; publish only an entire authority revision. */
    private static final class CatalogBuild {
        final CloudPatternStore.Library library;
        final ServerLevel level;
        final Catalog target;
        final long revision;
        final int total;
        final boolean full;
        final List<UUID> dirty;
        final LinkedHashMap<UUID, CatalogRow> rows;
        final List<UUID> visible = new ArrayList<>();
        final Set<String> types = new TreeSet<>();
        final BitSet items = new BitSet(), fluids = new BitSet();
        List<UUID> page = List.of();
        int cursor, pageOffset, summarized;
        Iterator<CatalogRow> summary;
        CatalogBuild(CloudPatternStore.Library library, ServerLevel level, Catalog target) {
            this.library = library; this.level = level; this.target = target;
            revision = library.catalogRevision(); full = target.rebuild || target.dirty.isEmpty();
            dirty = full ? List.of() : List.copyOf(target.dirty);
            total = full ? library.entryCount() : dirty.size();
            rows = full ? new LinkedHashMap<>() : target.rows;
        }
        boolean step() {
            if (revision != library.catalogRevision()) return false;
            if (cursor < total) {
                UUID id;
                if (full) {
                    if (page.isEmpty() || cursor >= pageOffset + page.size()) {
                        pageOffset = cursor; page = library.entryIdPage(cursor, 64);
                    }
                    id = page.get(cursor - pageOffset);
                } else id = dirty.get(cursor);
                rows.put(id, catalogRow(library.entry(id), level)); cursor++; return true;
            }
            if (summary == null) summary = rows.values().iterator();
            if (summary.hasNext()) {
                CatalogRow row = summary.next(); summarized++;
                if (!row.exported) {
                    visible.add(row.id); types.addAll(row.types);
                    for (int id : row.items) if (id >= 0) items.set(id);
                    for (int id : row.fluids) if (id >= 0) fluids.set(id);
                }
                return true;
            }
            target.rows = rows; target.types = types;
            target.visible = Collections.unmodifiableList(visible);
            target.searchItems = items.toLongArray(); target.searchFluids = fluids.toLongArray();
            target.searches.clear(); target.terminalSearches.clear(); target.targets.clear();
            target.rebuild = false; target.dirty.clear(); target.revision = revision;
            target.failureRevision = Long.MIN_VALUE; target.failure = "";
            return false;
        }
    }

    /** Called by authority mutations, before allocation notification. No inventory or decode work here. */
    public static void catalogChanged(CloudPatternStore.Library library, UUID entryId) {
        Catalog catalog = CATALOGS.get(library);
        if (catalog == null) return;
        if (entryId != null && publishSingle(library, catalog, entryId)) return;
        if (entryId == null) catalog.rebuild = true; else catalog.dirty.add(entryId);
        ServerLevel level = catalog.level.get();
        if (level != null) catalog(library, level);
    }

    /** Upload/ACK changes one row. Publish it immediately when the existing catalogue is ready;
     * archive/export mutations fall back to the conservative full summary path. */
    private static boolean publishSingle(CloudPatternStore.Library library, Catalog catalog, UUID entryId) {
        if (catalog.build != null || catalog.rebuild || catalog.revision + 1 != library.catalogRevision()) return false;
        ServerLevel level = catalog.level.get(); if (level == null) return false;
        CatalogRow old = catalog.rows.get(entryId);
        CatalogRow next = catalogRow(library.entry(entryId), level);
        if (old != null && (old.exported != next.exported || !old.types.equals(next.types)
                || !Arrays.equals(old.items, next.items) || !Arrays.equals(old.fluids, next.fluids))) return false;
        catalog.rows.put(entryId, next);
        if (old == null && !next.exported) {
            var visible = new ArrayList<>(catalog.visible); visible.add(entryId); catalog.visible = List.copyOf(visible);
        }
        catalog.types.addAll(next.types);
        for (int id : next.items) if (id >= 0) catalog.searchItems = withBit(catalog.searchItems, id);
        for (int id : next.fluids) if (id >= 0) catalog.searchFluids = withBit(catalog.searchFluids, id);
        catalog.searches.clear(); catalog.terminalSearches.clear(); catalog.targets.clear();
        catalog.dirty.remove(entryId); catalog.failureRevision = Long.MIN_VALUE; catalog.failure = "";
        catalog.revision = library.catalogRevision();
        return true;
    }
    private static long[] withBit(long[] bits, int id) {
        BitSet value = BitSet.valueOf(bits); value.set(id); return value.toLongArray();
    }

    private static CatalogRow catalogRow(CloudPatternStore.Entry original, ServerLevel level) {
        CompoundTag display = metadata(original, level);
        Set<String> recipeTypes = new TreeSet<>(); original.recipeTypes().forEach(t -> recipeTypes.add(t.toString()));
        String searchable = display.getString("name") + " " + display.getString("author") + " " + display.getString("authorId")
                + " " + display.getString("typeNames") + " " + String.join(" ", recipeTypes)
                + (display.getBoolean("hasCircuit") ? " " + display.getInt("circuit") : "");
        return new CatalogRow(original.id(), searchable.toLowerCase(Locale.ROOT), Set.copyOf(recipeTypes),
                display.getIntArray("itemIds"), display.getIntArray("fluidIds"),
                original.status() == CloudLedger.Status.EXPORTED && catalysts(original).isEmpty(),
                original.status()==CloudLedger.Status.AVAILABLE || original.status()==CloudLedger.Status.LEASED
                        || original.status()==CloudLedger.Status.ARCHIVED,
                terminalSide(display,"input"),terminalSide(display,"output"));
    }
    private static TerminalSide terminalSide(CompoundTag display,String prefix) {
        var items=new ArrayList<Integer>();var fluids=new ArrayList<Integer>();var keys=new StringBuilder();
        for(Tag value:display.getList(prefix+"ItemKeys",Tag.TAG_STRING)) {
            var id=net.minecraft.resources.ResourceLocation.tryParse(value.getAsString());
            if(id!=null) { keys.append(id).append('\n');
                if(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id))
                    items.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))); }
        }
        for(Tag value:display.getList(prefix+"FluidKeys",Tag.TAG_STRING)) {
            var id=net.minecraft.resources.ResourceLocation.tryParse(value.getAsString());
            if(id!=null) { keys.append(id).append('\n');
                if(net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id))
                    fluids.add(net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id))); }
        }
        return new TerminalSide(keys.toString(),items.stream().mapToInt(Integer::intValue).toArray(),fluids.stream().mapToInt(Integer::intValue).toArray());
    }
    /** Complete immutable UUID results shared across menus. No original ItemStacks are retained in this query cache. */
    public static List<UUID> terminalCatalogue(CloudPatternStore.Library library,ServerLevel level,String input,String output,
            BitSet inputItems,BitSet inputFluids,BitSet outputItems,BitSet outputFluids) {
        Catalog catalog=catalog(library,level);
        if (catalog.revision != library.catalogRevision()) throw new IllegalStateException("Cloud catalogue is still indexing");
        var key=new TerminalQuery(input,output,inputItems,inputFluids,outputItems,outputFluids);
        var cached=catalog.terminalSearches.get(key);if(cached!=null)return cached;
        var matches=new ArrayList<UUID>();
        for(var row:catalog.rows.values()) if(row.terminalVisible && row.input.matches(input,inputItems,inputFluids)
                && row.output.matches(output,outputItems,outputFluids)) matches.add(row.id);
        var result=List.copyOf(matches);catalog.terminalSearches.put(key,result);
        while(catalog.terminalSearches.size()>32)catalog.terminalSearches.remove(catalog.terminalSearches.keySet().iterator().next());
        return result;
    }
    public static void prepareOpeningCatalog(ServerLevel level, UUID libraryId) {
        var store = CloudPatternStore.get(level);
        if (!store.healthy()) return;
        var library = store.library(libraryId);
        if (library.healthy() && !library.hasOpeningCatalog()) cacheOpeningCatalog(library, catalog(library, level));
    }
    private static Catalog catalog(CloudPatternStore.Library library, ServerLevel level) {
        Catalog catalog = CATALOGS.computeIfAbsent(library, ignored -> new Catalog());
        if (level != null && catalog.level.get() != level) catalog.level = new java.lang.ref.WeakReference<>(level);
        if (catalog.revision != library.catalogRevision() && catalog.failureRevision != library.catalogRevision() && catalog.build == null) {
            CatalogBuild build = new CatalogBuild(library, level, catalog); catalog.build = build;
            if (level == null) {
                // Dependency-light fixtures have no server queue; production callers always supply a level.
                while (build.step()) { }
                catalog.build = null;
            } else BUILDS.computeIfAbsent(level.getServer(), ignored -> new ArrayDeque<>()).addLast(build);
        }
        return catalog;
    }
    /** Display invalidation only; never queues work from menu polling. */
    public static long catalogUiRevision(CloudPatternStore.Library library) {
        Catalog catalog = CATALOGS.get(library);
        return catalog == null ? 0 : 31 * catalog.revision + catalog.failureRevision;
    }
    public static boolean catalogReady(CloudPatternStore.Library library, ServerLevel level) {
        return catalog(library, level).revision == library.catalogRevision();
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tickCatalogs(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Deque<CatalogBuild> queue = BUILDS.get(event.getServer()); if (queue == null) return;
        long deadline = System.nanoTime() + 2_000_000;
        for (int steps = 0; steps < 256 && !queue.isEmpty() && System.nanoTime() < deadline; steps++) {
            CatalogBuild build = queue.removeFirst();
            if (build.target.build != build) continue;
            try {
                if (build.step()) queue.addLast(build);
                else {
                    build.target.build = null;
                    if (build.target.revision != build.library.catalogRevision()) catalog(build.library, build.level);
                    else cacheOpeningCatalog(build.library, build.target);
                }
            } catch (RuntimeException failure) {
                build.target.build = null; build.target.rebuild = true;
                build.target.failureRevision = build.library.catalogRevision();
                build.target.failure = "Cloud catalogue indexing failed: " + failure.getClass().getSimpleName();
                org.slf4j.LoggerFactory.getLogger(CloudLibraryService.class).warn("Cloud catalogue indexing stopped", failure);
            }
        }
    }
    private static void cacheOpeningCatalog(CloudPatternStore.Library library, Catalog catalog) {
        if (catalog.revision != library.catalogRevision() || library.hasOpeningCatalog()) return;
        var opening = new CompoundTag(); var first = new ListTag(); var types = new ListTag();
        catalog.visible.stream().limit(PAGE_SIZE).forEach(id -> first.add(StringTag.valueOf(id.toString())));
        catalog.types.forEach(type -> types.add(StringTag.valueOf(type)));
        opening.put("first", first); opening.put("types", types); opening.putInt("total", catalog.visible.size());
        opening.putLongArray("searchItems", catalog.searchItems); opening.putLongArray("searchFluids", catalog.searchFluids);
        library.cacheOpeningCatalog(opening);
    }
    public static CompoundTag page(ServerLevel level, UUID libraryId, String search, String type, int requestedPage, String selected) {
        return page(level, libraryId, search, type, requestedPage, selected, CloudPatternSearch.EMPTY);
    }
    private static CompoundTag pendingPage(CloudPatternStore.Library library, Catalog catalog, int requestedPage) {
        if (catalog.failureRevision == library.catalogRevision()) throw new IllegalStateException(catalog.failure);
        CompoundTag pending = new CompoundTag();
        pending.putBoolean("indexing", true);
        CatalogBuild build = catalog.build;
        int indexed = build == null ? 0 : build.full ? (int)((build.cursor + (long)build.summarized) / 2) : build.summarized;
        pending.putInt("indexed", indexed); pending.putInt("libraryTotal", library.entryCount());
        int page = Math.max(0, Math.min(1_000_000, requestedPage));
        pending.putInt("page", page); pending.putInt("pages", page + 1); pending.putInt("total", 0);
        pending.put("entries", new ListTag());
        ListTag types = new ListTag(); catalog.types.forEach(type -> types.add(StringTag.valueOf(type)));
        pending.put("types", types);
        pending.putLongArray("searchItems", catalog.searchItems); pending.putLongArray("searchFluids", catalog.searchFluids);
        pending.putLong("revision", library.revision());
        return pending;
    }
    /** Results are complete. Filtering never publishes a partial count or advances on menu polling. */
    public static CompoundTag page(ServerLevel level, UUID libraryId, String search, String type, int requestedPage, String selected, CloudPatternSearch localized) {
        var library = CloudPatternStore.get(level).library(libraryId);
        if (search.isBlank() && type.isEmpty() && requestedPage == 0 && selected.isEmpty()) {
            var opening = library.openingCatalog();
            if (!opening.isEmpty()) {
                var rows = new ListTag();
                for (var id : opening.getList("first", Tag.TAG_STRING)) rows.add(entry(library.entry(UUID.fromString(id.getAsString())), level));
                opening.remove("first"); opening.remove("schema"); opening.remove("catalogRevision");
                opening.put("entries", rows); opening.putInt("page", 0);
                opening.putInt("pages", Math.max(1, (opening.getInt("total") + PAGE_SIZE - 1) / PAGE_SIZE));
                opening.putInt("libraryTotal", library.storedCount()); opening.putInt("indexed", opening.getInt("total"));
                opening.putBoolean("indexing", false); opening.putLong("revision", library.revision());
                opening.putInt("recovery", library.recoveries().size()); return opening;
            }
        }
        Catalog catalog = catalog(library, level);
        if (catalog.revision != library.catalogRevision()) {
            return pendingPage(library, catalog, requestedPage);
        }
        if (!library.hasOpeningCatalog()) cacheOpeningCatalog(library, catalog);
        String query = search.strip().toLowerCase(Locale.ROOT);
        List<UUID> matches;
        if (query.isEmpty() && type.isEmpty()) {
            matches = catalog.visible;
        } else {
            if (catalog.locationRevision != library.locationRevision()) {
                catalog.targets.clear(); catalog.searches.clear(); catalog.locationRevision = library.locationRevision();
            }
            Query key = new Query(query, type, localized);
            matches = catalog.searches.get(key);
            if (matches == null) {
                var found = new ArrayList<UUID>();
                for (UUID id : catalog.visible) {
                    CatalogRow row = catalog.rows.get(id);
                    if (!type.isEmpty() && !type.equals("archived") && !row.types.contains(type)) continue;
                    boolean accepted = query.isEmpty() || row.name.contains(query) || id.toString().contains(query);
                    if (!accepted) {
                        for (int item : row.items) if (localized.matchesItem(item)) { accepted = true; break; }
                        if (!accepted) for (int fluid : row.fluids) if (localized.matchesFluid(fluid)) { accepted = true; break; }
                        if (!accepted) for (String mode : row.types) if (localized.matchesType(mode)) { accepted = true; break; }
                        if (!accepted) accepted = catalog.targets.computeIfAbsent(id, ignored -> targetAddress(library.entry(id), level).toLowerCase(Locale.ROOT)).contains(query);
                    }
                    if (accepted) found.add(id);
                }
                matches = List.copyOf(found); catalog.searches.put(key, matches);
                while (catalog.searches.size() > 32) catalog.searches.remove(catalog.searches.keySet().iterator().next());
            }
        }
        int count = matches.size();
        int page = Math.min(Math.max(0, requestedPage), Math.max(0, (count - 1) / PAGE_SIZE));
        ListTag entries = new ListTag(), types = new ListTag();
        for (int i = page * PAGE_SIZE; i < Math.min(count, (page + 1) * PAGE_SIZE); i++)
            entries.add(entry(library.entry(matches.get(i)), level));
        catalog.types.forEach(t -> types.add(StringTag.valueOf(t)));
        CompoundTag result = new CompoundTag();
        result.put("entries", entries); result.put("types", types);
        result.putLongArray("searchItems", catalog.searchItems); result.putLongArray("searchFluids", catalog.searchFluids);
        result.putInt("page", page); result.putInt("pages", Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE));
        result.putInt("total", count); result.putInt("libraryTotal", library.storedCount());
        result.putBoolean("indexing", false); result.putInt("indexed", count); result.putLong("revision", library.revision());
        result.putInt("recovery", library.recoveries().size());
        if (!selected.isEmpty()) {
            try {
                var detail = library.entry(UUID.fromString(selected)); CompoundTag row = entry(detail, level);
                CloudPatternDiagnostics.latest(library, detail.id()).ifPresent(diagnostic -> {
                    row.putString("diagnostic", diagnostic.reason()); row.putString("diagnosticNode", diagnostic.nodeId().toString());
                });
                result.put("detail", row); result.putLong("revision", detail.revision());
            } catch (IllegalArgumentException | NullPointerException ignored) { }
        }
        return result;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { BUILDS.remove(event.getServer()); CATALOGS.clear(); }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void reloaded(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        // Entry display metadata is a durable shard cache; only this derived in-memory index needs rebuilding.
        BUILDS.clear(); CATALOGS.clear();
    }
    public static void prepareDisplay(CloudPatternStore.Entry entry, ServerLevel level) { metadata(entry, level); }
    private static CompoundTag metadata(CloudPatternStore.Entry entry, ServerLevel level) {
        CompoundTag cached = entry.displayMetadata();
        if (cached.getInt("displaySchema") == 3) return resolveRegistryIds(cached);
        CompoundTag row = new CompoundTag(); ItemStack original = entry.original();
        net.minecraft.network.chat.Component displayName = original.getHoverName();
        var itemIds = new TreeSet<Integer>(); var fluidIds = new TreeSet<Integer>();
        var inputItems=new TreeSet<Integer>();var inputFluids=new TreeSet<Integer>();
        var outputItems=new TreeSet<Integer>();var outputFluids=new TreeSet<Integer>();
        ItemStack icon = original.copy(); int circuit = 0; boolean hasCircuit = false;
        try {
            var details = PatternDetailsHelper.decodePattern(original, level);
            if (details != null) for (var input : details.getInputs()) for (var possible : input.getPossibleInputs()) {
                if (possible.what() instanceof appeng.api.stacks.AEFluidKey fluid)
                    inputFluids.add(net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(fluid.getFluid()));
                if (!(possible.what() instanceof appeng.api.stacks.AEItemKey item)) continue;
                ItemStack candidate = item.toStack();
                if (org.gtlcore.gtlcore.common.data.GTLItems.VIRTUAL_INGREDIENT.isIn(candidate)) {
                    var fluidPayload = org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior.payloadFluidKey(candidate);
                    if (fluidPayload != null) {
                        inputFluids.add(net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(fluidPayload.getFluid()));
                        continue;
                    }
                    var payload = org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior.payloadItemKey(candidate);
                    if (payload != null) candidate = payload.toStack();
                }
                inputItems.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(candidate.getItem()));
                if (com.gregtechceu.gtceu.common.item.IntCircuitBehaviour.isIntegratedCircuit(candidate)) {
                    circuit = com.gregtechceu.gtceu.common.item.IntCircuitBehaviour.getCircuitConfiguration(candidate); hasCircuit = true;
                }
            }
            if (details != null && details.getOutputs().length > 0) {
                row.put("iconKey", details.getOutputs()[0].what().toTagGeneric());
                displayName = details.getOutputs()[0].what().getDisplayName();
                if (details.getOutputs()[0].what() instanceof appeng.api.stacks.AEItemKey item) icon = item.toStack();
                for (var output : details.getOutputs()) {
                    if (output.what() instanceof appeng.api.stacks.AEItemKey item) outputItems.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(item.getItem()));
                    else if (output.what() instanceof appeng.api.stacks.AEFluidKey fluid) outputFluids.add(net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(fluid.getFluid()));
                }
            }
        } catch (RuntimeException ignored) { }
        itemIds.addAll(inputItems);itemIds.addAll(outputItems);fluidIds.addAll(inputFluids);fluidIds.addAll(outputFluids);
        String name = displayName.getString();
        row.putUUID("entry", entry.id()); row.putString("id", entry.id().toString()); row.putString("name", name);
        row.putString("nameJson", net.minecraft.network.chat.Component.Serializer.toJson(displayName));
        row.putString("recipeJson", net.minecraft.network.chat.Component.Serializer.toJson(displayName));
        row.putIntArray("itemIds", itemIds.stream().mapToInt(Integer::intValue).toArray());
        row.putIntArray("fluidIds", fluidIds.stream().mapToInt(Integer::intValue).toArray());
        row.put("icon", icon.save(new CompoundTag()));
        var orderedTypes = entry.recipeTypes().stream().sorted().toList(); ListTag typeIds = new ListTag();
        orderedTypes.forEach(type -> typeIds.add(StringTag.valueOf(type.toString())));
        row.put("types", typeIds); row.putString("type", orderedTypes.isEmpty() ? "" : orderedTypes.getFirst().toString());
        row.putString("typeName", orderedTypes.isEmpty() ? "" : PatternQuickUploadMetadata.recipeTypeName(orderedTypes.getFirst()).getString());
        row.putString("typeNames", orderedTypes.stream().map(type -> PatternQuickUploadMetadata.recipeTypeName(type).getString()).collect(java.util.stream.Collectors.joining(", ")));
        org.gtlcore.gtlcore.integration.ae2.pattern.PatternEncoderMetadata.readEncoder(original).ifPresent(author -> {
            row.putString("author", author.displayName()); row.putString("authorId", author.id().toString());
        });
        CompoundTag config = entry.configuration();
        if (config.contains("circuit", Tag.TAG_INT)) { circuit = config.getInt("circuit"); hasCircuit = true; }
        row.putBoolean("hasCircuit", hasCircuit); if (hasCircuit) row.putInt("circuit", circuit);
        row.putString("recipe", name);
        ListTag itemKeys = new ListTag(), fluidKeys = new ListTag();
        itemIds.forEach(id -> itemKeys.add(StringTag.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(id)).toString())));
        fluidIds.forEach(id -> fluidKeys.add(StringTag.valueOf(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(id)).toString())));
        row.put("itemKeys", itemKeys); row.put("fluidKeys", fluidKeys); row.putInt("displaySchema", 3);
        row.put("inputItemKeys",registryKeys(inputItems,true));row.put("inputFluidKeys",registryKeys(inputFluids,false));
        row.put("outputItemKeys",registryKeys(outputItems,true));row.put("outputFluidKeys",registryKeys(outputFluids,false));
        CompoundTag persisted = row.copy(); persisted.remove("itemIds"); persisted.remove("fluidIds");
        entry.cacheDisplayMetadata(persisted);
        return row;
    }
    private static ListTag registryKeys(Set<Integer> ids,boolean item){
        var keys=new ListTag();for(int id:ids)keys.add(StringTag.valueOf(item
                ?net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(id)).toString()
                :net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(id)).toString()));return keys;
    }
    public static boolean terminalMatches(CloudPatternStore.Entry entry,ServerLevel level,String query,boolean input,BitSet items,BitSet fluids){
        if(query.isEmpty())return true;var row=metadata(entry,level);String prefix=input?"input":"output";
        for(Tag key:row.getList(prefix+"ItemKeys",Tag.TAG_STRING)){var id=net.minecraft.resources.ResourceLocation.tryParse(key.getAsString());
            if(id!=null&&(id.toString().contains(query)||(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)&&items.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))))))return true;}
        for(Tag key:row.getList(prefix+"FluidKeys",Tag.TAG_STRING)){var id=net.minecraft.resources.ResourceLocation.tryParse(key.getAsString());
            if(id!=null&&(id.toString().contains(query)||(net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id)&&fluids.get(net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id))))))return true;}
        return false;
    }
    private static CompoundTag resolveRegistryIds(CompoundTag row) {
        row.putIntArray("itemIds", row.getList("itemKeys", Tag.TAG_STRING).stream()
                .map(Tag::getAsString).map(net.minecraft.resources.ResourceLocation::tryParse).filter(Objects::nonNull)
                .filter(net.minecraft.core.registries.BuiltInRegistries.ITEM::containsKey)
                .mapToInt(id -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))).toArray());
        row.putIntArray("fluidIds", row.getList("fluidKeys", Tag.TAG_STRING).stream()
                .map(Tag::getAsString).map(net.minecraft.resources.ResourceLocation::tryParse).filter(Objects::nonNull)
                .filter(net.minecraft.core.registries.BuiltInRegistries.FLUID::containsKey)
                .mapToInt(id -> net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id))).toArray());
        return row;
    }
    public static CompoundTag entry(CloudPatternStore.Entry entry, ServerLevel level) {
        CompoundTag row = metadata(entry, level);
        row.putLong("revision", entry.revision());
        row.putBoolean("catalystsOnly", entry.status() == CloudLedger.Status.EXPORTED);
        row.putBoolean("leased", entry.lease() != null); row.putString("state", entry.status().name());
        row.putString("target", targetAddress(entry, level));
        if (entry.lease() != null) {
            var node = CloudPatternStore.get(level).node(entry.lease().nodeId());
            row.putString("targetNode", node.id().toString()); row.putString("targetDimension", node.dimension().toString());
            var pos = net.minecraft.core.BlockPos.of(node.position()); row.putString("targetPos", pos.toShortString());
            var targetLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, node.dimension()));
            var chunk = targetLevel == null ? null : targetLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            boolean loaded = chunk != null && chunk.getBlockEntity(pos) instanceof com.gregtechceu.gtceu.api.machine.IMachineBlockEntity holder
                    && holder.getMetaMachine() instanceof CloudPatternBufferMachine target && node.id().equals(target.cloudNodeId());
            row.putBoolean("targetLoaded", loaded);
        }
        return row;
    }
    private static String targetAddress(CloudPatternStore.Entry entry, ServerLevel level) {
        if (entry.lease() == null) return "";
        var node = CloudPatternStore.get(level).node(entry.lease().nodeId());
        return node.dimension() + " " + net.minecraft.core.BlockPos.of(node.position()).toShortString();
    }
    public static void identify(CloudPatternStore.Entry entry, UUID actor) {
        entry.setRecipeTypes(actor, PatternQuickUploadMetadata.readRecipeTypeIds(entry.original()));
    }
}
