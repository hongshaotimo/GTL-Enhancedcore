package com.gtl.enhancedcore.common.cloud;

import appeng.api.networking.IGrid;
import com.gtl.enhancedcore.common.cloud.store.CloudPatternStore;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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
    /** The controller's current node is the wired endpoint; wireless membership adds an exact saved core constraint. */
    static IGrid serviceGrid(CloudPatternLibraryMachine main) {
        if (!(main.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || main.libraryId() == null || main.owner() == null) return null;
        try {
            var library = CloudPatternStore.get(level).library(main.libraryId());
            if (!library.healthy()) return null;
            IGrid current = main.getMainNode().isActive() ? main.getMainNode().getGrid() : null;
            if (current == null) return null;
            var wireless = CloudMeConnection.selectedNetwork(main);
            return !wireless.selected() || wireless.grid() == current ? current : null;
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
            // Only the controller's own current grid may expose the library to this resolver.
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
