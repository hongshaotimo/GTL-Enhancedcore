package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.recipe.RecipeRetryPolicy;
import com.gtl.enhancedcore.common.recipe.RecipePowerBudget;
import com.gtl.enhancedcore.common.recipe.BoundedRecipeAdmission;
import com.gtl.enhancedcore.common.recipe.iv.IvWorkBudget;
import com.gtl.enhancedcore.common.util.FairRecipeSelector;
import java.util.ArrayList;
import java.util.HashMap;

final class RecipeRecoveryRegression {
    private static int assertions;

    static int run() {
        int idleSearches = 0;
        for (long tick = 1; tick <= 1200; tick++) {
            if (RecipeRetryPolicy.shouldTick(tick, true, false, false)) idleSearches++;
            check(RecipeRetryPolicy.shouldTick(tick, false, true, false), "Running batches keep every processing tick");
            check(RecipeRetryPolicy.shouldTick(tick, true, false, true), "Inventory notifications bypass idle throttling");
        }
        check(idleSearches == 60, "Idle fallback searches once per second, not every tick");
        check(RecipeRetryPolicy.shouldTick(40, true, false, false), "A rejected condition is revisited without restart");
        check(!RecipeRetryPolicy.shouldTick(41, true, false, false), "Idle polling has a fixed work budget");
        check(RecipeRetryPolicy.shouldTick(Long.MAX_VALUE, true, true, false), "A paid output-waiting batch is never discarded");
        check(RecipeRetryPolicy.shouldTick(Long.MIN_VALUE, true, false, true), "Timer overflow does not mask a dirty recipe");
        check(RecipePowerBudget.power(8192, 4) == 32768, "Input voltage and amperage produce actual available power");
        check(RecipePowerBudget.power(Long.MAX_VALUE, 2) == Long.MAX_VALUE, "Extreme energy hatch power saturates safely");
        check(RecipePowerBudget.power(-1, Long.MAX_VALUE) == 0, "Invalid negative voltage cannot create positive power");
        check(RecipePowerBudget.add(Long.MAX_VALUE - 4, 5) == Long.MAX_VALUE, "Multiple energy hatches cannot wrap power negative");
        check(RecipePowerBudget.add(32, -64) == 32, "Invalid negative power does not reduce another hatch contribution");
        for (long requested = 0; requested <= 128; requested++) {
            for (long capacity = 0; capacity <= 128; capacity++) {
                long limit = capacity;
                int[] probes = {0};
                long accepted = BoundedRecipeAdmission.maximum(requested, parallel -> {
                    probes[0]++;
                    check(parallel > 0, "Zero parallel is never treated as a one-cycle recipe");
                    return parallel <= limit;
                });
                check(accepted == Math.min(requested, capacity), "Admission keeps only the jointly fitting cycles");
                check(probes[0] <= 8, "Small admission searches are bounded logarithmically");
            }
        }
        int[] largeProbes = {0};
        check(BoundedRecipeAdmission.maximum(Long.MAX_VALUE, parallel -> {
            largeProbes[0]++;
            return parallel <= Long.MAX_VALUE - 17;
        }) == Long.MAX_VALUE - 17, "Admission handles maximum long budgets without overflow");
        check(largeProbes[0] <= 64, "Extreme admission never loops through individual cycles");
        check(BoundedRecipeAdmission.maximum(-1, parallel -> { throw new AssertionError("Unexpected negative probe"); }) == 0,
                "Negative requests never scale or commit a recipe");
        var pending = new ArrayList<String>();
        var visits = new HashMap<String, Integer>();
        for (int index = 0; index < 1024; index++) pending.add(String.format("order-%04d", index));
        var deliverySelector = new FairRecipeSelector<String>();
        for (int tick = 0; tick < 128; tick++) {
            var selected = IvWorkBudget.deliveries(pending, deliverySelector, identity -> identity);
            check(selected.size() <= IvWorkBudget.DELIVERY_JOBS_PER_TICK, "Long queues obey the per-tick work bound");
            check(selected.size() * IvWorkBudget.TRANSFERS_PER_JOB <= 64, "AE transfers have one controller-wide bound");
            for (var identity : selected) visits.merge(identity, 1, Integer::sum);
        }
        check(visits.size() == pending.size(), "A blocked early order cannot starve later orders");
        check(visits.values().stream().allMatch(count -> count == 2), "Every queued order receives equal retry opportunities");
        return assertions;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
