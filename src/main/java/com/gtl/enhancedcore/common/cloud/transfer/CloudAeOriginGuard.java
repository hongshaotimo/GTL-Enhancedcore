package com.gtl.enhancedcore.common.cloud.transfer;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;

/** Fail-closed return destination. AE grids have no public persistent UUID. */
public final class CloudAeOriginGuard {
    public record Origin(UUID nodeId, String dimension, long position, long generation) {
        public Origin {
            Objects.requireNonNull(nodeId);
            if (dimension == null || dimension.isBlank() || generation < 0) throw new IllegalArgumentException("Invalid origin");
        }
    }

    private Origin origin;
    private Object acceptedGrid;
    private boolean suspended = true;
    private boolean changedGrid;

    public Origin origin() { return origin; }
    public boolean suspended() { return suspended; }

    public boolean captureOnAccept(Object grid, Origin endpoint, boolean hasJobs) {
        Objects.requireNonNull(endpoint);
        if (hasJobs) return endpoint.equals(origin) && canUse(grid, true);
        if (grid == null) return false;
        origin = endpoint;
        acceptedGrid = grid;
        suspended = false;
        changedGrid = false;
        return true;
    }

    public boolean captureOnAccept(MEPatternBufferPartMachine buffer, Origin endpoint, boolean hasJobs) {
        return captureOnAccept(grid(buffer), endpoint, hasJobs);
    }

    public boolean canUse(Object grid, boolean hasJobs) {
        if (!hasJobs) return grid != null;
        if (grid == null) { suspended = true; return false; }
        if (grid != acceptedGrid) { suspended = true; changedGrid = true; return false; }
        // Only the exact original grid may automatically recover after a transient outage.
        // A restart has acceptedGrid=null; reconstructed grids still need continuity proof.
        if (changedGrid) return false;
        suspended = false;
        return origin != null;
    }

    public boolean canUse(MEPatternBufferPartMachine buffer, boolean hasJobs) {
        return canUse(grid(buffer), hasJobs);
    }

    /** UI projection only; never grants continuity or changes the guard's state. */
    public boolean peekCanUse(MEPatternBufferPartMachine buffer, boolean hasJobs) {
        Object grid = grid(buffer);
        return grid != null && (!hasJobs || grid == acceptedGrid && !changedGrid && origin != null);
    }

    /** Caller must authenticate an explicit reconnect; a matching owner alone is insufficient. */
    public boolean authorizeReconnect(Object grid, Origin endpoint, boolean authorized) {
        if (!authorized || grid == null || origin == null || !origin.equals(endpoint)) return false;
        acceptedGrid = grid;
        suspended = false;
        changedGrid = false;
        return true;
    }

    public void clearWhenDrained(boolean hasJobs) {
        if (hasJobs) throw new IllegalStateException("Outstanding orders retain their origin");
        origin = null;
        acceptedGrid = null;
        suspended = true;
        changedGrid = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        if (origin != null) {
            tag.putUUID("nodeId", origin.nodeId());
            tag.putString("dimension", origin.dimension());
            tag.putLong("position", origin.position());
            tag.putLong("generation", origin.generation());
        }
        return tag;
    }

    /** A process restart never authorizes a network reconstructed at the same position. */
    public void load(CompoundTag tag) {
        acceptedGrid = null;
        suspended = true;
        changedGrid = true;
        origin = null;
        if (tag.getInt("schema") != 1) throw new IllegalArgumentException("Unknown AE origin schema");
        if (tag.hasUUID("nodeId")) origin = new Origin(tag.getUUID("nodeId"), tag.getString("dimension"),
                tag.getLong("position"), tag.getLong("generation"));
    }

    private static Object grid(MEPatternBufferPartMachine buffer) {
        return buffer.isRemote() || !buffer.getMainNode().isOnline() ? null : buffer.getMainNode().getGrid();
    }
}
