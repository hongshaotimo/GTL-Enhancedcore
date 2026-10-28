package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvPresentation;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineDisplay;
import com.gtl.enhancedcore.common.recipe.iv.IvMachineScope;
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
        assertions = 0;
        Method canonical = IvMachineDisplay.class.getDeclaredMethod(
                "canonicalLine", List.class, String.class, Component.class);
        canonical.setAccessible(true);
        Method plainBatch = IvMachineDisplay.class.getDeclaredMethod("plainBatchThreads", int.class);
        plainBatch.setAccessible(true);
        Method threadLine = IvMachineDisplay.class.getDeclaredMethod("threadLine", int.class);
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

        var stellar = new ArrayList<Component>(List.of(tier, diagnostic,
                Component.translatable("gtl_enhancedcore.galaxy.power_cap", "MAX+16", "old")));
        var balance = java.math.BigInteger.TEN.pow(52);
        var cap = balance;
        IvMachineDisplay.stellarDisplay(stellar, cap, balance, java.math.BigInteger.TEN.pow(18));
        IvMachineDisplay.stellarDisplay(stellar, cap, balance, java.math.BigInteger.TEN.pow(18));
        check(count(stellar, "gtceu.multiblock.max_energy_per_tick") == 1
                        && count(stellar, "gtceu.multiblock.max_recipe_tier") == 1
                        && count(stellar, "gtl_enhancedcore.galaxy.grid_balance") == 1
                        && count(stellar, "gtl_enhancedcore.galaxy.power_cap") == 0
                        && stellar.contains(diagnostic),
                "stellar UI uses one upstream power/tier row and preserves machine-specific content");
        var powerLine = stellar.stream().filter(line -> line.getContents() instanceof TranslatableContents t
                && t.getKey().equals("gtceu.multiblock.max_energy_per_tick")).findFirst().orElseThrow();
        var powerArgs = ((TranslatableContents)powerLine.getContents()).getArgs();
        check(((Component)powerArgs[0]).getStyle().getHoverEvent()
                        .getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT).getString()
                        .equals(com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(balance)),
                "stellar maximum power retains the entire grid balance beyond long and the former tier cap");
        var balanceLine = stellar.stream().filter(line -> line.getContents() instanceof TranslatableContents t
                && t.getKey().equals("gtl_enhancedcore.galaxy.grid_balance")).findFirst().orElseThrow();
        var balanceValue = (Component)((TranslatableContents)balanceLine.getContents()).getArgs()[0];
        check(balanceLine.getStyle().getColor().getValue() == net.minecraft.ChatFormatting.GRAY.getColor()
                        && balanceValue.getString().length() < 14
                        && balanceValue.getStyle().getHoverEvent().getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT)
                                .getString().equals(com.gregtechceu.gtceu.utils.FormattingUtil.formatNumbers(balance)),
                "stellar balance has native gray label, compact number and exact hover amount");

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

        check(IvMachineDisplay.wrap(null, null) == null,
                "the client null polling supplier remains null instead of activating an extra refresh path");
        check(IvMachineDisplay.MODE_ID.replace(':', '.').equals(IvMachineDisplay.MODE_TRANSLATION),
                "Jade's synthetic recipe mode and GUI translate exactly the same mode name");
        var ids = new ArrayList<>(IvMachineScope.NATIVE_IDS);
        IvMachineScope.FOREIGN_NATIVE_IDS.values().forEach(ids::addAll);
        for (String id : ids) {
            int expectedThreads = IvMachineScope.baseThreadsFor(id);
            var rendered = new ArrayList<Component>();
            // Model an override which never calls super and appends another stale mode after its base rows.
            var callback = IvMachineDisplay.after(text -> {
                text.add(tier);
                text.add(Component.translatable("gtceu.gui.machinemode", "lathe"));
                text.add(Component.translatable("gtceu.multiblock.parallel", 1000));
                text.add(Component.translatable("gtladditions.multiblock.threads", 64));
                text.add(diagnostic);
                text.add(Component.translatable("gtceu.gui.machinemode", "late subclass mode"));
            }, text -> IvMachineDisplay.isolation(text, 128, expectedThreads, summary));
            callback.accept(rendered);
            check(count(rendered, "gtceu.gui.machinemode") == 1
                            && count(rendered, "gtladditions.multiblock.threads") == 1
                            && count(rendered, "gtl_enhancedcore.gui.iv_queue") == 1,
                    id + ": final projection handles complete overrides and late duplicate rows once");
            var modeContents = (TranslatableContents) rendered.get(1).getContents();
            check(modeContents.getArgs()[0] instanceof Component name
                            && name.getContents() instanceof TranslatableContents translated
                            && translated.getKey().equals(IvMachineDisplay.MODE_TRANSLATION),
                    id + ": GUI reports the active isolation mode rather than the original recipe type");
            var displayedThreads = rendered.stream().filter(line -> line.getContents() instanceof TranslatableContents t
                    && t.getKey().equals("gtladditions.multiblock.threads")).findFirst().orElseThrow();
            Object argument = ((TranslatableContents) displayedThreads.getContents()).getArgs()[0];
            check(argument instanceof Component n && n.getString().equals(IvPresentation.number(expectedThreads))
                            && rendered.contains(tier) && rendered.contains(diagnostic),
                    id + ": effective threads agree with the shared policy and machine diagnostics survive");
            // Subsequent server refresh re-evaluates the complete callback, without retaining the last list.
            rendered.clear();
            callback.accept(rendered);
            check(count(rendered, "gtceu.gui.machinemode") == 1
                            && count(rendered, "gtl_enhancedcore.gui.iv_remaining") == 1,
                    id + ": live refresh uses the same projection as initial text");
        }
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
