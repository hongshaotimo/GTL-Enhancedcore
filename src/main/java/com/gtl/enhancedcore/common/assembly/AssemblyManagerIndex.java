package com.gtl.enhancedcore.common.assembly;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;

/** A dimension-local inventory of known machines, retained while their chunks are unloaded. */
public final class AssemblyManagerIndex extends SavedData {
    public static final ResourceLocation TARGET = new ResourceLocation("gtladditions", "me_super_pattern_buffer");
    private final Map<BlockPos, String> targets = new HashMap<>();
    private long revision;
    private long snapshotRevision = -1;
    private List<AssemblyManagerNetwork.Target> cachedSnapshot = List.of();

    public static AssemblyManagerIndex get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(AssemblyManagerIndex::load,
                AssemblyManagerIndex::new, "gtl_enhancedcore_assembly_manager");
    }

    public static boolean isTarget(MetaMachine machine) {
        return machine instanceof MEPatternBufferPartMachineBase && TARGET.equals(machine.getDefinition().getId());
    }

    /** Called after load and name changes, independently of multiblock formation. */
    public static void update(MetaMachine machine) {
        if (machine.getLevel() instanceof ServerLevel level && isTarget(machine)) {
            var index = get(level);
            String name = ((MEPatternBufferPartMachineBase) machine).getCustomName();
            name = name == null ? "" : name;
            BlockPos pos = machine.getPos().immutable();
            if (!name.equals(index.targets.put(pos, name))) index.changed();
        }
    }

    public static void remove(MetaMachine machine) {
        if (machine.getLevel() instanceof ServerLevel level && isTarget(machine)) get(level).remove(machine.getPos());
    }

    private void remove(BlockPos pos) {
        if (targets.remove(pos) != null) changed();
    }

    private void changed() { revision++; snapshotRevision = -1; setDirty(); }

    /** Reconcile only an already loaded chunk, including replacement/removal while offline. */
    public void reconcile(ServerLevel level, LevelChunk chunk) {
        var chunkPos = chunk.getPos();
        for (BlockPos pos : new ArrayList<>(targets.keySet())) {
            if ((pos.getX() >> 4) == chunkPos.x && (pos.getZ() >> 4) == chunkPos.z
                    && !isTarget(MetaMachine.getMachine(chunk, pos))) remove(pos);
        }
        for (var blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
            var machine = MetaMachine.getMachine(chunk, blockEntity.getBlockPos());
            if (isTarget(machine)) update(machine);
        }
    }

    /** Event-maintained data only: no per-reader machine traversal or chunk loading. */
    public List<AssemblyManagerNetwork.Target> snapshot(ServerLevel level) {
        if (snapshotRevision == revision) return cachedSnapshot;
        List<AssemblyManagerNetwork.Target> result = new ArrayList<>();
        targets.forEach((pos, name) -> result.add(new AssemblyManagerNetwork.Target(pos, name)));
        result.sort(java.util.Comparator.comparingLong(target -> target.pos().asLong()));
        cachedSnapshot = List.copyOf(result);
        snapshotRevision = revision;
        return cachedSnapshot;
    }

    public boolean contains(BlockPos pos) { return targets.containsKey(pos); }

    private static AssemblyManagerIndex load(CompoundTag tag) {
        var index = new AssemblyManagerIndex();
        var list = tag.getList("targets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            index.targets.put(BlockPos.of(entry.getLong("pos")), entry.getString("name"));
        }
        return index;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        targets.forEach((pos, name) -> {
            var entry = new CompoundTag();
            entry.putLong("pos", pos.asLong()); entry.putString("name", name); list.add(entry);
        });
        tag.put("targets", list);
        return tag;
    }
}
