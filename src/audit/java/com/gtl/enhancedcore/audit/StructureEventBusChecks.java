package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.performance.structure.StructureSearchBackoff;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchScheduler;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchWatches;
import com.gtl.enhancedcore.common.performance.structure.StructureSearchEvents;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.ModLauncherFactory;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Uses the real ModLauncher generated wrappers and real event post; never dispatches methods directly. */
public final class StructureEventBusChecks {
    private StructureEventBusChecks() {}

    public static void run(List<String> checks) throws Exception {
        var factory = new ModLauncherFactory();
        var oldChunk = LegacyHandlers.class.getDeclaredMethod("unloaded", ChunkEvent.Unload.class);
        var oldLevel = LegacyHandlers.class.getDeclaredMethod("unloaded", LevelEvent.Unload.class);
        check(factory.getUniqueName(oldChunk).equals(factory.getUniqueName(oldLevel)), checks,
                "Actual Forge wrapper naming aliases the original ChunkEvent.Unload and LevelEvent.Unload overloads");
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        // Only an uninitialized holder is needed: real event constructors read its null world;
        // no level, chunk storage, registries or player save are created or accessed.
        var chunk = (LevelChunk) unsafe.allocateInstance(LevelChunk.class);
        var broken = BusBuilder.builder().useModLauncher().setExceptionHandler((bus, event, listeners, index, failure) -> {}).build();
        int casts = 0;
        try {
            broken.register(LegacyHandlers.class);
            for (Event event : events(chunk)) {
                try { broken.post(event); }
                catch (ClassCastException expected) { casts++; }
            }
            check(casts == 1, checks,
                    "Real Forge event post reproduces exactly one original overloaded-Unload ClassCastException");
        } finally {
            broken.unregister(LegacyHandlers.class);
        }

        var fixed = BusBuilder.builder().useModLauncher().build();
        AtomicInteger loads = new AtomicInteger(), unloads = new AtomicInteger(), levels = new AtomicInteger();
        try {
            fixed.register(StructureSearchEvents.class);
            fixed.addListener(EventPriority.LOWEST, false, ChunkEvent.Load.class, event -> loads.incrementAndGet());
            fixed.addListener(EventPriority.LOWEST, false, ChunkEvent.Unload.class, event -> unloads.incrementAndGet());
            fixed.addListener(EventPriority.LOWEST, false, LevelEvent.Unload.class, event -> levels.incrementAndGet());
            for (int i = 0; i < 10; i++) for (Event event : events(chunk)) fixed.post(event);
            check(loads.get() == 10 && unloads.get() == 10 && levels.get() == 10, checks,
                    "Real isolated bus posts load chunk-unload and level-unload through production generated handlers repeatedly");
        } finally {
            fixed.unregister(StructureSearchEvents.class);
        }
        var names = new HashSet<String>();
        for (var method : StructureSearchEvents.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribeEvent.class)) {
                check(names.add(factory.getUniqueName(method)), checks,
                        "Production generated wrapper name is unique: " + method.getName());
            }
        }
        check(names.size() == 3, checks, "All three production structure event subscribers are covered");
    }

    public static void runWorld(ServerLevel level, List<String> checks) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        var bus = BusBuilder.builder().useModLauncher().build();
        var watches = StructureSearchScheduler.watches(level);
        var budget = new StructureSearchBackoff();
        var chunk = level.getChunk(32, 32);
        var bounds = new StructureSearchWatches.Bounds(512, level.getMinBuildHeight(), 512,
                527, level.getMaxBuildHeight() - 1, 527);
        try {
            bus.register(StructureSearchEvents.class);
            watches.watch(budget, bounds);
            long token = budget.begin(0); budget.failed(0, token);
            bus.post(new ChunkEvent.Load(chunk, false));
            check(!budget.current(token) && budget.failures() == 0, checks,
                    "Real server ChunkEvent.Load post wakes the watched structure through its generated handler");
            token = budget.begin(0); budget.failed(0, token);
            bus.post(new ChunkEvent.Unload(chunk));
            check(!budget.current(token) && budget.failures() == 0, checks,
                    "Real server ChunkEvent.Unload post wakes the watched structure through its generated handler");
            bus.post(new LevelEvent.Unload(level));
            check(watches.searchCount() == 0 && watches.referenceCount() == 0 && budget.begin(Long.MAX_VALUE) == -1,
                    checks, "Real server LevelEvent.Unload post clears watches and disables old search generations");
        } finally {
            watches.remove(budget);
            bus.unregister(StructureSearchEvents.class);
        }
    }

    public static final class LegacyHandlers {
        @SubscribeEvent public static void loaded(ChunkEvent.Load event) {}
        @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) {}
        @SubscribeEvent public static void unloaded(LevelEvent.Unload event) {}
    }

    private static Event[] events(LevelChunk chunk) {
        // Forge advances each event's phase during dispatch; an instance cannot be posted twice.
        return new Event[]{new ChunkEvent.Load(chunk, false), new ChunkEvent.Unload(chunk), new LevelEvent.Unload(null)};
    }

    private static void check(boolean condition, List<String> checks, String label) {
        if (!condition) throw new AssertionError(label);
        checks.add(label);
    }
}
