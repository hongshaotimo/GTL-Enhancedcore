package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.CloudOrderCapacity;
import java.util.Map;

public final class CloudOrderCapacityRegression {
    public static int run() {
        int checks = 0;
        long max = Long.MAX_VALUE;
        check(CloudOrderCapacity.limit(max, 1, Map.of("input", 1L)) == max,
                "one recipe operation and one input remain long-max dispatchable"); checks++;
        check(CloudOrderCapacity.limit(max, 2, Map.of("input", 1L)) == max / 2,
                "recipe operations per pattern cap dispatch before operation multiplication overflows"); checks++;
        long half = max / 2;
        check(CloudOrderCapacity.limit(half, 2, Map.of("input", 2L)) == half,
                "reported half-long request is accepted exactly at both operation and input limits"); checks++;
        check(Math.multiplyExact(2, CloudOrderCapacity.limit(half, 2, Map.of("input", 2L))) == max - 1,
                "accepted half-long order produces an exact representable recipe operation count"); checks++;
        check(CloudOrderCapacity.limit(half + 1, 2, Map.of("input", 2L)) == half,
                "one above the half-long boundary is clipped before AE dispatch"); checks++;
        check(Math.multiplyExact(2, CloudOrderCapacity.limit(max, 2, Map.of("input", 1L))) == max - 1,
                "Long.MAX dispatch is reduced when one pattern represents two recipe operations"); checks++;
        check(CloudOrderCapacity.limit(max, 1, Map.of("input", 2L)) == half,
                "input material multiplication has an independent long boundary"); checks++;
        check(CloudOrderCapacity.limit(max, 3, Map.of("input", 1L)) == max / 3,
                "non-power-of-two recipe scale is bounded exactly"); checks++;
        check(CloudOrderCapacity.limit(1, 0, Map.of("input", 1L)) == 0
                        && CloudOrderCapacity.limit(1, 1, Map.of("input", 0L)) == 0,
                "invalid operation or input quantities fail closed"); checks++;
        var units = Map.of("item", 2L, "fluid", 1000L);
        long clipped = CloudOrderCapacity.limit(max, 2, units);
        check(clipped == max / 1000 && Math.multiplyExact(clipped, 1000) > 0,
                "mixed item and fluid dispatch is clipped before either input is multiplied"); checks++;
        long outputBounded = CloudOrderCapacity.limit(CloudOrderCapacity.limit(max, 1, Map.of("input", 1L)),
                1, Map.of("output", 7L));
        check(outputBounded == max / 7 && Math.multiplyExact(outputBounded, 7) <= max,
                "AE waiting-output quantities also remain exactly representable"); checks++;
        check(CloudOrderCapacity.refillAmount(7, 50, max) == max - 14,
                "after one full glue batch, seven remaining mB can be refilled without overflow"); checks++;
        check(CloudOrderCapacity.refillAmount(max - 7, 50, max) == 0,
                "an already funded batch does not pull extra fluid from ME"); checks++;
        check(CloudOrderCapacity.refillAmount(0, 1, max) == max,
                "single-unit input can refill the full long-valued batch"); checks++;
        long[] shares = com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.shares(clipped, 3, 0);
        var inventories = com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.stock(
                Map.of("item", Math.multiplyExact(clipped, 2), "fluid", Math.multiplyExact(clipped, 1000)), clipped, shares);
        for (int i = 0; i < shares.length; i++) {
            check(inventories.get(i).get("item") == Math.multiplyExact(shares[i], 2)
                            && inventories.get(i).get("fluid") == Math.multiplyExact(shares[i], 1000),
                    "clipped Long.MAX order keeps exact whole-pattern quantities for every shared executor"); checks++;
        }
        return checks;
    }

    public static void main(String[] args) { System.out.println("Cloud order capacity checks: " + run()); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
