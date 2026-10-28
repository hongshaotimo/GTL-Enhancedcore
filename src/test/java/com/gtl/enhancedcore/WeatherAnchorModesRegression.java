package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.WeatherAnchorModes;

final class WeatherAnchorModesRegression {
    static int run() {
        int selected = WeatherAnchorModes.toggle(0, 0);
        selected = WeatherAnchorModes.toggle(selected, 2);
        check(selected == (WeatherAnchorModes.DAY | WeatherAnchorModes.CLEAR), "day and clear can coexist");
        selected = WeatherAnchorModes.toggle(selected, 4);
        check(selected == (WeatherAnchorModes.DAY | WeatherAnchorModes.THUNDER), "selecting weather replaces weather only");
        selected = WeatherAnchorModes.toggle(selected, 1);
        check(selected == (WeatherAnchorModes.NIGHT | WeatherAnchorModes.THUNDER), "night replaces day only");
        selected = WeatherAnchorModes.toggle(selected, 1);
        check(selected == WeatherAnchorModes.THUNDER, "selected time mode can be disabled");
        selected = WeatherAnchorModes.toggle(selected, 4);
        check(selected == 0 && WeatherAnchorModes.isValid(selected), "selected weather mode can be disabled");
        check(!WeatherAnchorModes.isValid(WeatherAnchorModes.DAY | WeatherAnchorModes.NIGHT), "opposing time modes conflict");
        check(!WeatherAnchorModes.isValid(WeatherAnchorModes.CLEAR | WeatherAnchorModes.RAIN), "weather modes conflict");
        return 7;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
