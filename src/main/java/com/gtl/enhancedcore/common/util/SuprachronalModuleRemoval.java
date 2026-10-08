package com.gtl.enhancedcore.common.util;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;

public final class SuprachronalModuleRemoval {
    private SuprachronalModuleRemoval() {}

    public static <ModuleType> void detachLegacyModules(Set<ModuleType> modules,
                                                       Consumer<? super ModuleType> disconnect) {
        if (modules.isEmpty()) return;
        var previousModules = new ArrayList<>(modules);
        modules.clear();
        for (ModuleType module : previousModules) {
            if (module != null) disconnect.accept(module);
        }
    }

    public static boolean isInstalledModuleCount(String translationKey) {
        return "tooltip.gtlcore.installed_module_count".equals(translationKey);
    }

    public static boolean previewHasModules(boolean originalFlag, boolean suprachronalController) {
        return originalFlag && !suprachronalController;
    }
}
