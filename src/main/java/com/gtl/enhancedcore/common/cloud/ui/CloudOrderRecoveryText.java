package com.gtl.enhancedcore.common.cloud.ui;

import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Shared display of server-owned frozen-order summaries; never authorizes a recovery. */
final class CloudOrderRecoveryText {
    private CloudOrderRecoveryText() {}

    static String value(CompoundTag row, String key) {
        String value = row == null ? "" : row.getString(key);
        return value.isEmpty() ? CloudPatternPanel.tr("recovery_unknown") : value;
    }

    static String status(CompoundTag row) {
        String status = row == null ? "" : row.getString("status");
        return switch (status) {
            case "FROZEN" -> CloudPatternPanel.tr("recovery_status_frozen");
            case "NEEDS_REVIEW" -> CloudPatternPanel.tr("recovery_status_review");
            default -> value(row, "status");
        };
    }

    static String mode(CompoundTag row) {
        String mode = row == null ? "" : row.getString("mode");
        return mode.isEmpty() ? CloudPatternPanel.tr("recovery_unknown") : CloudPatternPanel.recipeType(mode);
    }

    static String machine(CompoundTag row) {
        String name = value(row, "machineDefinition");
        var id = ResourceLocation.tryParse(name);
        if (id == null) return name;
        String key = "block." + id.getNamespace() + "." + id.getPath();
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? name : translated;
    }

    static String reason(CompoundTag row) {
        if (row == null) return CloudPatternPanel.tr("recovery_select_hint");
        String reason = row.getString("recoverReason");
        return reason.isEmpty() ? CloudPatternPanel.tr(row.getBoolean("recoverAllowed")
                ? "recovery_claim_hint" : "recovery_target_unavailable") : Component.translatable(reason).getString();
    }

    static Component[] tooltip(CompoundTag row) {
        var lines = new ArrayList<Component>();
        if (row == null) {
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_select_hint"));
        } else {
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_order_status", status(row)));
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_order_machine", machine(row)));
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_order_mode", mode(row)));
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_order_location", value(row, "sourceDimension"), value(row, "sourcePos")));
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_order_jobs", row.getInt("jobCount")));
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_operation", value(row, "id")));
            if (row.contains("recoverAllowed")) lines.add(Component.literal(reason(row)));
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_order_hint_1"));
            lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_order_hint_2"));
        }
        lines.add(Component.translatable(CloudPatternPanel.PREFIX + "recovery_safe_hint"));
        return lines.toArray(Component[]::new);
    }

    static ImageWidget line(WidgetGroup parent, int x, int y, int width, Supplier<String> text, int color, String id) {
        var line = new ImageWidget(x, y, width, 10, new TextTexture(() -> text.get().replace("%", "%%"))
                .setColor(color).setDropShadow(false).setWidth(width).setType(TextTexture.TextType.LEFT_HIDE));
        line.setClientSideWidget(); line.setId(id); parent.addWidget(line); return line;
    }
}
