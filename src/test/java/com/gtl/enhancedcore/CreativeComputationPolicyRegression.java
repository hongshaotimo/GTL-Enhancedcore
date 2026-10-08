package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.machine.hatch.CreativeComputationPolicy;
import java.util.List;

/** Checks the numeric limits used by the receiver without loading Forge machines headlessly. */
final class CreativeComputationPolicyRegression {

    private CreativeComputationPolicyRegression() {}

    static int run() {
        int checked = 0;
        int max = CreativeComputationPolicy.MAX_CWU_PER_TICK;
        require(max == 2_100_000_000, "creative computation uses the requested CWU/t limit"); checked++;
        for (int requested : new int[]{-1, 0, 1, 20, max - 1, max, Integer.MAX_VALUE}) {
            int expected = Math.min(Math.max(requested, 0), max);
            require(CreativeComputationPolicy.suppliedCWUt(requested, true) == expected,
                    "formed receiver clamps the CWU/t request " + requested); checked++;
            require(CreativeComputationPolicy.suppliedCWUt(requested, false) == 0,
                    "loose receiver cannot supply CWU/t " + requested); checked++;
        }
        require(CreativeComputationPolicy.requiredCWUt(List.of(1_000_000_000, 1_100_000_000)) == max,
                "multiple recipe entries can use the full capacity"); checked++;
        require(CreativeComputationPolicy.requiredCWUt(List.of(2_000_000_000, 200_000_000)) == 2_200_000_000L,
                "multiple recipe entries cannot overflow an int sum"); checked++;
        require(CreativeComputationPolicy.requiredCWUt(List.of(-1, 20)) == -1,
                "negative computation input is invalid"); checked++;
        require(CreativeComputationPolicy.progressBeforeTickIncrement(100, Integer.MAX_VALUE, max)
                        == 2_100_000_099,
                "total-CWU recipe keeps GTCEu's progress - 1 + supplied rule"); checked++;
        require(CreativeComputationPolicy.progressBeforeTickIncrement(100_000_000, Integer.MAX_VALUE, max)
                        == Integer.MAX_VALUE - 1,
                "total-CWU progress cannot overflow at a long duration"); checked++;
        require(CreativeComputationPolicy.progressBeforeTickIncrement(100, 200, max) == 199,
                "total-CWU progress leaves one tick before recipe completion"); checked++;
        return checked;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
