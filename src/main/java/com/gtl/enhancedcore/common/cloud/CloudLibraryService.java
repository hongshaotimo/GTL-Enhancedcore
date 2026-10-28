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
        final LinkedHashMap<UUID, CatalogRow> rows = new LinkedHashMap<>();
        final Set<UUID> dirty = new LinkedHashSet<>();
        final Set<String> types = new TreeSet<>();
        List<UUID> visible = List.of();
        long[] searchItems = new long[0], searchFluids = new long[0];
        final Map<UUID, String> targets = new HashMap<>();
        final LinkedHashMap<Query, List<UUID>> searches = new LinkedHashMap<>(32, .75f, true);
        final LinkedHashMap<TerminalQuery,List<UUID>> terminalSearches = new LinkedHashMap<>(32,.75f,true);
    }
    // A menu never owns an index. World-library identity keeps results stable across opens.
    private static final Map<CloudPatternStore.Library, Catalog> CATALOGS = new WeakHashMap<>();

    /** Called by authority mutations, before allocation notification. No inventory or decode work here. */
    public static void catalogChanged(CloudPatternStore.Library library, UUID entryId) {
        Catalog catalog = CATALOGS.get(library);
        if (catalog == null) return;
        if (entryId == null) catalog.rebuild = true; else catalog.dirty.add(entryId);
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
        var key=new TerminalQuery(input,output,inputItems,inputFluids,outputItems,outputFluids);
        var cached=catalog.terminalSearches.get(key);if(cached!=null)return cached;
        var matches=new ArrayList<UUID>();
        for(var row:catalog.rows.values()) if(row.terminalVisible && row.input.matches(input,inputItems,inputFluids)
                && row.output.matches(output,outputItems,outputFluids)) matches.add(row.id);
        var result=List.copyOf(matches);catalog.terminalSearches.put(key,result);
        while(catalog.terminalSearches.size()>32)catalog.terminalSearches.remove(catalog.terminalSearches.keySet().iterator().next());
        return result;
    }
    private static Catalog catalog(CloudPatternStore.Library library, ServerLevel level) {
        Catalog catalog = CATALOGS.computeIfAbsent(library, ignored -> new Catalog());
        if (catalog.revision != library.catalogRevision()) {
            if (catalog.rebuild || catalog.dirty.isEmpty()) {
                catalog.rows.clear();
                for (int offset = 0; offset < library.entryCount(); offset += 4096)
                    for (UUID id : library.entryIdPage(offset, 4096)) catalog.rows.put(id, catalogRow(library.entry(id), level));
            } else {
                for (UUID id : catalog.dirty) catalog.rows.put(id, catalogRow(library.entry(id), level));
            }
            catalog.types.clear();
            var visible = new ArrayList<UUID>();
            var itemScope = new BitSet(); var fluidScope = new BitSet();
            for (CatalogRow row : catalog.rows.values()) {
                if (!row.exported) {
                    visible.add(row.id); catalog.types.addAll(row.types);
                    for (int id : row.items) if (id >= 0) itemScope.set(id);
                    for (int id : row.fluids) if (id >= 0) fluidScope.set(id);
                }
            }
            catalog.visible = List.copyOf(visible); catalog.searches.clear(); catalog.terminalSearches.clear();
            catalog.targets.clear(); catalog.searchItems = itemScope.toLongArray(); catalog.searchFluids = fluidScope.toLongArray();
            catalog.rebuild = false; catalog.dirty.clear(); catalog.revision = library.catalogRevision();
        }
        return catalog;
    }
    public static CompoundTag page(ServerLevel level, UUID libraryId, String search, String type, int requestedPage, String selected) {
        return page(level, libraryId, search, type, requestedPage, selected, CloudPatternSearch.EMPTY);
    }
    /** Results are complete. Filtering never publishes a partial count or advances on menu polling. */
    public static CompoundTag page(ServerLevel level, UUID libraryId, String search, String type, int requestedPage, String selected, CloudPatternSearch localized) {
        var library = CloudPatternStore.get(level).library(libraryId);
        Catalog catalog = catalog(library, level);
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
    public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent event) { CATALOGS.clear(); }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void reloaded(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        // Entry display metadata is a durable shard cache; only this derived in-memory index needs rebuilding.
        CATALOGS.clear();
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
