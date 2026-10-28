package com.gtl.enhancedcore.common.cloud.transfer;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Only registered source pattern inventories are guarded; unrelated inventories are unaffected. */
public final class CloudImportInventoryGuard {
    private static final Map<Object, WeakReference<CloudImportSourceAccess>> SOURCES = new WeakHashMap<>();
    private static final ThreadLocal<Object> RECOVERY = new ThreadLocal<>();
    private CloudImportInventoryGuard() {}
    public static synchronized void register(Object inventory, CloudImportSourceAccess source) { SOURCES.put(inventory, new WeakReference<>(source)); }
    public static synchronized boolean blocked(Object inventory) {
        var reference = SOURCES.get(inventory); var source = reference == null ? null : reference.get();
        return source != null && (source.cloud$importQuarantined() || CloudNetworkPatternImport.sourceLocked(source)) && RECOVERY.get() != inventory;
    }
    static <T> T recover(Object inventory, java.util.function.Supplier<T> action) {
        if (RECOVERY.get() != null) throw new IllegalStateException("Nested import recovery");
        RECOVERY.set(inventory);
        try { return action.get(); } finally { RECOVERY.remove(); }
    }
}
