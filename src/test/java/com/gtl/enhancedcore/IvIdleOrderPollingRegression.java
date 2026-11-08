package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.iv.IvIdleOrderPolling;

final class IvIdleOrderPollingRegression {
    private static int assertions;

    static int run() {
        assertions = 0;
        var polling = new IvIdleOrderPolling();
        int fullTicks = 0;
        for (long tick = 7; tick < 1207; tick++) {
            boolean run = polling.shouldRun(tick, true, true);
            if (run) fullTicks++;
            check(run == ((tick - 7) % 20 == 0), "Empty polling follows elapsed ticks from its first full pass");
        }
        check(fullTicks == 60, "A clean empty engine runs maintenance sixty times in sixty seconds");
        check(polling.shouldRun(1207, false, true), "A newly pushed order bypasses empty throttling next tick");
        for (long tick = 1208; tick < 1248; tick++)
            check(polling.shouldRun(tick, false, true), "Every existing order keeps its processing and delivery ticks");
        check(polling.shouldRun(1248, true, true), "The first empty tick publishes the completed order's cleared state");
        check(!polling.shouldRun(1249, true, true), "A second empty tick avoids repeated collection and binding");
        check(polling.shouldRun(1250, true, false), "Pausing refreshes empty diagnostics immediately");
        check(!polling.shouldRun(1251, true, false), "Paused empty engines also have a bounded maintenance rate");
        check(polling.shouldRun(1252, true, true), "Resuming refreshes empty diagnostics immediately");
        check(polling.shouldRun(1253, false, false), "Paused orders keep their output and cancellation path");
        check(polling.shouldRun(1254, true, false), "An unavailable or unknown scan cannot carry an idle throttle forward");
        polling.reset();
        check(polling.shouldRun(1255, true, false), "Explicit mode, load or availability reset forces the next full pass");
        check(!polling.shouldRun(1255, true, false), "Repeated acceleration of an empty engine in the same tick is bounded");
        check(polling.shouldRun(10, true, false), "A timer rollback cannot retain a future deadline");
        polling.reset();
        check(polling.shouldRun(Long.MAX_VALUE - 1, true, true), "The maximum timer range accepts a first full pass");
        check(!polling.shouldRun(Long.MAX_VALUE, true, true), "One tick near overflow is still throttled");
        check(polling.shouldRun(Long.MIN_VALUE, true, true), "Timer wrap immediately refreshes the engine");
        check(!polling.shouldRun(Long.MIN_VALUE + 19, true, true), "Wrapped timers retain the normal interval");
        check(polling.shouldRun(Long.MIN_VALUE + 20, true, true), "Wrapped timers resume polling after twenty ticks");
        check(polling.shouldRun(Long.MAX_VALUE, true, true), "An elapsed subtraction overflow cannot suppress work");
        return assertions;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
