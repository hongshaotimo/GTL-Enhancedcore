package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Actual NBT envelope/validation checks, independent of a player world. */
public final class CloudStoreNbtRegression {
    private static int assertions;
    private static final class Partition extends CloudPatternStore.CheckedData {
        private CompoundTag value = new CompoundTag();
        void load(CompoundTag tag) { readChecked(tag); }
        @Override protected CompoundTag writeData() { return value.copy(); }
        @Override protected void readData(CompoundTag tag) {
            if (tag.getLong("quantity") < 0) throw new IllegalArgumentException("Negative quantity");
            value = tag.copy();
        }
    }
    public static void main(String[] args) { System.out.println("Cloud NBT assertions: " + run()); }
    static int run() {
        assertions = 0;
        CompoundTag left = new CompoundTag(), right = new CompoundTag(), nested = new CompoundTag();
        nested.putString("fluidTag", "test"); left.put("nested", nested); left.putLong("quantity", Long.MAX_VALUE);
        right.putLong("quantity", Long.MAX_VALUE); right.put("nested", nested.copy());
        check(CloudPatternStore.checksum(left).equals(CloudPatternStore.checksum(right)), "Hash independent of compound insertion order");
        right.putLong("quantity", Long.MAX_VALUE - 1);
        check(!CloudPatternStore.checksum(left).equals(CloudPatternStore.checksum(right)), "Changing long quantity changes checksum");
        Partition original = new Partition(); original.value = left.copy(); CompoundTag serialized = original.save(new CompoundTag());
        Partition restored = new Partition(); restored.load(serialized);
        check(restored.healthy() && restored.value.equals(left), "Validated NBT envelope round-trips full payload");
        CompoundTag damaged = serialized.copy(); damaged.getCompound("data").putLong("quantity", 0); damaged.putString("unknownFutureField", "preserve");
        Partition quarantine = new Partition(); quarantine.load(damaged);
        check(!quarantine.healthy(), "Checksum mismatch quarantines whole partition");
        check(quarantine.save(new CompoundTag()).equals(damaged), "Unknown fields and damaged original survive save without replacement");
        CompoundTag unknownFormat = serialized.copy(); unknownFormat.putInt("formatVersion", 99);
        Partition future = new Partition(); future.load(unknownFormat);
        check(!future.healthy() && future.save(new CompoundTag()).equals(unknownFormat), "Unknown schema is preserved, never interpreted as an empty library");
        CompoundTag invalid = new CompoundTag(); invalid.putLong("quantity", -1);
        CompoundTag envelope = new CompoundTag(); envelope.putInt("formatVersion", 1); envelope.put("data", invalid);
        envelope.putString("sha256", CloudPatternStore.checksum(invalid)); Partition invalidState = new Partition(); invalidState.load(envelope);
        check(!invalidState.healthy(), "Semantically invalid state is quarantined even with valid checksum");
        CompoundTag copy = quarantine.quarantinedData(); copy.putString("unknownFutureField", "changed");
        check(quarantine.quarantinedData().equals(damaged), "Diagnostic access cannot mutate the preserved original");
        CompoundTag listsA = new CompoundTag(), listsB = new CompoundTag(); ListTag first = new ListTag(), second = new ListTag();
        first.add(left.copy()); first.add(right.copy()); second.add(right.copy()); second.add(left.copy());
        listsA.put("rows", first); listsB.put("rows", second);
        check(!CloudPatternStore.checksum(listsA).equals(CloudPatternStore.checksum(listsB)), "List order is part of the checksum");
        diskFailure();
        unchangedSnapshots();
        return assertions;
    }
    private static void unchangedSnapshots() {
        java.nio.file.Path root = null;
        try {
            root = java.nio.file.Files.createTempDirectory("cloud-unchanged-regression-");
            var target = root.resolve("partition.dat");
            Partition partition = new Partition(); partition.value.putLong("quantity", 1); partition.setDirty();
            partition.save(target.toFile());
            var stamp = java.nio.file.attribute.FileTime.fromMillis(1234567890000L);
            java.nio.file.Files.setLastModifiedTime(target, stamp);
            for (int i = 0; i < 155; i++) { partition.setDirty(); partition.save(target.toFile()); }
            check(java.nio.file.Files.getLastModifiedTime(target).equals(stamp), "155 unchanged dirty requests never rewrite the committed file");
            check(!java.nio.file.Files.exists(root.resolve("partition.dat.previous")), "Unchanged dirty requests never copy a previous backup");
            check(partition.healthy() && !partition.isDirty(), "Unchanged requests finish clean and healthy");
            Partition reloaded = new Partition(); reloaded.load(net.minecraft.nbt.NbtIo.readCompressed(target.toFile()).getCompound("data"));
            reloaded.setDirty(); reloaded.save(target.toFile());
            check(java.nio.file.Files.getLastModifiedTime(target).equals(stamp) && !reloaded.isDirty(), "Reloaded unchanged partition reuses the durable checksum without rewriting");
            var capture = CloudPatternStore.CheckedData.class.getDeclaredMethod("captureSnapshot", java.nio.file.Path.class);
            capture.setAccessible(true);
            var persist = CloudPatternStore.CheckedData.class.getDeclaredMethod("persistAll", java.util.List.class); persist.setAccessible(true);
            partition.value.putLong("quantity", 2); partition.setDirty();
            Object older = capture.invoke(partition, target);
            partition.value.putLong("quantity", 1); partition.setDirty();
            Object newer = capture.invoke(partition, target);
            check(older != null && newer != null, "Return to committed payload must not elide a newer snapshot while writes are pending");
            persist.invoke(null, java.util.List.of(newer)); persist.invoke(null, java.util.List.of(older));
            check(net.minecraft.nbt.NbtIo.readCompressed(target.toFile()).getCompound("data").getCompound("data").getLong("quantity") == 1,
                    "Older queued write cannot overwrite a later committed payload");
            var finish = CloudPatternStore.CheckedData.class.getDeclaredMethod("finishSnapshot", newer.getClass(), Throwable.class); finish.setAccessible(true);
            finish.invoke(partition, newer, null); finish.invoke(partition, older, null);
            partition.setDirty();
            check(capture.invoke(partition, target) == null && !partition.isDirty(), "Completion ordering retains the correct deduplication payload");
            partition.value.putLong("quantity", 3); partition.setDirty(); Object pending = capture.invoke(partition, target);
            partition.value.putLong("quantity", 4); partition.setDirty();
            persist.invoke(null, java.util.List.of(pending)); finish.invoke(partition, pending, null);
            check(partition.isDirty(), "Background completion cannot clear a mutation made after capture");
            partition.save(target.toFile());
            check(net.minecraft.nbt.NbtIo.readCompressed(target.toFile()).getCompound("data").getCompound("data").getLong("quantity") == 4,
                    "Next save persists the mutation made during an earlier write");
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally {
            if (root != null) try (var files = java.nio.file.Files.walk(root)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(file);
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
    }
    private static void diskFailure() {
        net.minecraft.SharedConstants.tryDetectVersion();
        java.nio.file.Path root = null;
        try {
            root = java.nio.file.Files.createTempDirectory("cloud-store-regression-");
            var blocker = root.resolve("blocked");
            java.nio.file.Files.writeString(blocker, "preserve original file");
            Partition partition = new Partition(); partition.value.putLong("quantity", Long.MAX_VALUE); partition.setDirty();
            partition.save(blocker.resolve("partition.dat").toFile());
            check(!partition.healthy() && partition.isDirty(), "Disk failure keeps partition dirty and unavailable without escaping vanilla save");
            check(partition.diagnostic().contains("partition.dat") && partition.diagnostic().contains("Exception"), "Disk failure diagnostic includes target path and real I/O cause");
            check(java.nio.file.Files.readString(blocker).equals("preserve original file"), "Failed cloud save never replaces the obstructing original");
            check(partition.save(new CompoundTag()).getCompound("data").getLong("quantity") == Long.MAX_VALUE, "Failed save retains exact in-memory payload");
            var target = root.resolve("partition.dat"); partition.save(target.toFile());
            check(partition.healthy() && !partition.isDirty(), "Successful durable retry clears only the I/O failure");
            Partition restored = new Partition(); restored.load(net.minecraft.nbt.NbtIo.readCompressed(target.toFile()).getCompound("data"));
            check(restored.healthy() && restored.value.equals(partition.value), "Retried snapshot rereads the exact original payload");
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        finally {
            if (root != null) try (var files = java.nio.file.Files.walk(root)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(file);
            } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
