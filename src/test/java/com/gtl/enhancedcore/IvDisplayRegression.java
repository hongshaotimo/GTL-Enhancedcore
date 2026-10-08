package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvPresentation;
import com.gtl.enhancedcore.mixin.gtceu.IvNativeMachineUiMixin;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Checks that isolation display rows retain upstream information while removing conflicting capacity rows. */
public final class IvDisplayRegression {
    private static int assertions;

    public static int run() throws Exception {
        Method canonical = IvNativeMachineUiMixin.class.getDeclaredMethod(
                "iv$canonicalLine", List.class, String.class, Component.class);
        canonical.setAccessible(true);
        Method plainBatch = IvNativeMachineUiMixin.class.getDeclaredMethod("iv$plainBatchThreads", int.class);
        plainBatch.setAccessible(true);
        Method threadLine = IvNativeMachineUiMixin.class.getDeclaredMethod("iv$threadLine", int.class);
        threadLine.setAccessible(true);

        Component tier = Component.translatable("gtceu.multiblock.max_recipe_tier", "IV");
        Component mode = Component.translatable("gtceu.gui.machinemode", "original");
        Component parallel = Component.translatable("gtceu.multiblock.parallel", 64);
        Component thread = Component.translatable("gtladditions.multiblock.threads", 64);
        Component diagnostic = Component.translatable("gtlcore.machine.temperature", 1800);
        check((int)plainBatch.invoke(null, 0) == 64
                        && (int)plainBatch.invoke(null, 128) == 192
                        && (int)plainBatch.invoke(null, Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "ordinary multi-recipe threads match GTLAdditions' saturated 64 + additional-thread value");
        var plainLines = new ArrayList<>(List.of(tier, mode, parallel, thread, thread, diagnostic));
        canonical.invoke(null, plainLines, "gtladditions.multiblock.threads", threadLine.invoke(null, 64));
        check(plainLines.getFirst() == tier && plainLines.get(1) == mode
                        && plainLines.get(2) == parallel && plainLines.contains(diagnostic)
                        && count(plainLines, "gtladditions.multiblock.threads") == 1,
                "ordinary mode keeps upstream tier, machine mode, parallel, and diagnostics while deduplicating threads");
        Object plainArgument = ((TranslatableContents)plainLines.get(3).getContents()).getArgs()[0];
        check(plainArgument instanceof Component value && value.getString().equals("64"),
                "ordinary mode displays the upstream 64-thread base");
        canonical.invoke(null, plainLines, "gtladditions.multiblock.threads", threadLine.invoke(null, 192));
        Object upgradedArgument = ((TranslatableContents)plainLines.get(3).getContents()).getArgs()[0];
        check(upgradedArgument instanceof Component value && value.getString().equals("192")
                        && count(plainLines, "gtladditions.multiblock.threads") == 1,
                "ordinary mode adds modifier threads without creating a second row");
        canonical.invoke(null, plainLines, "gtladditions.multiblock.threads", null);
        check(count(plainLines, "gtladditions.multiblock.threads") == 0,
                "ordinary mutable single-recipe mode has no cross-recipe thread row");
        var missingThread = new ArrayList<>(List.of(tier, mode, parallel, diagnostic));
        canonical.invoke(null, missingThread, "gtladditions.multiblock.threads", threadLine.invoke(null, 64));
        check(count(missingThread, "gtladditions.multiblock.threads") == 1
                        && missingThread.get(3).getContents() instanceof TranslatableContents translated
                        && translated.getKey().equals("gtladditions.multiblock.threads")
                        && missingThread.get(4) == diagnostic,
                "a missing thread row is placed beside the upstream parallel row before diagnostics");

        var lines = new ArrayList<>(List.of(tier, mode, parallel, thread, thread, diagnostic));

        canonical.invoke(null, lines, "gtceu.gui.machinemode",
                Component.translatable("gtceu.gui.machinemode", Component.translatable("gtl_enhancedcore.gui.iv_mode")));
        canonical.invoke(null, lines, "gtceu.multiblock.parallel",
                Component.translatable("gtceu.multiblock.parallel", 128));
        canonical.invoke(null, lines, "gtladditions.multiblock.threads",
                Component.translatable("gtladditions.multiblock.threads", 192));
        check(lines.getFirst() == tier && lines.contains(diagnostic),
                "upstream recipe tier and machine-specific diagnostics survive row replacement");
        check(count(lines, "gtceu.gui.machinemode") == 1
                        && count(lines, "gtceu.multiblock.parallel") == 1
                        && count(lines, "gtladditions.multiblock.threads") == 1,
                "each capacity or mode line is shown exactly once");
        check(((TranslatableContents) lines.get(3).getContents()).getArgs()[0].equals(192),
                "the displayed thread value is replaced with the isolation value");

        canonical.invoke(null, lines, "gtceu.multiblock.parallel", null);
        check(count(lines, "gtceu.multiblock.parallel") == 0,
                "an upstream parallel row is hidden when actual parallel is one");
        canonical.invoke(null, lines, "gtceu.multiblock.parallel",
                Component.translatable("gtceu.multiblock.parallel", 2));
        check(count(lines, "gtceu.multiblock.parallel") == 1,
                "a missing upstream row is supplied once when parallel becomes available");

        var summary = new CompoundTag();
        summary.putLong("parallelHatch", 128);
        summary.putInt("threads", 192);
        summary.putLong("budget", 24576);
        summary.putInt("active", 1);
        summary.putInt("count", 2);
        summary.putString("remaining", "10");
        summary.putString("running", "1");
        IvPresentation.append(lines, summary, false);
        check(count(lines, "gtl_enhancedcore.gui.iv_queue") == 1
                        && count(lines, "gtl_enhancedcore.gui.iv_remaining") == 1,
                "isolation order information remains visible");
        check(count(lines, "gtl_enhancedcore.gui.iv_budget") == 0
                        && count(lines, "gtl_enhancedcore.gui.iv_native_budget") == 0,
                "the order section does not repeat upstream parallel and thread rows");
        return assertions;
    }

    private static long count(List<Component> lines, String key) {
        return lines.stream().filter(line -> line.getContents() instanceof TranslatableContents translated
                && key.equals(translated.getKey())).count();
    }

    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
}
