package com.gtl.enhancedcore.integration.ftbquests;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

public final class QuestVisibilityCycleGuard {
    private static final ThreadLocal<Set<Object>> ACTIVE = ThreadLocal.withInitial(
            () -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private QuestVisibilityCycleGuard() {}

    public static boolean evaluate(Object quest, BooleanSupplier visibilityCheck) {
        Set<Object> active = ACTIVE.get();
        if (!active.add(quest)) return false;
        try {
            return visibilityCheck.getAsBoolean();
        } finally {
            active.remove(quest);
            if (active.isEmpty()) ACTIVE.remove();
        }
    }
}
