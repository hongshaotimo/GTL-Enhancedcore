package com.gtl.enhancedcore.common.patterncut;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Server-owned cut payloads; only entries committed to the index are readable. */
public final class PatternCutStore {
    private final Path directory;

    public PatternCutStore(Path directory) {
        this.directory = Objects.requireNonNull(directory).toAbsolutePath().normalize();
    }

    public record Entry(UUID id, String name, String dimension, BlockPos source, int patterns, long created) {}

    /** Reads metadata only, without opening any cut payload. */
    public synchronized List<Entry> list() throws IOException {
        return List.copyOf(loadIndex().values());
    }

    /** Publishes a fresh cut only after its complete payload has been saved. */
    public synchronized Entry put(UUID id, CompoundTag payload) throws IOException {
        Path file = payloadPath(id);
        Map<UUID, Entry> entries = loadIndex();
        if (entries.containsKey(id) || Files.exists(file)) {
            throw new IOException("Cut already exists: " + id);
        }
        Entry entry = entryFromPayload(id, payload);
        writeAtomically(file, payload);
        entries.put(id, entry);
        // A failed index publication leaves an inaccessible file, never another readable cut.
        saveIndex(entries);
        return entry;
    }

    public synchronized CompoundTag read(UUID id) throws IOException {
        Path file = payloadPath(id);
        if (!loadIndex().containsKey(id)) {
            throw new NoSuchFileException(file.toString());
        }
        return NbtIo.readCompressed(file.toFile());
    }

    /** Claims a cut durably before its contents are delivered to a machine. */
    public synchronized CompoundTag take(UUID id) throws IOException {
        Path file = payloadPath(id);
        Map<UUID, Entry> entries = loadIndex();
        if (!entries.containsKey(id)) {
            throw new NoSuchFileException(file.toString());
        }
        CompoundTag payload = NbtIo.readCompressed(file.toFile());
        entries.remove(id);
        saveIndex(entries);
        return payload;
    }

    /** Claims a complete same-name group with one index update. */
    public synchronized void takeAll(List<UUID> ids) throws IOException {
        Map<UUID, Entry> entries = loadIndex();
        for (UUID id : ids) {
            if (!entries.containsKey(id) || !Files.isRegularFile(payloadPath(id)))
                throw new NoSuchFileException(payloadPath(id).toString());
        }
        ids.forEach(entries::remove);
        saveIndex(entries);
    }

    /** Restores one explicitly identified claim after a rejected paste, without rewriting its payload. */
    public synchronized void restore(UUID id) throws IOException {
        Path file = payloadPath(id);
        Map<UUID, Entry> entries = loadIndex();
        if (entries.containsKey(id)) return;
        CompoundTag payload = NbtIo.readCompressed(file.toFile());
        entries.put(id, entryFromPayload(id, payload));
        saveIndex(entries);
    }

    /** Cleans up a claimed payload without changing its unavailable status. */
    public synchronized void finish(UUID id) throws IOException {
        Path file = payloadPath(id);
        if (loadIndex().containsKey(id)) {
            throw new IOException("Cut has not been claimed: " + id);
        }
        Files.deleteIfExists(file);
    }

    /** Invalidates the cut before deleting its payload, even when file cleanup fails. */
    public synchronized void remove(UUID id) throws IOException {
        Path file = payloadPath(id);
        Map<UUID, Entry> entries = loadIndex();
        if (entries.remove(id) != null) {
            saveIndex(entries);
        }
        Files.deleteIfExists(file);
    }

    public synchronized boolean contains(UUID id) throws IOException {
        Path file = payloadPath(id);
        return loadIndex().containsKey(id) && Files.isRegularFile(file);
    }

    private Path payloadPath(UUID id) {
        return directory.resolve(Objects.requireNonNull(id).toString() + ".nbt");
    }

    private Entry entryFromPayload(UUID id, CompoundTag payload) throws IOException {
        if (!payload.contains("name", Tag.TAG_STRING)
                || !payload.contains("dimension", Tag.TAG_STRING)
                || !payload.contains("bufferPos", Tag.TAG_LONG)
                || !payload.contains("patterns", Tag.TAG_LIST)) {
            throw new IOException("Invalid cut payload: " + id);
        }
        ListTag patterns = (ListTag) payload.get("patterns");
        if (!patterns.isEmpty() && patterns.getElementType() != Tag.TAG_COMPOUND) {
            throw new IOException("Invalid cut patterns: " + id);
        }
        return new Entry(id, payload.getString("name"), payload.getString("dimension"),
                BlockPos.of(payload.getLong("bufferPos")), patterns.size(), System.currentTimeMillis());
    }

    private Map<UUID, Entry> loadIndex() throws IOException {
        Path index = directory.resolve("index.nbt");
        Map<UUID, Entry> entries = new LinkedHashMap<>();
        if (!Files.exists(index)) {
            return entries;
        }
        CompoundTag root = NbtIo.readCompressed(index.toFile());
        if (!root.contains("entries", Tag.TAG_LIST)) {
            throw new IOException("Invalid cut index: " + index);
        }
        ListTag list = (ListTag) root.get("entries");
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IOException("Invalid cut index entries: " + index);
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            if (!tag.hasUUID("id") || !tag.contains("name", Tag.TAG_STRING)
                    || !tag.contains("dimension", Tag.TAG_STRING) || !tag.contains("bufferPos", Tag.TAG_LONG)
                    || !tag.contains("patterns", Tag.TAG_INT) || !tag.contains("created", Tag.TAG_LONG)
                    || tag.getInt("patterns") < 0) {
                throw new IOException("Invalid cut index entry: " + i);
            }
            UUID id = tag.getUUID("id");
            Entry entry = new Entry(id, tag.getString("name"), tag.getString("dimension"),
                    BlockPos.of(tag.getLong("bufferPos")), tag.getInt("patterns"), tag.getLong("created"));
            if (entries.put(id, entry) != null) {
                throw new IOException("Duplicate cut index entry: " + id);
            }
        }
        return entries;
    }

    private void saveIndex(Map<UUID, Entry> entries) throws IOException {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", entry.id());
            tag.putString("name", entry.name());
            tag.putString("dimension", entry.dimension());
            tag.putLong("bufferPos", entry.source().asLong());
            tag.putInt("patterns", entry.patterns());
            tag.putLong("created", entry.created());
            list.add(tag);
        }
        CompoundTag root = new CompoundTag();
        root.put("entries", list);
        writeAtomically(directory.resolve("index.nbt"), root);
    }

    private void writeAtomically(Path target, CompoundTag tag) throws IOException {
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            NbtIo.writeCompressed(tag, temporary.toFile());
            // Refuse unsupported filesystems rather than expose a partially replaced index.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}
