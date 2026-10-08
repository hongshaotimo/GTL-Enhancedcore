package com.gtl.enhancedcore.audit;

import com.gtl.enhancedcore.common.util.PlayerTooltipStyles;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class PlayerTooltipStyleChecks {
    private static final Set<Integer> RAINBOW_COLORS = Set.of(
            ChatFormatting.RED.getColor(), ChatFormatting.GOLD.getColor(), ChatFormatting.YELLOW.getColor(),
            ChatFormatting.GREEN.getColor(), ChatFormatting.AQUA.getColor(), ChatFormatting.BLUE.getColor(),
            ChatFormatting.LIGHT_PURPLE.getColor());

    private PlayerTooltipStyleChecks() {}

    public static int verify(String translationKey) {
        Component first = PlayerTooltipStyles.rainbow(translationKey);
        Component second = PlayerTooltipStyles.rainbow(translationKey);
        int checks = verifyComponent(translationKey, first) + verifyComponent(translationKey, second);
        require(first != second, "Rainbow components must not share a cached mutable instance: " + translationKey);
        return checks + 1;
    }

    public static int verifyAnimation(String translationKey, List<Component> samples) {
        require(samples.size() >= 2, "Rainbow animation needs multiple timed tooltip samples: " + translationKey);
        var colors = new HashSet<Integer>();
        int checks = 1;
        for (Component sample : samples) {
            checks += verifyComponent(translationKey, sample);
            colors.add(sample.getStyle().getColor().getValue());
        }
        require(colors.size() > 1, "Rainbow color did not advance between tooltip samples: " + translationKey);
        return checks + 1;
    }

    private static int verifyComponent(String translationKey, Component component) {
        if (!(component.getContents() instanceof TranslatableContents contents)) {
            throw new AssertionError("Rainbow text must retain TranslatableContents: " + translationKey);
        }
        require(contents.getKey().equals(translationKey), "Rainbow translation key changed: " + translationKey);
        require(contents.getArgs().length == 0, "Unexpected rainbow translation arguments: " + translationKey);
        require(component.getSiblings().isEmpty(), "Unexpected literal or per-character rainbow segments: " + translationKey);
        require(Language.getInstance().has(translationKey), "Missing loaded rainbow translation: " + translationKey);
        var color = component.getStyle().getColor();
        require(color != null, "Missing rainbow color: " + translationKey);
        require(RAINBOW_COLORS.contains(color.getValue()), "Color differs from the native GTLAdditions palette: " + translationKey);
        require(component.getStyle().equals(Style.EMPTY.withColor(color)),
                "Rainbow factory introduced a non-color style: " + translationKey);
        require(component.getString().equals(Component.translatable(translationKey).getString()),
                "Rainbow text differs from the active translation: " + translationKey);
        return 9;
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
