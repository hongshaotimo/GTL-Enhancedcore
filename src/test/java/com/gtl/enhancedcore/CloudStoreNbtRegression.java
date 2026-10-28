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
        return assertions;
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
