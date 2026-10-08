package com.gtl.enhancedcore.common.util;

import com.gtladd.gtladditions.utils.CommonUtils;
import java.util.Objects;
import net.minecraft.network.chat.Component;

public final class PlayerTooltipStyles {
    private PlayerTooltipStyles() {}

    public static Component rainbow(String translationKey) {
        Objects.requireNonNull(translationKey, "translationKey");
        return CommonUtils.INSTANCE.createLanguageRainbowComponentOnServer(
                Component.translatable(translationKey));
    }
}
