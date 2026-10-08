package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Uses temporary non-player keys in the real transformed field, only in an isolated audit client. */
public final class FlightStateConcurrencyChecks {
    private FlightStateConcurrencyChecks() {}

    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        if (!Boolean.getBoolean("gtl.enhancedcore.expectFlightFix")) return;
        Class<?> handler = Class.forName("committee.nova.mods.avaritia.init.handler.AbilityHandler");
        var field = handler.getField("entitiesWithFlight");
        if (Modifier.isFinal(field.getModifiers())) throw new AssertionError("Flight map AT not applied");
        var map = (Map<String, Object>) field.get(null);
        if (!map.getClass().getName().equals("java.util.Collections$SynchronizedMap")) {
            throw new AssertionError("Flight map was not synchronized before world entry");
        }
        String prefix = "enhancedcore-audit-" + java.util.UUID.randomUUID();
        var names = java.util.List.of(prefix + ":true", prefix + ":false");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (String key : names) futures.add(executor.submit(() -> {
                for (int i = 0; i < 2000; i++) {
                    Object value = map.computeIfAbsent(key, ignored -> new Object());
                    if (map.get(key) != value) throw new AssertionError("Flight map value identity changed");
                    if (map.remove(key) != value) throw new AssertionError("Flight map remove identity changed");
                }
            }));
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            names.forEach(map::remove);
        }
        GTLEnhancedcore.LOGGER.info("[CHUNK_BENCH] FLIGHT_MAP_CHECKS synchronized=true final=false operations=12000");
    }
}
