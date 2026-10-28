package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.CloudPatternLibraryMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;

/** Server-only WAL for explicit imports from a caller-proven loaded ME source.
 * One head/previous pair per physical facet; ambiguous extraction is escrowed and never guessed successful. */
public final class CloudNetworkImportJournal {
    private static final String STATE = "gtl_cloud_network_import";
    private record Witness(UUID operation, WeakReference<IGrid> grid, WeakReference<InternalInventory> inventory) {}
    private static final Map<MinecraftServer, Map<Path, Witness>> WITNESSES = new WeakHashMap<>();
    private CloudNetworkImportJournal() {}

    /** The caller resolves the inventory from an actual grid node and proves its handler is idle. */
    public static int transfer(ServerPlayer actor, CloudPatternLibraryMachine main, String sourceKey,
                               BlockEntity holder, InternalInventory inv, int slot, CompoundTag configuration, Runnable changed) {
        IGrid grid = authorize(actor, main);
        if (!(holder.getLevel() instanceof ServerLevel level) || level.getServer() != main.serverLevel().getServer()
                || holder.isRemoved() || slot < 0 || slot >= inv.size() || sourceKey == null || sourceKey.isBlank() || sourceKey.length() > 512)
            throw new IllegalArgumentException("Invalid loaded pattern source");
        var pos = holder.getBlockPos(); var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(pos) != holder || !level.mayInteract(actor, pos)
                || !CloudLibraryService.claimAllows(actor, level, pos)) throw new SecurityException("Pattern source denied");
        String address = level.dimension().location() + "/" + pos.asLong() + "/" + sourceKey;
        String facet = UUID.nameUUIDFromBytes(sourceKey.getBytes(StandardCharsets.UTF_8)).toString();
        CompoundTag state = sourceState(holder, facet); Path directory = directory(level, address);
        try {
            if (state.getBoolean("review")) throw new IllegalStateException("Pattern source requires review");
            CompoundTag old = readHead(directory, state);
            if (old != null && reconcileInventory(actor, main, holder, inv, changed, state, directory, old)) return 1;
            ItemStack before = inv.getStackInSlot(slot).copy(); ItemStack expected = before.copyWithCount(1);
            if (!valid(expected, level) || !unique(main, expected)) return 0;
            authorize(actor, main);
            CompoundTag intent = prepare(actor, main, holder, state, address, expected, configuration, "INVENTORY");
            intent.putInt("slot", slot); intent.put("before", before.save(new CompoundTag()));
            ItemStack after = before.copy(); after.shrink(1); intent.put("after", after.save(new CompoundTag()));
            write(directory, intent);
            witnesses(level).put(directory, new Witness(intent.getUUID("operation"), new WeakReference<>(grid), new WeakReference<>(inv)));
            if (main.serviceGrid() != grid) throw new IllegalStateException("Source ME grid changed before debit");
            if (!debitInventory(holder, inv, changed, state, directory, intent)) return 0;
            complete(main.serverLevel(), directory, intent); clearEscrow(holder, state, intent); return 1;
        } catch (RuntimeException failure) { review(holder, state, directory, failure); throw failure; }
    }

    /** Aggregated ME storage has no durable grid UUID. Unresolved work may resume only with the exact live grid witness. */
    public static int transferStorage(ServerPlayer actor, CloudPatternLibraryMachine main, AEItemKey key, long visibleCount) {
        IGrid grid = authorize(actor, main); if (visibleCount < 1 || key == null) return 0;
        ServerLevel level = main.serverLevel(); BlockEntity holder = main.getHolder().getSelf();
        CompoundTag state = sourceState(holder, "storage");
        String address = level.dimension().location() + "/" + main.getPos().asLong() + "/storage/" + main.libraryId();
        Path directory = directory(level, address);
        try {
            if (state.getBoolean("review")) throw new IllegalStateException("ME storage import requires review");
            CompoundTag old = readHead(directory, state);
            if (old != null) {
                requireSource(old, state, holder);
                String status = old.getString("status");
                boolean committed = cleanCommitted(main.serverLevel(), old, state);
                if (committed) clearEscrow(holder, state, old);
                if (!status.equals("ABORTED") && !committed) {
                    var witness = witnesses(level).get(directory);
                    if (witness == null || !witness.operation.equals(old.getUUID("operation")) || witness.grid.get() != grid
                            || !old.getString("kind").equals("STORAGE") || !old.getString("status").equals("DEBITED")
                            || state.getLong("processedGeneration") != old.getLong("generation")
                            || !old.getUUID("library").equals(main.libraryId()) || !old.getUUID("actor").equals(actor.getUUID()))
                        throw new IllegalStateException("Unresolved ME debit lacks its original live network witness");
                    complete(level, directory, old); clearEscrow(holder, state, old); return 1;
                }
            }
            ItemStack expected = key.toStack();
            if (!valid(expected, level) || !unique(main, expected)) return 0;
            CompoundTag intent = prepare(actor, main, holder, state, address, expected, new CompoundTag(), "STORAGE");
            write(directory, intent); witnesses(level).put(directory, new Witness(intent.getUUID("operation"), new WeakReference<>(grid), new WeakReference<>(null)));
            authorize(actor, main);
            if (main.serviceGrid() != grid) throw new IllegalStateException("ME grid changed before storage debit");
            long removed = grid.getStorageService().getInventory().extract(key, 1, Actionable.MODULATE, IActionSource.ofPlayer(actor));
            intent.putLong("reportedAmount", removed);
            intent.put("escrow", key.toStack((int)Math.max(0, Math.min(Integer.MAX_VALUE, removed))).save(new CompoundTag()));
            rememberEscrow(holder, state, intent);
            if (removed == 0) {
                intent.putString("status", "ABORTED"); write(directory, intent); clearEscrow(holder, state, intent); witnesses(level).remove(directory); return 0;
            }
            if (removed != 1) {
                intent.putString("status", "REVIEW"); write(directory, intent);
                throw new IllegalStateException("ME storage returned an invalid extraction amount");
            }
            markDebited(holder, state, intent); intent.putString("status", "DEBITED"); write(directory, intent);
            main.markDirty(); complete(level, directory, intent); clearEscrow(holder, state, intent); return 1;
        } catch (RuntimeException failure) { review(holder, state, directory, failure); throw failure; }
    }

    private static IGrid authorize(ServerPlayer actor, CloudPatternLibraryMachine main) {
        if (actor == null || !(main.getLevel() instanceof ServerLevel level) || actor.getServer() != level.getServer())
            throw new SecurityException("Invalid cloud import actor");
        IGrid grid = main.serviceGrid();
        if (!main.networkAvailable(grid) || !CloudLibraryService.authorized(actor, main.libraryId(), main))
            throw new SecurityException("Cloud import network or library denied");
        return grid;
    }
    private static boolean valid(ItemStack pattern, ServerLevel level) {
        if (pattern.isEmpty() || pattern.getCount() != 1) return false;
        try { return PatternDetailsHelper.decodePattern(pattern, level) != null; } catch (RuntimeException invalid) { return false; }
    }
    private static boolean unique(CloudPatternLibraryMachine main, ItemStack pattern) {
        return !CloudPatternStore.get(main.serverLevel()).library(main.libraryId()).containsOriginal(pattern);
    }
    private static CompoundTag sourceState(BlockEntity holder, String facet) {
        CompoundTag persistent = holder.getPersistentData(); CompoundTag sources = persistent.getCompound(STATE);
        if (!persistent.contains(STATE)) persistent.put(STATE, sources);
        CompoundTag state = sources.getCompound(facet);
        if (!sources.contains(facet)) {
            state.putUUID("identity", UUID.randomUUID()); state.putLong("processedGeneration", 0);
            sources.put(facet, state); holder.setChanged();
        }
        if (!state.hasUUID("identity") || state.getLong("processedGeneration") < 0) throw new IllegalStateException("Invalid source identity or generation");
        return state;
    }
    private static Path directory(ServerLevel level, String address) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_network_import")
                .resolve(UUID.nameUUIDFromBytes(address.getBytes(StandardCharsets.UTF_8)).toString());
    }
    private static Map<Path, Witness> witnesses(ServerLevel level) {
        return WITNESSES.computeIfAbsent(level.getServer(), ignored -> new HashMap<>());
    }
    private static CompoundTag prepare(ServerPlayer actor, CloudPatternLibraryMachine main, BlockEntity holder, CompoundTag state,
                                       String address, ItemStack expected, CompoundTag configuration, String kind) {
        CompoundTag tag = new CompoundTag(); tag.putInt("schema", 1); tag.putString("kind", kind);
        tag.putString("address", address); tag.putUUID("source", state.getUUID("identity"));
        tag.putString("holderType", String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(holder.getType())));
        tag.putString("dimension", holder.getLevel().dimension().location().toString()); tag.putLong("position", holder.getBlockPos().asLong());
        tag.putLong("generation", Math.addExact(state.getLong("processedGeneration"), 1)); tag.putUUID("operation", UUID.randomUUID());
        tag.putUUID("library", main.libraryId()); tag.putUUID("actor", actor.getUUID());
        tag.put("expected", expected.save(new CompoundTag())); tag.put("configuration", configuration.copy());
        tag.putString("status", "PREPARED"); return tag;
    }
    private static void requireSource(CompoundTag tag, CompoundTag state, BlockEntity holder) {
        if (tag.getInt("schema") != 1 || !tag.hasUUID("source") || !tag.getUUID("source").equals(state.getUUID("identity"))
                || !tag.hasUUID("operation") || !tag.hasUUID("library") || !tag.hasUUID("actor") || tag.getLong("generation") < 1
                || !tag.getString("holderType").equals(String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(holder.getType())))
                || !tag.getString("dimension").equals(holder.getLevel().dimension().location().toString())
                || tag.getLong("position") != holder.getBlockPos().asLong()) throw new IllegalStateException("Network import source identity mismatch");
        if (tag.getString("status").equals("REVIEW")) throw new IllegalStateException("Network import evidence requires review");
    }
    private static boolean cleanCommitted(ServerLevel level, CompoundTag intent, CompoundTag state) {
        if (!intent.getString("status").equals("COMMITTED") || state.getLong("processedGeneration") != intent.getLong("generation")
                || !state.hasUUID("lastOperation") || !state.getUUID("lastOperation").equals(intent.getUUID("operation"))) return false;
        if (state.hasUUID("escrowOperation") && !state.getUUID("escrowOperation").equals(intent.getUUID("operation")))
            throw new IllegalStateException("Committed head differs from retained source escrow operation");
        var receipt = CloudPatternStore.get(level).library(intent.getUUID("library")).receipt(intent.getUUID("operation"));
        if (receipt == null || !receipt.kind().equals("UPLOAD") || !receipt.status().equals("ACKNOWLEDGED"))
            throw new IllegalStateException("Committed network import lacks acknowledged ownership");
        return true;
    }
    private static boolean reconcileInventory(ServerPlayer actor, CloudPatternLibraryMachine main, BlockEntity holder, InternalInventory inv,
                                               Runnable changed, CompoundTag state, Path directory, CompoundTag intent) {
        requireSource(intent, state, holder);
        if (intent.getString("status").equals("ABORTED")) return false;
        if (cleanCommitted(main.serverLevel(), intent, state)) { clearEscrow(holder, state, intent); return false; }
        var witness = witnesses(main.serverLevel()).get(directory);
        if (witness == null || !witness.operation.equals(intent.getUUID("operation"))
                || witness.grid.get() != main.serviceGrid() || witness.inventory.get() != inv)
            throw new IllegalStateException("Pending part import lacks original inventory and live grid witness");
        if (!intent.getString("kind").equals("INVENTORY") || !intent.getUUID("library").equals(main.libraryId())
                || !intent.getUUID("actor").equals(actor.getUUID())) throw new IllegalStateException("Pending network import belongs to another source or actor");
        int slot = intent.getInt("slot"); long generation = intent.getLong("generation"), processed = state.getLong("processedGeneration");
        if (slot < 0 || slot >= inv.size() || processed < generation - 1 || processed > generation)
            throw new IllegalStateException("Network import source generation gap");
        if (processed == generation - 1) {
            if (!debitInventory(holder, inv, changed, state, directory, intent)) return false;
        } else {
            if (!state.hasUUID("lastOperation") || !state.getUUID("lastOperation").equals(intent.getUUID("operation"))
                    || !ItemStack.matches(inv.getStackInSlot(slot), ItemStack.of(intent.getCompound("after"))))
                throw new IllegalStateException("Network import post-debit evidence differs");
            intent.put("escrow", intent.getCompound("expected").copy()); intent.putString("status", "DEBITED"); write(directory, intent);
        }
        complete(main.serverLevel(), directory, intent); clearEscrow(holder, state, intent); return true;
    }
    private static boolean debitInventory(BlockEntity holder, InternalInventory inv, Runnable changed, CompoundTag state, Path directory, CompoundTag intent) {
        int slot = intent.getInt("slot"); ItemStack before = ItemStack.of(intent.getCompound("before"));
        ItemStack expected = ItemStack.of(intent.getCompound("expected"));
        if (!ItemStack.matches(inv.getStackInSlot(slot), before) || expected.getCount() != 1
                || !ItemStack.isSameItemSameTags(before, expected) || state.getLong("processedGeneration") != intent.getLong("generation") - 1)
            throw new IllegalStateException("Network import pre-debit evidence differs");
        ItemStack actual = inv.extractItem(slot, 1, false);
        intent.put("escrow", actual.save(new CompoundTag()));
        rememberEscrow(holder, state, intent);
        if (actual.isEmpty() && ItemStack.matches(inv.getStackInSlot(slot), before)) {
            intent.putString("status", "ABORTED"); write(directory, intent); clearEscrow(holder, state, intent);
            if (holder.getLevel() instanceof ServerLevel level) witnesses(level).remove(directory); return false;
        }
        if (!ItemStack.matches(actual, expected) || !ItemStack.matches(inv.getStackInSlot(slot), ItemStack.of(intent.getCompound("after")))) {
            intent.putString("status", "REVIEW"); write(directory, intent);
            throw new IllegalStateException("Network import actual debit could not be confirmed");
        }
        markDebited(holder, state, intent); intent.putString("status", "DEBITED"); write(directory, intent); changed.run(); return true;
    }
    private static void markDebited(BlockEntity holder, CompoundTag state, CompoundTag intent) {
        state.putLong("processedGeneration", intent.getLong("generation")); state.putUUID("lastOperation", intent.getUUID("operation")); holder.setChanged();
    }
    private static void rememberEscrow(BlockEntity holder, CompoundTag state, CompoundTag intent) {
        state.put("escrow", intent.getCompound("escrow").copy()); state.putUUID("escrowOperation", intent.getUUID("operation"));
        if (intent.contains("reportedAmount")) state.putLong("reportedAmount", intent.getLong("reportedAmount"));
        holder.setChanged();
    }
    private static void clearEscrow(BlockEntity holder, CompoundTag state, CompoundTag intent) {
        if (state.hasUUID("escrowOperation") && !state.getUUID("escrowOperation").equals(intent.getUUID("operation")))
            throw new IllegalStateException("Cannot clear escrow belonging to another source operation");
        state.remove("escrow"); state.remove("escrowOperation"); state.remove("reportedAmount"); holder.setChanged();
    }
    private static void complete(ServerLevel level, Path directory, CompoundTag intent) {
        ItemStack escrow = ItemStack.of(intent.getCompound("escrow"));
        if (!ItemStack.matches(escrow, ItemStack.of(intent.getCompound("expected"))) || escrow.getCount() != 1)
            throw new IllegalStateException("Network import has no exact debited escrow");
        var store = CloudPatternStore.get(level); UUID library = intent.getUUID("library"), actor = intent.getUUID("actor"), operation = intent.getUUID("operation");
        var entry = store.upload(library, actor, escrow, intent.getCompound("configuration"), operation);
        CloudLibraryService.identify(entry, actor); store.checkpoint(); store.acknowledge(library, actor, operation); store.checkpoint();
        intent.putUUID("entry", entry.id()); intent.putString("status", "COMMITTED"); intent.remove("escrow"); write(directory, intent);
        witnesses(level).remove(directory);
    }
    private static CompoundTag readHead(Path directory, CompoundTag state) {
        Path head = directory.resolve("head.dat");
        if (!Files.exists(head)) {
            if (state.getLong("processedGeneration") != 0 || Files.exists(directory.resolve("previous.dat")))
                throw new IllegalStateException("Network import journal head is missing");
            return null;
        }
        try {
            CompoundTag outer = NbtIo.readCompressed(head.toFile()), payload = outer.getCompound("payload");
            if (!outer.getString("sha256").equals(CloudPatternStore.checksum(payload))) throw new IllegalStateException("Network import journal checksum mismatch");
            return payload;
        } catch (IOException failure) { throw new IllegalStateException("Network import journal is unreadable", failure); }
    }
    private static void review(BlockEntity holder, CompoundTag state, Path directory, RuntimeException failure) {
        state.putBoolean("review", true);
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        state.putString("failure", message.substring(0, Math.min(160, message.length()))); holder.setChanged();
        // Preserve the on-disk head on read/checksum failure. Never fabricate escrow for an unknown extraction.
        try {
            CompoundTag intent = readHead(directory, state);
            if (intent != null && !intent.getString("status").equals("COMMITTED")) {
                intent.putString("status", "REVIEW"); intent.putString("failure", state.getString("failure")); write(directory, intent);
            }
        } catch (RuntimeException evidenceFailure) { failure.addSuppressed(evidenceFailure); }
    }
    private static void write(Path directory, CompoundTag intent) {
        Path head = directory.resolve("head.dat"), pending = directory.resolve("head.pending");
        try {
            Files.createDirectories(directory); var outer = new CompoundTag();
            outer.put("payload", intent.copy()); outer.putString("sha256", CloudPatternStore.checksum(intent));
            NbtIo.writeCompressed(outer, pending.toFile()); force(pending);
            if (!NbtIo.readCompressed(pending.toFile()).equals(outer)) throw new IOException("Journal verification failed");
            if (Files.exists(head)) {
                Path previousPending = directory.resolve("previous.pending"); Files.copy(head, previousPending, StandardCopyOption.REPLACE_EXISTING);
                force(previousPending); Files.move(previousPending, directory.resolve("previous.dat"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(pending, head, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) { throw new IllegalStateException("Network import journal could not be persisted", failure); }
    }
    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.force(true); }
    }
}
