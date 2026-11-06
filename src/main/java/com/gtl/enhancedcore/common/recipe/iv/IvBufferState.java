package com.gtl.enhancedcore.common.recipe.iv;

import java.util.*;
import net.minecraft.nbt.*;

/** A durable latch: unloading the controller never exposes isolated stock to the ordinary system. */
public final class IvBufferState {
    public UUID identity = UUID.randomUUID();
    public String owner = "";
    public final List<IvJob> jobs = new ArrayList<>();
    /** Runtime index for cancellation refunds; ordinary active queues never scan for returnable stock. */
    final Set<IvJob> cancelledJobs = Collections.newSetFromMap(new IdentityHashMap<>());
    public String message = "";
    public boolean refreshNeeded;
    public boolean accepting = true;
    /** Runtime-only cursor so a full/filtered ME cannot starve other recoverable stock. */
    int returnCursor;
    private CompoundTag unreadable;
    public boolean dedicated() { return !owner.isEmpty() || unreadable != null; }
    public boolean healthy() { return unreadable == null; }
    public boolean occupied(int slot) { return jobs.stream().anyMatch(job -> job.slot == slot); }
    /** Caller already owns this job through the queue or the decoded snapshot. */
    void trackCancelled(IvJob job) {
        if (job.cancelled) cancelledJobs.add(job);
    }
    boolean removeJob(IvJob job) {
        cancelledJobs.remove(job);
        return jobs.remove(job);
    }
    boolean removeJobsIf(java.util.function.Predicate<IvJob> retired) {
        boolean changed = false;
        var iterator = jobs.iterator();
        while (iterator.hasNext()) {
            IvJob job = iterator.next();
            if (retired.test(job)) {
                cancelledJobs.remove(job);
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }
    /** Only called by the player's explicit Shift-break operation. */
    public void discardTasks() {
        jobs.clear();cancelledJobs.clear();unreadable=null;accepting=false;message="";
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
        unreadable = tag.copy(); jobs.clear(); cancelledJobs.clear();
        try { readChecked(tag); }
        catch (RuntimeException failure) {
            message = "gtl_enhancedcore.diagnostic.iv_unreadable";
            IvTaskLog.error(null, null, "LOAD_BUFFER", failure);
        }
    }
    private void readChecked(CompoundTag tag) {
        if (tag.isEmpty()) {
            identity = UUID.randomUUID(); owner = ""; accepting = true; unreadable = null;
            return;
        }
        if (!tag.contains("version", Tag.TAG_INT) || !tag.contains("identity", Tag.TAG_INT_ARRAY)
                || !tag.hasUUID("identity") || !tag.contains("owner", Tag.TAG_STRING)
                || !tag.contains("jobs", Tag.TAG_LIST))
            throw new IllegalArgumentException("Incomplete isolated task ledger");
        if (tag.contains("accepting") && !tag.contains("accepting", Tag.TAG_BYTE))
            throw new IllegalArgumentException("Invalid isolated task ledger fields");
        if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unknown task schema");
        UUID restoredId = tag.getUUID("identity"); String restoredOwner = tag.getString("owner");
        List<IvJob> restored = new ArrayList<>(); Set<UUID> ids = new HashSet<>();
        ListTag list = (ListTag) tag.get("jobs");
        if (list.size() > 0 && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid isolated task list");
        for (int i = 0; i < list.size(); i++) {
            IvJob job = IvJob.load(list.getCompound(i));
            if (!ids.add(job.id)) throw new IllegalArgumentException("Duplicate task identity");
            restored.add(job);
        }
        if (!restored.isEmpty() && restoredOwner.isEmpty()) throw new IllegalArgumentException("Unowned tasks");
        identity = restoredId; owner = restoredOwner; jobs.addAll(restored);
        for (IvJob job : restored) trackCancelled(job);
        accepting=!tag.contains("accepting") || tag.getBoolean("accepting"); unreadable = null;
    }
}
