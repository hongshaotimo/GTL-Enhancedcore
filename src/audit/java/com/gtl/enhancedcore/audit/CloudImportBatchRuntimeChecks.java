package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.cloud.store.CloudLedger;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import com.gtl.enhancedcore.common.cloud.transfer.CloudBufferImport;
import com.gtl.enhancedcore.common.cloud.transfer.CloudImportJournal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

/** Real WAL/storage fault audit; caller must already enforce the marked isolated-world boundary. */
public final class CloudImportBatchRuntimeChecks {
    private CloudImportBatchRuntimeChecks() {}
    public static void run(ServerLevel level, List<String> checks) throws Exception {
        Path world = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path auditRoot = Path.of("C:/IDEA/GTL-Enhancedcore/_work/cloud-pattern-ae-rework-20261023").toAbsolutePath().normalize();
        if (!world.startsWith(auditRoot) || world.equals(auditRoot) || !Files.isRegularFile(world.getParent().resolve("cloud-store.marker")))
            throw new IllegalStateException("Batch fault audit requires a marked task-owned isolated world");
        UUID owner = UUID.randomUUID(); var store = CloudPatternStore.get(level); UUID libraryId = store.defaultLibrary(owner);
        var library = store.library(libraryId); var sink = CloudImportJournal.sink(level, libraryId, owner);
        long position = new net.minecraft.core.BlockPos(170, 64, 170).asLong();
        String dimension = level.dimension().location().toString();
        var source = new CloudBufferImport.Source(dimension, position, UUID.randomUUID());
        ItemStack expected = new ItemStack(Items.PAPER); CompoundTag config = new CompoundTag(); config.putInt("circuit", 12);
        sink.beginBatch(); UUID first = sink.prepare(source, 0, expected.copy(), config), second = sink.prepare(source, 1, expected.copy(), config);
        sink.commit(first, expected.copy()); sink.commit(second, expected.copy());
        require(library.entryCount() == 2 && library.entry(library.receipt(first).subject()).status() == CloudLedger.Status.EXTERNAL_PENDING
                && library.entry(library.receipt(second).subject()).status() == CloudLedger.Status.EXTERNAL_PENDING,
                "Real batch originals stay unavailable before the batch durability boundary", checks);
        Path directory = world.resolve("data/gtl_cloud_import").resolve(UUID.nameUUIDFromBytes(dimension.getBytes(StandardCharsets.UTF_8)) + "_" + position);
        Path obstruction = directory.resolve(second + ".dat.pending"); Files.createDirectory(obstruction);
        boolean failed = false;
        try { sink.endBatch(); } catch (IllegalStateException expectedFailure) { failed = true; }
        finally { Files.delete(obstruction); }
        require(failed, "Injected second COMMITTED WAL write failure interrupts real batch publication", checks);
        require(NbtIo.readCompressed(directory.resolve(first + ".dat").toFile()).getString("status").equals("COMMITTED")
                        && NbtIo.readCompressed(directory.resolve(second + ".dat").toFile()).getString("status").equals("DEBITED"),
                "Partial WAL fault preserves distinct committed and debited recovery evidence", checks);
        require(library.receipt(first).status().equals("EXTERNAL_PENDING") && library.receipt(second).status().equals("EXTERNAL_PENDING"),
                "No original is acknowledged when any batch COMMITTED WAL write fails", checks);
        var retry = CloudImportJournal.sink(level, libraryId, owner); retry.commit(first, expected.copy()); retry.commit(second, expected.copy());
        require(library.entryCount() == 2 && library.receipt(first).status().equals("ACKNOWLEDGED") && library.receipt(second).status().equals("ACKNOWLEDGED"),
                "Real operation replay after partial batch failure acknowledges exactly two originals without duplication", checks);
    }
    private static void require(boolean condition, String message, List<String> checks) {
        if (!condition) throw new AssertionError(message); checks.add(message);
    }
}
