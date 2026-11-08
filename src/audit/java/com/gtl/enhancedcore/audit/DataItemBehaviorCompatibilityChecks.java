package com.gtl.enhancedcore.audit;

import com.gregtechceu.gtceu.common.item.DataItemBehavior;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/** Runs against the real transformed GTCEu class in a disposable Forge title-screen client. */
public final class DataItemBehaviorCompatibilityChecks {
    private DataItemBehaviorCompatibilityChecks() {}

    public static List<String> run() throws Exception {
        List<String> checks = new ArrayList<>();
        DataItemBehavior behavior = new DataItemBehavior();
        ItemStack empty = new ItemStack(Items.PAPER);
        List<Component> tooltip = new ArrayList<>();
        behavior.appendHoverText(empty, null, tooltip, TooltipFlag.Default.NORMAL);
        check(empty.getTag() == null && tooltip.isEmpty(), "Unbound tooltip leaves absent NBT absent", checks);
        for (String key : List.of("pos", "superPos")) {
            ItemStack bound = new ItemStack(Items.PAPER);
            CompoundTag tag = new CompoundTag();
            tag.putIntArray(key, new int[]{17, 42, -29});
            tag.putString("audit_untouched", "preserved");
            bound.setTag(tag);
            CompoundTag before = tag.copy();
            tooltip.clear();
            behavior.appendHoverText(bound, null, tooltip, TooltipFlag.Default.NORMAL);
            String translation = key.equals("pos") ? "gtceu.tooltip.proxy_bind" : "gtceu.tooltip.super_proxy_bind";
            check(tooltip.size() == 1 && tooltip.get(0).getContents() instanceof TranslatableContents contents
                    && contents.getKey().equals(translation), "ADD " + key + " binding tooltip remains", checks);
            TranslatableContents contents = (TranslatableContents) tooltip.get(0).getContents();
            check(contents.getArgs().length == 3
                    && ((Component) contents.getArgs()[0]).getString().equals("17")
                    && ((Component) contents.getArgs()[1]).getString().equals("42")
                    && ((Component) contents.getArgs()[2]).getString().equals("-29"),
                    "ADD " + key + " binding coordinates remain", checks);
            check(bound.getTag() == tag && tag.equals(before), "ADD " + key + " tooltip does not mutate NBT", checks);
        }
        Method reader = Arrays.stream(DataItemBehavior.class.getDeclaredMethods())
                .filter(method -> method.getName().endsWith("$gtlcore$readTooltipData")
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{ItemStack.class})
                        && method.getReturnType() == CompoundTag.class)
                .findFirst().orElseThrow(() -> new AssertionError("GTLCore read-only redirect was not merged"));
        reader.setAccessible(true);
        CompoundTag fallback = (CompoundTag) reader.invoke(behavior, empty);
        check(fallback.isEmpty() && empty.getTag() == null, "GTLCore null NBT reader returns detached empty tag", checks);
        ItemStack present = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putString("audit", "kept");
        present.setTag(tag);
        check(reader.invoke(behavior, present) == tag, "GTLCore reader preserves existing tag identity", checks);
        return checks;
    }

    private static void check(boolean condition, String description, List<String> checks) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
    }
}
