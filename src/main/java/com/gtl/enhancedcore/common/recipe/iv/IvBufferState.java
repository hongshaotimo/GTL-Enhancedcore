package com.gtl.enhancedcore.common.recipe.iv;

import java.util.*;
import net.minecraft.nbt.*;

/** A durable latch: unloading the controller never exposes isolated stock to the ordinary system. */
public final class IvBufferState {
    public UUID identity = UUID.randomUUID();
    public String owner = "";
    public final List<IvJob> jobs = new ArrayList<>();
    public String message = "";
    public boolean refreshNeeded;
    public boolean accepting = true;
    /** Runtime-only cursor so a full/filtered ME cannot starve other recoverable stock. */
    int returnCursor;
    private CompoundTag unreadable;
    public boolean dedicated() { return !owner.isEmpty() || unreadable != null; }
    public boolean healthy() { return unreadable == null; }
    public boolean occupied(int slot) { return jobs.stream().anyMatch(job -> job.slot == slot); }
    /** Only called by the player's explicit Shift-break operation. */
    public void discardTasks() {
        jobs.clear();unreadable=null;accepting=false;message="";
    }
    public CompoundTag save() {
        if (unreadable != null) return unreadable.copy();
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putUUID("identity", identity);
        tag.putString("owner", owner); tag.putBoolean("accepting",accepting); ListTag queue = new ListTag();
        for (IvJob job : jobs) queue.add(job.save());
        tag.put("jobs", queue); return tag;
    }
    public void load(CompoundTag tag) {
        // Never silently drop an entry if a mod, ingredient or recipe codec changed.
        unreadable = tag.copy(); jobs.clear();
        try {
            if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unknown task schema");
            UUID restoredId = tag.getUUID("identity"); String restoredOwner = tag.getString("owner");
            List<IvJob> restored = new ArrayList<>(); Set<UUID> ids = new HashSet<>();
            ListTag list = tag.getList("jobs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                IvJob job = IvJob.load(list.getCompound(i));
                if (!ids.add(job.id)) throw new IllegalArgumentException("Duplicate task identity");
                restored.add(job);
            }
            if (!restored.isEmpty() && restoredOwner.isEmpty()) throw new IllegalArgumentException("Unowned tasks");
            identity = restoredId; owner = restoredOwner; jobs.addAll(restored); accepting=!tag.contains("accepting") || tag.getBoolean("accepting"); unreadable = null;
        } catch (RuntimeException failure) {
            message = "gtl_enhancedcore.diagnostic.iv_unreadable";
            IvTaskLog.error(null, null, "LOAD_BUFFER", failure);
        }
    }
}
