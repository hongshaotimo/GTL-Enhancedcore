package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.integration.jade.IvBufferInfoProvider;
import com.gtl.enhancedcore.integration.jade.SuperBufferJadeNames;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.Identifiers;
import snownee.jade.impl.Tooltip;
import snownee.jade.impl.ui.ElementHelper;

public final class SuperBufferJadeClientTitleChecks {
    private static final String NAME_KEY = "gtlEnhancedcoreSuperBufferName";
    private static final String STATE_KEY = "gtlEnhancedcoreIvBuffer";
    private static final String ORIGINAL_TITLE = "Original machine title";
    private static final String PRESERVED_LINE = "Preserved third-party line";

    private SuperBufferJadeClientTitleChecks() {}

    public static int run() {
        var previousUid = ElementHelper.INSTANCE.currentUid();
        try {
            ElementHelper.INSTANCE.setCurrentUid(new IvBufferInfoProvider().getUid());
            return runChecks();
        } finally {
            ElementHelper.INSTANCE.setCurrentUid(previousUid);
        }
    }

    private static int runChecks() {
        var checks = new Checks();
        checks.require(Language.getInstance().has("gtceu.compressor"), "The real client recipe-type translation is missing");
        for (var snapshot : List.of(
                new SuperBufferJadeNames.NameSnapshot("压缩机", "gtceu.compressor"),
                new SuperBufferJadeNames.NameSnapshot("我的样板总成", ""),
                new SuperBufferJadeNames.NameSnapshot("gtceu.compressor", ""))) {
            for (boolean includeState : List.of(false, true)) {
                var data = new CompoundTag();
                var name = new CompoundTag();
                snapshot.write(name);
                data.put(NAME_KEY, name);
                if (includeState) {
                    var state = new CompoundTag();
                    state.putInt("jobs", 2);
                    state.putBoolean("accepting", true);
                    data.put(STATE_KEY, state);
                }
                var before = data.copy();
                var tooltip = tooltip(true);
                new IvBufferInfoProvider().appendTooltip(tooltip, accessor(data), null);
                checks.require(tooltip.get(Identifiers.CORE_OBJECT_NAME).size() == 1, "Jade contains a duplicate object title");
                checks.require(tooltip.get(Identifiers.CORE_OBJECT_NAME).getFirst().getMessage().equals(snapshot.display().getString()),
                        "The actual Jade title did not display the received name in the current language");
                checks.require(tooltip.getMessage().contains(PRESERVED_LINE), "Replacing the title erased a third-party line");
                checks.require(!tooltip.getMessage().contains(ORIGINAL_TITLE), "The stale machine title remains alongside its replacement");
                checks.require(data.equals(before), "Client title rendering mutated server data");
                checks.require(tooltip.size() == (includeState ? 5 : 2), "Replacing the title damaged isolation status lines");
                var hidden = tooltip(false);
                new IvBufferInfoProvider().appendTooltip(hidden, accessor(data), null);
                checks.require(hidden.get(Identifiers.CORE_OBJECT_NAME).isEmpty(), "A custom name bypassed the hidden-title setting");
                checks.require(hidden.getMessage().contains(PRESERVED_LINE), "The hidden-title path erased unrelated information");
            }
        }
        for (var invalid : List.of(new CompoundTag(), emptyName(), invalidName())) {
            var data = new CompoundTag();
            data.put(NAME_KEY, invalid);
            var tooltip = tooltip(true);
            new IvBufferInfoProvider().appendTooltip(tooltip, accessor(data), null);
            checks.require(tooltip.get(Identifiers.CORE_OBJECT_NAME).size() == 1, "Invalid metadata erased the regular title");
            checks.require(tooltip.get(Identifiers.CORE_OBJECT_NAME).getFirst().getMessage().equals(ORIGINAL_TITLE),
                    "Invalid metadata changed the regular title");
            checks.require(tooltip.getMessage().contains(PRESERVED_LINE), "Invalid metadata erased unrelated information");
        }
        return checks.assertions;
    }

    private static Tooltip tooltip(boolean showTitle) {
        var tooltip = new Tooltip();
        if (showTitle) tooltip.add(0, Component.literal(ORIGINAL_TITLE), Identifiers.CORE_OBJECT_NAME);
        tooltip.add(Component.literal(PRESERVED_LINE));
        return tooltip;
    }

    private static BlockAccessor accessor(CompoundTag data) {
        return (BlockAccessor) Proxy.newProxyInstance(BlockAccessor.class.getClassLoader(), new Class<?>[]{BlockAccessor.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getServerData" -> data;
                    case "toString" -> "SuperBufferJadeClientTitleAccessor";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new IllegalStateException("Unexpected Jade title accessor: " + method.getName());
                });
    }

    private static CompoundTag emptyName() {
        var data = new CompoundTag();
        data.putString("name", "");
        data.putString("nameKey", "gtceu.compressor");
        return data;
    }

    private static CompoundTag invalidName() {
        var data = new CompoundTag();
        data.putInt("name", 7);
        return data;
    }

    private static final class Checks {
        private int assertions;

        private void require(boolean condition, String message) {
            assertions++;
            if (!condition) throw new AssertionError(message);
        }
    }
}
