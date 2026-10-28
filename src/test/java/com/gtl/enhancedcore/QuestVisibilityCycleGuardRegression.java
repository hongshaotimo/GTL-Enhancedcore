package com.gtl.enhancedcore;

import com.gtl.enhancedcore.integration.ftbquests.QuestVisibilityCycleGuard;

final class QuestVisibilityCycleGuardRegression {
    private QuestVisibilityCycleGuardRegression() {}

    static int run() {
        Object first = new Object();
        Object second = new Object();
        boolean cycleResult = QuestVisibilityCycleGuard.evaluate(first,
                () -> QuestVisibilityCycleGuard.evaluate(second,
                        () -> QuestVisibilityCycleGuard.evaluate(first, () -> true)));
        check(!cycleResult, "dependency cycle should resolve as not visible");
        check(QuestVisibilityCycleGuard.evaluate(first, () -> true), "guard state should clear after traversal");
        return 2;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
