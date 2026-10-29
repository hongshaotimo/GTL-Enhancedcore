package com.gtl.enhancedcore.common.recipe.iv;

import com.google.common.primitives.Ints;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gtladd.gtladditions.api.machine.IThreadModifierMachine;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import java.util.List;
import java.util.function.Consumer;
import java.math.BigInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic;

/** Final GUI projection, after virtual dispatch and all upstream/registration-specific display rows. */
public final class IvMachineDisplay {
    public static final String MODE_TRANSLATION = "gtl_enhancedcore.gui.iv_mode";
    public static final String MODE_ID = "gtl_enhancedcore:gui.iv_mode";

    private IvMachineDisplay() {}

    public static Component modeLine() {
        return Component.translatable("gtceu.gui.machinemode", Component.translatable(MODE_TRANSLATION))
                .withStyle(ChatFormatting.AQUA);
    }

    /** Preserve null on the client: it disables server-side text polling in the upstream widget. */
    public static Consumer<List<Component>> wrap(WorkableElectricMultiblockMachine machine,
            Consumer<List<Component>> upstream) {
        return upstream == null ? null : after(upstream, lines -> update(machine, lines));
    }

    public static Consumer<List<Component>> after(Consumer<List<Component>> upstream,
            Consumer<List<Component>> projection) {
        return lines -> {
            upstream.accept(lines);
            projection.accept(lines);
        };
    }

    private static void update(WorkableElectricMultiblockMachine machine, List<Component> text) {
        if (com.gtl.enhancedcore.common.machine.galaxy.GalaxyLinks.active(machine)) {
            var handler = com.gtl.enhancedcore.common.machine.galaxy.GalaxyWirelessEnergyHandler.forMachine(machine);
            var recipe = machine.getRecipeLogic().getLastRecipe();
            var balance = handler == null ? BigInteger.ZERO : handler.getMaxAvailableEnergy();
            stellarDisplay(text, balance, balance,
                    recipe != null && recipe.data.contains(com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.EU_TAG)
                            ? com.gtl.enhancedcore.common.machine.galaxy.GalaxyPower.eut(recipe) : null);
        }
        if (IvMachineScope.unlimited(machine)) {
            canonicalLine(text, "gtceu.multiblock.parallel", unlimitedLine("gtceu.multiblock.parallel"));
            canonicalLine(text, "gtladditions.multiblock.threads", unlimitedLine("gtladditions.multiblock.threads"));
        }
        if (!IvMachineScope.nativeTarget(machine) || !machine.isFormed()) return;
        if (!IvMachineScope.crossRecipeEnabled(machine)) {
            int threads = plainThreads(machine);
            if (!IvMachineScope.unlimited(machine))
                canonicalLine(text, "gtladditions.multiblock.threads", threads > 1 ? threadLine(threads) : null);
            text.add(Component.translatable("gtl_enhancedcore.tooltip.iv_native.1").withStyle(ChatFormatting.YELLOW));
            return;
        }
        isolation(text, IvMachineScope.parallel(machine), IvMachineScope.threads(machine),
                ((IvNativeAccess)machine.getRecipeLogic()).iv$summary());
    }

    /** The upstream capacity rows retain their position, gray labels and colored values. */
    public static void stellarDisplay(List<Component> text, BigInteger powerCap, BigInteger balance, BigInteger batchEut) {
        for (int i = 0; i < text.size(); i++) {
            if ("gtceu.multiblock.max_energy_per_tick".equals(key(text.get(i))))
                text.set(i, Component.translatable("gtl_enhancedcore.galaxy.max_power"));
        }
        canonicalLine(text, "gtl_enhancedcore.galaxy.max_power", Component.translatable(
                "gtl_enhancedcore.galaxy.max_power", energyValue(powerCap, ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
        canonicalLine(text, "gtceu.multiblock.max_recipe_tier", Component.translatable(
                "gtceu.multiblock.max_recipe_tier", Component.literal("MAX").withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
        canonicalLine(text, "gtl_enhancedcore.galaxy.power_cap", null);
        canonicalLine(text, "gtl_enhancedcore.galaxy.grid_balance", balance == null ? null : Component.translatable(
                "gtl_enhancedcore.galaxy.grid_balance", energyValue(balance, ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
        canonicalLine(text, "gtl_enhancedcore.galaxy.batch_power", batchEut == null ? null : Component.translatable(
                "gtl_enhancedcore.galaxy.batch_power", energyValue(batchEut, ChatFormatting.RED)).withStyle(ChatFormatting.GRAY));
    }

    private static Component energyValue(BigInteger amount, ChatFormatting color) {
        String label = amount.abs().compareTo(BigInteger.valueOf(1_000_000_000_000L)) >= 0
                ? FormattingUtil.DECIMAL_FORMAT_SIC.format(amount) : FormattingUtil.formatNumbers(amount);
        return Component.literal(label).withStyle(style -> style.withColor(color).withHoverEvent(
                new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        Component.literal(FormattingUtil.formatNumbers(amount)))));
    }

    /** GUI mode/capacity and order data all describe the same effective isolation engine. */
    public static void isolation(List<Component> text, int parallel, int threads, CompoundTag summary) {
        canonicalLine(text, "gtceu.gui.machinemode", modeLine());
        canonicalLine(text, "gtceu.multiblock.parallel", parallel > 1
                ? Component.translatable("gtceu.multiblock.parallel",
                        Component.literal(FormattingUtil.formatNumbers(parallel)).withStyle(ChatFormatting.DARK_PURPLE))
                        .withStyle(ChatFormatting.GRAY) : null);
        canonicalLine(text, "gtladditions.multiblock.threads", threads > 1 ? threadLine(threads) : null);
        if (summary.getBoolean("unlimited")) {
            canonicalLine(text, "gtceu.multiblock.parallel", unlimitedLine("gtceu.multiblock.parallel"));
            canonicalLine(text, "gtladditions.multiblock.threads", unlimitedLine("gtladditions.multiblock.threads"));
        }
        IvPresentation.append(text, summary, true);
    }
    private static Component unlimitedLine(String key) {
        return Component.translatable(key, com.gtl.enhancedcore.common.util.PlayerTooltipStyles.rainbow(
                "gtladditions.multiblock.forge_of_the_antichrist.parallel"))
                .withStyle(ChatFormatting.GRAY);
    }
    private static String key(Component line) {
        return line.getContents() instanceof TranslatableContents translated ? translated.getKey() : "";
    }
    /** Replace only known upstream rows, preserving their position and every machine-specific display line. */
    private static void canonicalLine(List<Component> text, String key, Component replacement) {
        boolean found = false;
        for (int i = 0; i < text.size();) {
            if (!key.equals(key(text.get(i)))) {
                i++;
            } else if (!found && replacement != null) {
                text.set(i++, replacement);
                found = true;
            } else {
                text.remove(i);
            }
        }
        if (!found && replacement != null) {
            int insertAt = text.size();
            if (key.equals("gtladditions.multiblock.threads")) {
                boolean anchored = false;
                for (String anchor : List.of("gtceu.multiblock.parallel", "gtceu.gui.machinemode",
                        "gtceu.multiblock.max_recipe_tier")) {
                    for (int i = 0; i < text.size(); i++) {
                        if (anchor.equals(key(text.get(i)))) {
                            insertAt = i + 1;
                            anchored = true;
                            break;
                        }
                    }
                    if (anchored) break;
                }
            }
            text.add(insertAt, replacement);
        }
    }
    private static Component threadLine(int threads) {
        return Component.translatable("gtladditions.multiblock.threads",
                Component.literal(FormattingUtil.formatNumbers(threads)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.GRAY);
    }
    private static int plainBatchThreads(int additionalThreads) {
        return Ints.saturatedCast((long)IvMachineScope.UPSTREAM_MULTIPLE_RECIPE_THREADS + additionalThreads);
    }
    /** Match the ordinary-mode branches of GTLAdditions' ParallelProviderMixin. */
    private static int plainThreads(WorkableElectricMultiblockMachine machine) {
        var logic = machine.getRecipeLogic();
        if (logic instanceof com.gtladd.gtladditions.api.machine.logic.GTLAddMultipleRecipesLogic add)
            return add.getMultipleThreads();
        if (logic instanceof MultipleRecipesLogic && machine instanceof IThreadModifierMachine modifier)
            return plainBatchThreads(modifier.getAdditionalThread());
        if (logic instanceof MutableRecipesLogic<?> mutable && mutable.isMultipleRecipeMode())
            return mutable.getMultipleThreads();
        return 0;
    }

}
