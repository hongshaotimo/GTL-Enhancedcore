package com.gtl.enhancedcore.common.recipe.iv;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;

/** Shared GUI/Jade vocabulary: an order is not a processing batch. */
public final class IvPresentation {
    private IvPresentation() {}
    public static String number(long value) { return NumberFormat.getIntegerInstance(Locale.ROOT).format(value); }
    public static String number(String value) {
        try { return NumberFormat.getIntegerInstance(Locale.ROOT).format(new BigInteger(value)); }
        catch (NumberFormatException ignored) { return value; }
    }
    private static Component value(Object value, ChatFormatting color) {
        return Component.literal(value instanceof Number n ? number(n.longValue()) : number(String.valueOf(value))).withStyle(color);
    }
    public static void append(List<Component> text, CompoundTag data, boolean detail) {
        // Parallel and thread capacity use the upstream GUI/Jade rows; this section only describes orders.
        text.add(Component.translatable("gtl_enhancedcore.gui.iv_queue",value(data.getInt("active"),ChatFormatting.GREEN),
                value(data.getInt("count"),ChatFormatting.WHITE),value(data.getInt("outputWaiting"),ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        text.add(Component.translatable("gtl_enhancedcore.gui.iv_remaining",value(data.getString("remaining"),ChatFormatting.YELLOW),
                value(data.getString("running"),ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        if (data.getInt("active") > 0) text.add(Component.translatable("gtl_enhancedcore.gui.iv_time_help").withStyle(ChatFormatting.DARK_GRAY));
        if (!detail) return;
        var rows = data.getList("orders",Tag.TAG_COMPOUND);
        for (int i=0;i<rows.size();i++) {
            var row=rows.getCompound(i); String type=row.getString("type").replace(':','.');
            text.add(Component.translatable("gtl_enhancedcore.gui.iv_order",row.getInt("slot"),Component.translatable(type).withStyle(ChatFormatting.AQUA),
                    value(row.getLong("delivered"),ChatFormatting.GREEN),value(row.getLong("total"),ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
            if (row.getBoolean("recoveredTail")) text.add(Component.translatable("gtl_enhancedcore.gui.iv_recovered_tail").withStyle(ChatFormatting.DARK_GRAY));
            if (row.getLong("cancelled")>0) text.add(Component.translatable("gtl_enhancedcore.gui.iv_cancelled_count",value(row.getLong("cancelled"),ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
            if (row.getLong("running")>0)
                text.add(Component.translatable("gtl_enhancedcore.gui.iv_batch",value(row.getLong("running"),ChatFormatting.GOLD),row.getInt("batchPercent"),value(row.getLong("remaining"),ChatFormatting.YELLOW)).withStyle(ChatFormatting.DARK_GRAY));
            else if (row.getBoolean("waitingOutput")) text.add(Component.translatable("gtl_enhancedcore.gui.iv_output_wait").withStyle(ChatFormatting.YELLOW));
            Component reason = com.gtl.enhancedcore.common.recipe.MachineDiagnostics.decode(row.getString("reason"));
            if (reason != null) text.add(reason.copy().withStyle(ChatFormatting.YELLOW));
        }
        if(data.getInt("count")>rows.size())text.add(Component.translatable("gtl_enhancedcore.gui.iv_more_orders",data.getInt("count")-rows.size()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
