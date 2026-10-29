package com.gtl.enhancedcore.common.cloud.transfer;

import appeng.parts.encoding.PatternEncodingLogic;
import appeng.api.inventories.InternalInventory;
import com.gtl.enhancedcore.common.cloud.CloudLibraryService;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/** Durable intent plus a debit marker saved with AE's real encoded inventory. */
public final class CloudTerminalUpload {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CloudTerminalUpload.class);
    private static final java.util.Map<UUID, java.lang.ref.WeakReference<PatternEncodingLogic>> REVIEWS = new java.util.HashMap<>();
    private CloudTerminalUpload() {}
    public static boolean quarantined(PatternEncodingLogic logic) { return state(logic).getBoolean("quarantined"); }
    public static boolean processingOriginal(ItemStack stack, net.minecraft.world.level.Level level) {
        if (stack.getCount() != 1) return false;
        try {
            var details = appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, level);
            return details != null && !(details instanceof appeng.crafting.pattern.AECraftingPattern);
        } catch (RuntimeException invalid) { return false; }
    }
    private static CompoundTag state(PatternEncodingLogic logic) { return ((CloudTerminalSource)logic).cloud$sourceState(); }
    private static Path directory(ServerLevel level, UUID identity) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/gtl_cloud_terminal").resolve(identity.toString());
    }
    private static boolean write(ServerLevel level, CompoundTag tag) {
        Path target = directory(level, tag.getUUID("source")).resolve("head.dat");
        Path pending = target.resolveSibling(target.getFileName() + ".pending");
        boolean hadPrevious = Files.exists(target);
        try {
            Files.createDirectories(target.getParent()); NbtIo.writeCompressed(tag, pending.toFile());
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) { channel.force(true); }
            if (hadPrevious) {
                Path previous = target.resolveSibling("previous.dat"), previousPending = target.resolveSibling("previous.pending");
                Files.copy(target, previousPending, StandardCopyOption.REPLACE_EXISTING);
                try (FileChannel channel = FileChannel.open(previousPending, StandardOpenOption.WRITE)) { channel.force(true); }
                Files.move(previousPending, previous, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return hadPrevious;
        } catch (IOException failure) { throw new IllegalStateException("Terminal upload intent could not be saved", failure); }
    }
    private static void restorePrepared(ServerLevel level, CompoundTag intent, boolean hadPrevious, long processed) {
        Path directory = directory(level, intent.getUUID("source"));
        Path head = directory.resolve("head.dat");
        try {
            if (!hadPrevious) {
                Files.deleteIfExists(head);
                if (processed == 0) Files.deleteIfExists(directory.resolve("previous.dat"));
                return;
            }
            Path pending = directory.resolve("head.rollback");
            Files.copy(directory.resolve("previous.dat"), pending, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel channel = FileChannel.open(pending, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(pending, head, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) { throw new IllegalStateException("Terminal upload intent rollback failed", failure); }
    }
    private static void restoreState(CompoundTag target, CompoundTag saved) {
        for (String key : new java.util.ArrayList<>(target.getAllKeys())) target.remove(key);
        target.merge(saved.copy());
    }
    private static final class TerminalSourceChangedException extends IllegalStateException {
        private TerminalSourceChangedException() { super("Terminal source differs from intent"); }
    }
    public static final class RetryableSourceChangeException extends IllegalStateException {
        private RetryableSourceChangeException(Throwable cause) { super("Terminal source changed before debit", cause); }
    }
    public static void upload(ServerPlayer player, UUID library, PatternEncodingLogic logic) {
        reconcile(player.serverLevel(), logic);
        if (quarantined(logic)) throw new IllegalStateException("Terminal source requires review");
        InternalInventory inventory = logic.getEncodedPatternInv(); ItemStack expected = inventory.getStackInSlot(0).copy();
        if (!processingOriginal(expected, player.level())) throw new IllegalArgumentException("Processing pattern required");
        var target = CloudPatternStore.get(player.serverLevel()).library(library);
        if (!target.healthy() || !target.authorized(player.getUUID()) || !CloudLibraryService.sameTeam(target.owner(), player.getUUID()))
            throw new SecurityException("Library denied");
        target.requireUniqueOriginal(expected);
        CompoundTag state = state(logic);
        if (!state.hasUUID("identity")) state.putUUID("identity", UUID.randomUUID());
        UUID operation = UUID.randomUUID(); CompoundTag intent = new CompoundTag();
        intent.putInt("schema", 2); intent.putLong("generation", Math.addExact(state.getLong("processedGeneration"), 1));
        intent.putUUID("source", state.getUUID("identity")); intent.putUUID("operation", operation);
        intent.putUUID("library", library); intent.putUUID("actor", player.getUUID()); intent.put("expected", expected.save(new CompoundTag()));
        boolean hadPrevious = write(player.serverLevel(), intent);
        CompoundTag previousState = state.copy();
        state.putUUID("library", library); state.putUUID("lastOperation", operation);
        try { complete(player.serverLevel(), logic, inventory, intent); }
        catch (TerminalSourceChangedException mismatch) {
            try {
                restorePrepared(player.serverLevel(), intent, hadPrevious, previousState.getLong("processedGeneration"));
                restoreState(state, previousState);
            } catch (RuntimeException rollbackFailure) {
                mismatch.addSuppressed(rollbackFailure); quarantine(logic, mismatch); throw mismatch;
            }
            throw new RetryableSourceChangeException(mismatch);
        }
        catch (RuntimeException failure) { quarantine(logic, failure); throw failure; }
    }
    private static void complete(ServerLevel level, PatternEncodingLogic logic, InternalInventory inventory, CompoundTag intent) {
        CompoundTag state = state(logic); UUID operation = intent.getUUID("operation");
        ItemStack expected = ItemStack.of(intent.getCompound("expected"));
        long generation = intent.getLong("generation"), processed = state.getLong("processedGeneration");
        if (generation < 1 || processed < 0 || processed > generation || processed < generation - 1)
            throw new IllegalStateException("generation_gap");
        var store = CloudPatternStore.get(level); UUID library = intent.getUUID("library"), actor = intent.getUUID("actor");
        var target = store.library(library); var receipt = target.receipt(operation);
        CloudPatternStore.Entry entry;
        if (receipt != null) {
            if (!receipt.kind().equals("UPLOAD") || !(receipt.status().equals("EXTERNAL_PENDING") || receipt.status().equals("ACKNOWLEDGED")))
                throw new IllegalStateException("upload_requires_review");
            entry = target.entry(receipt.subject());
            if (!ItemStack.isSameItemSameTags(expected, entry.original()) || !entry.configuration().isEmpty())
                throw new IllegalStateException("upload_receipt_payload_mismatch");
            // The receipt proves the original already entered the library; only debit an exact stale source snapshot.
            if (processed == generation - 1) {
                if (ItemStack.matches(inventory.getStackInSlot(0), expected)) {
                    ItemStack actual = inventory.extractItem(0, 1, false);
                    if (!ItemStack.matches(actual, expected)) throw new IllegalStateException("Terminal debit could not be confirmed");
                }
                state.putLong("processedGeneration", generation); logic.saveChanges();
            }
        } else {
            if (processed == generation - 1) {
                if (!ItemStack.matches(inventory.getStackInSlot(0), expected)) throw new TerminalSourceChangedException();
                ItemStack actual = inventory.extractItem(0, 1, false);
                if (!ItemStack.matches(actual, expected)) throw new IllegalStateException("Terminal debit could not be confirmed");
                state.putLong("processedGeneration", generation);
                logic.saveChanges();
            }
            entry = store.upload(library, actor, expected.copy(), new CompoundTag(), operation);
        }
        CloudLibraryService.identify(entry, actor); store.checkpointLibrary(library);
        store.acknowledge(library, actor, operation); store.checkpointLibrary(library);
    }
    public static String diagnostic(PatternEncodingLogic logic) {
        var tag = state(logic);
        if (!quarantined(logic)) return "";
        return (tag.hasUUID("identity") ? tag.getUUID("identity") : "unknown") + "/" + tag.getLong("processedGeneration")
                + "/" + (tag.hasUUID("lastOperation") ? tag.getUUID("lastOperation") : "unknown") + "/" + tag.getString("failure");
    }
    private static void quarantine(PatternEncodingLogic logic, Exception failure) {
        var state = state(logic); state.putBoolean("quarantined", true);
        String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        state.putString("failure", reason.substring(0, Math.min(160, reason.length())));
        // Preserve an ambiguous source as serialized escrow, inaccessible to normal AE extraction.
        var inventory = logic.getEncodedPatternInv();
        if (!inventory.getStackInSlot(0).isEmpty() && !state.contains("escrow")) {
            state.put("escrow", inventory.getStackInSlot(0).save(new CompoundTag())); inventory.setItemDirect(0, ItemStack.EMPTY);
        }
        logic.saveChanges();
        if (state.hasUUID("identity")) REVIEWS.put(state.getUUID("identity"), new java.lang.ref.WeakReference<>(logic));
        LOG.warn("Cloud terminal source quarantined; preserve head.dat, previous.dat and source NBT: {}", diagnostic(logic));
    }
    public static ListTag reviewDiagnostics(UUID library) {
        ListTag result = new ListTag(); REVIEWS.values().removeIf(ref -> ref.get() == null);
        for (var ref : REVIEWS.values()) {
            var logic = ref.get(); if (logic == null || !library.equals(state(logic).hasUUID("library") ? state(logic).getUUID("library") : null)) continue;
            result.add(StringTag.valueOf(diagnostic(logic))); if (result.size() >= 16) break;
        }
        return result;
    }
    public static void reconcile(ServerLevel level, PatternEncodingLogic logic) {
        ((CloudTerminalSource)logic).cloud$markReconciled();
        CompoundTag state = state(logic);
        if (!state.hasUUID("identity")) return;
        if (quarantined(logic) && !recoverUncommittedSourceMismatch(level, logic, state)
                && !recoverInterruptedCommit(level, logic, state)) {
            REVIEWS.put(state.getUUID("identity"), new java.lang.ref.WeakReference<>(logic)); return;
        }
        if (quarantined(logic)) return;
        Path directory = directory(level, state.getUUID("identity"));
        Path head = directory.resolve("head.dat");
        try {
            if (!Files.exists(head)) {
                if (state.getLong("processedGeneration") != 0 || Files.exists(directory.resolve("previous.dat")))
                    throw new IllegalStateException("intent_missing");
                return;
            }
            CompoundTag intent = NbtIo.readCompressed(head.toFile());
            if (intent.getInt("schema") != 2 || !intent.getUUID("source").equals(state.getUUID("identity")))
                throw new IllegalStateException("source_identity_mismatch");
            state.putUUID("library", intent.getUUID("library")); state.putUUID("lastOperation", intent.getUUID("operation"));
            complete(level, logic, logic.getEncodedPatternInv(), intent);
        } catch (IOException | RuntimeException failure) { quarantine(logic, failure); }
    }
    /** A durable debit marker owns exactly one intent. Retry its commit, never debit the current slot again. */
    private static boolean recoverInterruptedCommit(ServerLevel level, PatternEncodingLogic logic, CompoundTag state) {
        String failure = state.getString("failure");
        if (!failure.equals("Cloud snapshot write failed") && !failure.startsWith("Cloud snapshot write failed:")
                && !failure.equals("Cloud snapshot durability requires review")) return false;
        try {
            CompoundTag intent = NbtIo.readCompressed(directory(level, state.getUUID("identity")).resolve("head.dat").toFile());
            if (intent.getInt("schema") != 2 || !intent.getUUID("source").equals(state.getUUID("identity"))
                    || intent.getLong("generation") < 1 || intent.getLong("generation") != state.getLong("processedGeneration")
                    || !state.hasUUID("lastOperation") || !intent.getUUID("operation").equals(state.getUUID("lastOperation"))
                    || !state.hasUUID("library") || !intent.getUUID("library").equals(state.getUUID("library"))) return false;
            var inventory = logic.getEncodedPatternInv();
            ItemStack escrow = state.contains("escrow", Tag.TAG_COMPOUND) ? ItemStack.of(state.getCompound("escrow")) : ItemStack.EMPTY;
            if (state.contains("escrow") && (escrow.isEmpty() || !inventory.getStackInSlot(0).isEmpty())) return false;
            // complete validates an existing receipt's kind, payload and configuration;
            // a missing receipt is committed from the already-debited durable intent.
            CloudPatternStore.get(level).retryFailedWrites();
            complete(level, logic, inventory, intent);
            if (!escrow.isEmpty()) inventory.setItemDirect(0, escrow);
            state.remove("quarantined"); state.remove("failure"); state.remove("escrow");
            logic.saveChanges(); REVIEWS.remove(state.getUUID("identity"));
            LOG.info("Recovered interrupted cloud terminal commit: source={}, generation={}",
                    state.getUUID("identity"), state.getLong("processedGeneration"));
            return true;
        } catch (IOException | RuntimeException unavailable) {
            LOG.warn("Cloud terminal commit is still unavailable: {}", diagnostic(logic), unavailable);
            return false;
        }
    }
    // A source mismatch is retryable only when the debit marker and receipts prove it never committed.
    private static boolean recoverUncommittedSourceMismatch(ServerLevel level, PatternEncodingLogic logic, CompoundTag state) {
        if (!state.getString("failure").equals("Terminal source differs from intent")) return false;
        try {
            Path sourceDirectory = directory(level, state.getUUID("identity"));
            Path head = sourceDirectory.resolve("head.dat"), previous = sourceDirectory.resolve("previous.dat");
            long processed = state.getLong("processedGeneration");
            if (processed < 0 || processed == Long.MAX_VALUE) return false;
            CompoundTag committed = null;
            if (Files.exists(head)) {
                CompoundTag intent = NbtIo.readCompressed(head.toFile());
                if (intent.getInt("schema") != 2 || !intent.getUUID("source").equals(state.getUUID("identity"))) return false;
                long generation = intent.getLong("generation");
                if (generation == processed + 1) {
                    var target = CloudPatternStore.get(level).library(intent.getUUID("library"));
                    var receipt = target.receipt(intent.getUUID("operation"));
                    if (receipt != null) {
                        if (!receipt.kind().equals("UPLOAD") || !(receipt.status().equals("EXTERNAL_PENDING") || receipt.status().equals("ACKNOWLEDGED"))) return false;
                        ItemStack expected = ItemStack.of(intent.getCompound("expected"));
                        var entry = target.entry(receipt.subject());
                        if (!ItemStack.isSameItemSameTags(expected, entry.original()) || !entry.configuration().isEmpty()) return false;
                        committed = intent;
                    } else {
                        if (processed == 0) {
                            if (Files.exists(previous)) return false;
                        } else {
                            if (!Files.exists(previous)) return false;
                            committed = NbtIo.readCompressed(previous.toFile());
                            if (committed.getInt("schema") != 2 || !committed.getUUID("source").equals(state.getUUID("identity"))
                                    || committed.getLong("generation") != processed || !acknowledgedUpload(level, committed)) return false;
                        }
                        restorePrepared(level, intent, processed > 0, processed);
                    }
                } else if (generation == processed && acknowledgedUpload(level, intent)) {
                    var target = CloudPatternStore.get(level).library(intent.getUUID("library"));
                    var receipt = target.receipt(intent.getUUID("operation"));
                    var entry = target.entry(receipt.subject());
                    if (!ItemStack.isSameItemSameTags(ItemStack.of(intent.getCompound("expected")), entry.original())
                            || !entry.configuration().isEmpty()) return false;
                    committed = intent;
                } else return false;
            } else if (processed != 0 || Files.exists(previous)) {
                return false;
            } else if (state.hasUUID("library") && state.hasUUID("lastOperation")
                    && CloudPatternStore.get(level).library(state.getUUID("library")).receipt(state.getUUID("lastOperation")) != null) {
                return false;
            }

            InternalInventory inventory = logic.getEncodedPatternInv();
            if (state.contains("escrow", Tag.TAG_COMPOUND)) {
                ItemStack escrow = ItemStack.of(state.getCompound("escrow"));
                if (escrow.isEmpty() || !inventory.getStackInSlot(0).isEmpty()) return false;
                inventory.setItemDirect(0, escrow);
            }
            if (committed != null && committed.getLong("generation") == processed + 1)
                state.putLong("processedGeneration", committed.getLong("generation"));
            state.remove("quarantined"); state.remove("failure"); state.remove("escrow");
            if (committed == null) {
                state.remove("library"); state.remove("lastOperation");
            } else {
                state.putUUID("library", committed.getUUID("library"));
                state.putUUID("lastOperation", committed.getUUID("operation"));
            }
            logic.saveChanges();
            REVIEWS.remove(state.getUUID("identity"));
            return true;
        } catch (IOException | RuntimeException failure) {
            LOG.warn("Could not recover an uncommitted terminal source mismatch: {}", diagnostic(logic), failure);
            return false;
        }
    }
    private static boolean acknowledgedUpload(ServerLevel level, CompoundTag intent) {
        var receipt = CloudPatternStore.get(level).library(intent.getUUID("library")).receipt(intent.getUUID("operation"));
        return receipt != null && receipt.kind().equals("UPLOAD") && receipt.status().equals("ACKNOWLEDGED");
    }
}
