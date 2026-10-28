package com.gtl.enhancedcore.common.machine.galaxy;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** World-tick payment must not inherit GTLCore's wall-clock machine tick throttle. */
@Mod.EventBusSubscriber(modid = GTLEnhancedcore.MOD_ID)
public final class StellarFieldTicker {
    private static final Map<Level, Set<StellarVortexSpaceStationMachine>> LOADED = new WeakHashMap<>();

    private StellarFieldTicker() {}

    static synchronized void register(StellarVortexSpaceStationMachine machine) {
        Level level = machine.getLevel();
        if (level != null && !level.isClientSide) {
            LOADED.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(machine);
        }
    }

    static synchronized void unregister(StellarVortexSpaceStationMachine machine) {
        LOADED.values().forEach(machines -> machines.remove(machine));
        LOADED.values().removeIf(Set::isEmpty);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        StellarVortexSpaceStationMachine[] machines;
        synchronized (StellarFieldTicker.class) {
            Set<StellarVortexSpaceStationMachine> registered = LOADED.get(event.level);
            if (registered == null) return;
            machines = registered.toArray(StellarVortexSpaceStationMachine[]::new);
        }
        for (var machine : machines) {
            if (machine.getLevel() == event.level && !machine.isInValid()) machine.tickField();
            else unregister(machine);
        }
    }

    @SubscribeEvent
    public static synchronized void onServerStopped(ServerStoppedEvent event) { LOADED.clear(); }
}
