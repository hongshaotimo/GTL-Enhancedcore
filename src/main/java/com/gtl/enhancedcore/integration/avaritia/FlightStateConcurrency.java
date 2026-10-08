package com.gtl.enhancedcore.integration.avaritia;

import com.gtl.enhancedcore.GTLEnhancedcore;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Re-Avaritia integration; does not alter ability callbacks, keys or flight-state objects. */
public final class FlightStateConcurrency {
    private static final String HANDLER = "committee.nova.mods.avaritia.init.handler.AbilityHandler";

    private FlightStateConcurrency() {}

    /** Call only from commonSetup enqueueWork on the main thread, before entering any world. */
    public static void install() {
        if (FMLEnvironment.dist != Dist.CLIENT || !ModList.get().isLoaded("avaritia")) return;
        try {
            var field = Class.forName(HANDLER).getField("entitiesWithFlight");
            if (!Modifier.isStatic(field.getModifiers())) {
                throw new IllegalStateException("Avaritia entitiesWithFlight is no longer static");
            }
            Object original = field.get(null);
            if (original == null || original.getClass() != HashMap.class) {
                GTLEnhancedcore.LOGGER.info(
                        "[AvaritiaFlightState] Preserving existing entitiesWithFlight container: {}",
                        original == null ? "null" : original.getClass().getName());
                return;
            }
            if (Modifier.isFinal(field.getModifiers())) {
                throw new IllegalStateException(
                        "Avaritia entitiesWithFlight is still final; required access transformer was not applied");
            }

            // Wrap the existing map, not a copy: retain every entry and FlightInfo identity.
            Map<?, ?> originalMap = (Map<?, ?>) original;
            Map<?, ?> replacement = Collections.synchronizedMap(originalMap);
            field.set(null, replacement);
            if (field.get(null) != replacement) {
                throw new IllegalStateException("Avaritia entitiesWithFlight replacement failed identity readback");
            }
            GTLEnhancedcore.LOGGER.info(
                    "[AvaritiaFlightState] Installed synchronized entitiesWithFlight; entries={}, readback=verified",
                    replacement.size());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not install Avaritia flight-state synchronization", exception);
        }
    }
}
