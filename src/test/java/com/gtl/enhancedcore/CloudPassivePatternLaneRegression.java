package com.gtl.enhancedcore;

import java.nio.file.Files;
import java.nio.file.Path;

/** Contract checks for native-input/cloud batch ownership; runtime fixtures verify actual execution. */
final class CloudPassivePatternLaneRegression {
    static int run() {
        try {
            String buffers = Files.readString(Path.of(
                    "src/main/java/com/gtl/enhancedcore/common/recipe/iv/IvBuffers.java"));
            String mixin = Files.readString(Path.of(
                    "src/main/java/com/gtl/enhancedcore/mixin/gtceu/IvNativeRecipeLogicMixin.java"));
            check(buffers.contains("genericCloudNativeInputs") && buffers.contains("getHandlerIO()")
                            && !buffers.contains("(tick & 1L)"),
                    "native inputs include stock buses and no longer alternate incomplete ticks");
            check(mixin.contains("@WrapMethod(method = \"serverTick\")")
                            && mixin.contains("iv$nativeInputsActive") && mixin.contains("finally"),
                    "a committed physical batch keeps one native executor until completion");
            check(mixin.contains("iv$finishingNativeInputs || iv$isCrossRecipeEnabled()")
                            && mixin.contains("iv$nativeOutputReady"),
                    "native completion only yields after output admission and blocks automatic next-batch input");
            return 3;
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
