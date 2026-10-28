package com.gtl.enhancedcore.common.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Encodes the weather anchor's mutually exclusive time and weather choices. */
public final class WeatherAnchorModes {
    public static final int DAY = 1;
    public static final int NIGHT = 1 << 1;
    public static final int CLEAR = 1 << 2;
    public static final int RAIN = 1 << 3;
    public static final int THUNDER = 1 << 4;
    private static final int TIME_MASK = DAY | NIGHT;
    private static final int WEATHER_MASK = CLEAR | RAIN | THUNDER;

    private WeatherAnchorModes() {}

    public static int toggle(int selected, int modeIndex) {
        if (modeIndex < 0 || modeIndex > 4) return selected;
        int bit = 1 << modeIndex;
        if ((selected & bit) != 0) return selected & ~bit;
        int axis = (bit & TIME_MASK) != 0 ? TIME_MASK : WEATHER_MASK;
        return (selected & ~axis) | bit;
    }

    public static boolean isValid(int selected) {
        return Integer.bitCount(selected & TIME_MASK) <= 1
                && Integer.bitCount(selected & WEATHER_MASK) <= 1
                && (selected & ~(TIME_MASK | WEATHER_MASK)) == 0;
    }

    public static Component describe(int selected) {
        MutableComponent result = Component.empty();
        boolean hasMode = false;
        if ((selected & DAY) != 0) hasMode = appendMode(result, hasMode, "gtl_enhancedcore.anchor_day");
        if ((selected & NIGHT) != 0) hasMode = appendMode(result, hasMode, "gtl_enhancedcore.anchor_night");
        if ((selected & CLEAR) != 0) hasMode = appendMode(result, hasMode, "gtl_enhancedcore.anchor_clear");
        if ((selected & RAIN) != 0) hasMode = appendMode(result, hasMode, "gtl_enhancedcore.anchor_rain");
        if ((selected & THUNDER) != 0) hasMode = appendMode(result, hasMode, "gtl_enhancedcore.anchor_thunder");
        return result;
    }

    private static boolean appendMode(MutableComponent result, boolean hasMode, String translationKey) {
        if (hasMode) result.append(" / ");
        result.append(Component.translatable(translationKey));
        return true;
    }
}
