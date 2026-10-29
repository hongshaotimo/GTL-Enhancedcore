package com.gtl.enhancedcore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Contract checks for alternating ordinary input-assembly and cloud-order lanes. */
final class CloudPassivePatternLaneRegression {
    static int run() {
        try {
            String buffers = Files.readString(Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvBuffers.java"));
            String mixin = Files.readString(Path.of(
                    "src/main/java/com/gtl/enhancedcore/mixin/gtceu/IvNativeRecipeLogicMixin.java"));
            check(buffers.contains("genericCloudPassivePatternBuffer")
                            && buffers.contains("runPassivePatternLane"),
                    "cloud machines expose a distinct ordinary input-assembly lane");
            check(mixin.contains("iv$passivePatternLane")
                            && mixin.contains("IvBuffers.runPassivePatternLane"),
                    "native recipe logic alternates passive input and cloud order ticks");
            check(mixin.contains("iv$clearPassiveLane"),
                    "the passive lane flag is cleared after the original recipe tick");
            return 3;
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
