package com.gtl.enhancedcore.common.cloud;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import net.minecraft.nbt.CompoundTag;

/** Runtime bindings follow the controller's real loaded wired/wireless AE grid, never a player's default library. */
public final class CloudLibraryNetwork {
    private static final List<WeakReference<CloudPatternLibraryMachine>> LOADED = new ArrayList<>();
    private static List<WeakReference<CloudPatternLibraryMachine>> loadedSnapshot = List.of();
    private CloudLibraryNetwork() {}
    public enum State { DISCONNECTED, CONFLICT, DENIED, CONNECTED }
    public record Binding(State state, UUID libraryId, CloudPatternLibraryMachine main) {
        public boolean available() { return state == State.CONNECTED; }
        public String translationKey() { return "gtl_enhancedcore.cloud.network." + state.name().toLowerCase(java.util.Locale.ROOT); }
    }
    static void loaded(CloudPatternLibraryMachine machine) {
        if (!(machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || !level.getServer().isSameThread()) return;
        unloaded(machine); LOADED.add(new WeakReference<>(machine)); loadedSnapshot = List.copyOf(LOADED);
    }
    static void unloaded(CloudPatternLibraryMachine machine) {
        if (!(machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || !level.getServer().isSameThread()) return;
        if (LOADED.removeIf(ref -> ref.get() == null || ref.get() == machine)) loadedSnapshot = List.copyOf(LOADED);
    }
    /** Resolves association from the persisted physical AE machine anchor, never from the controller selector. */
    static IGrid serviceGrid(CloudPatternLibraryMachine main) {
        if (!(main.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || main.libraryId() == null || main.owner() == null) return null;
        try {
            var library = CloudPatternStore.get(level).library(main.libraryId());
            var anchor = library.meAnchor();
            if (anchor.isEmpty()) {
                IGrid candidate = main.getMainNode().isActive() ? main.getMainNode().getGrid() : null;
                if (candidate == null) return null;
                CompoundTag first = firstExternalMachineAnchor(candidate, main);
                if (first.isEmpty() || !library.bindMeAnchor(first)) return null;
                anchor = library.meAnchor();
            }
            IGrid anchorGrid = resolveAnchor(level, anchor);
            return anchorGrid != null && main.getMainNode().isActive()
                    && main.getMainNode().getGrid() == anchorGrid ? anchorGrid : null;
        } catch (RuntimeException unavailable) { return null; }
    }
    private static CompoundTag firstExternalMachineAnchor(IGrid grid, CloudPatternLibraryMachine main) {
        for (IGridNode node : grid.getNodes()) {
            Object owner = node.getOwner();
            if (!(owner instanceof MetaMachine machine) || machine == main || !(machine instanceof IGridConnectedMachine connected)
                    || !node.isActive() || node.getGrid() != grid || connected.getMainNode().getNode() != node) continue;
            var holder = machine.getHolder().getSelf();
            if (holder == null || holder.isRemoved() || holder.getLevel() != main.getLevel()) continue;
            CompoundTag address = new CompoundTag();
            address.putString("dimension", main.getLevel().dimension().location().toString());
            address.putLong("position", holder.getBlockPos().asLong());
            address.putString("blockEntity", net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(holder.getType()).toString());
            address.putString("machine", machine.getDefinition().getId().toString());
            address.putInt("side", -1);
            return address;
        }
        return new CompoundTag();
    }
    private static IGrid resolveAnchor(ServerLevel origin, CompoundTag address) {
        try {
            ResourceLocation dimension = ResourceLocation.tryParse(address.getString("dimension"));
            if (dimension == null) return null;
            ServerLevel level = origin.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null) return null;
            BlockPos pos = BlockPos.of(address.getLong("position"));
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            BlockEntity holder = chunk == null ? null : chunk.getBlockEntity(pos);
            if (!(holder instanceof IMachineBlockEntity machineHolder)
                    || !net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(holder.getType()).toString().equals(address.getString("blockEntity"))) return null;
            MetaMachine machine = machineHolder.getMetaMachine();
            if (!(machine instanceof IGridConnectedMachine connected)
                    || !machine.getDefinition().getId().toString().equals(address.getString("machine"))) return null;
            var node = connected.getMainNode().getNode();
            return node != null && node.isActive() ? node.getGrid() : null;
        } catch (RuntimeException unavailable) { return null; }
    }
    public static Binding resolve(IGrid grid, ServerPlayer player) {
        return resolve(grid, player == null ? null : player.getUUID());
    }
    public static Binding resolve(IGrid grid, UUID actor) {
        if (grid == null) return new Binding(State.DISCONNECTED, null, null);
        if (LOADED.removeIf(reference -> reference.get() == null)) loadedSnapshot = List.copyOf(LOADED);
        CloudPatternLibraryMachine selected = null;
        boolean authorized = false;
        for (var reference : loadedSnapshot) {
            var main = reference.get();
            // The controller node must belong to this exact grid before resolving its durable anchor.
            if (main == null || main.getMainNode().getGrid() != grid || !main.networkAvailable(grid)) continue;
            if (selected != null && !selected.libraryId().equals(main.libraryId()))
                return new Binding(State.CONFLICT, null, null);
            boolean allowed = CloudLibraryService.runtimeAuthorized(actor, main.libraryId(), main.serverLevel(), main.getPos());
            if (selected == null || allowed) selected = main;
            authorized |= allowed;
        }
        if (selected == null) return new Binding(State.DISCONNECTED, null, null);
        return new Binding(authorized ? State.CONNECTED : State.DENIED, selected.libraryId(), selected);
    }
}
