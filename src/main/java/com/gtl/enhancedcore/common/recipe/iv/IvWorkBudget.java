package com.gtl.enhancedcore.common.recipe.iv;

import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public final class IvWorkBudget {
    public static final int DELIVERY_JOBS_PER_TICK = 16;
    public static final int TRANSFERS_PER_JOB = 4;

    private IvWorkBudget() {}

    public static <T> Set<T> deliveries(Collection<T> pending, FairRecipeSelector<T> selector, Function<T, String> identity) {
        return selector.select(pending, DELIVERY_JOBS_PER_TICK, identity);
    }
}
