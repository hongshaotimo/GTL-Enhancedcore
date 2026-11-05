package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.CloudDispatchShares;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Arithmetic and routing properties independent of Forge, provider enumeration or tick order. */
public final class CloudDispatchSharesRegression {
    private static int assertions;
    private CloudDispatchSharesRegression() {}
    public static int run() {
        assertions = 0;
        var empty = List.of(BigInteger.ZERO, BigInteger.ZERO);
        var equal = List.of(0, 0);
        check(CloudDispatchShares.share(256, empty, equal, 0) == 128, "First of two empty providers accepts half the whole pattern order");
        var halfAssigned = List.of(BigInteger.valueOf(128), BigInteger.ZERO);
        check(CloudDispatchShares.share(128, halfAssigned, equal, 0) == 0,
                "Provider already holding its 128-share yields instead of repeatedly consuming one more pattern");
        check(CloudDispatchShares.share(128, halfAssigned, equal, 1) == 128,
                "Unloaded peer receives all 128 remaining repetitions after the first half is accepted");
        var scaleEight = List.of(CloudDispatchShares.repetitions(1000, 24, 8), BigInteger.ZERO);
        check(scaleEight.equals(halfAssigned) && CloudDispatchShares.share(128, scaleEight, equal, 0) == 0
                        && CloudDispatchShares.share(128, scaleEight, equal, 1) == 128,
                "Frozen scale eight produces the same routing decision as 128 logical pattern repetitions");
        check(CloudDispatchShares.repetitions(7, 0, 8).equals(BigInteger.ONE)
                        && CloudDispatchShares.repetitions(7, 2, 8).equals(BigInteger.TWO),
                "Partially active logical patterns round upward once after remaining and active operations are combined");
        check(CloudDispatchShares.repetitions(0, 0, Long.MAX_VALUE).signum() == 0,
                "Completed work contributes no routing backlog at any positive frozen scale");
        check(CloudDispatchShares.repetitions(Long.MAX_VALUE, Long.MAX_VALUE, 1)
                        .equals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO)),
                "Remaining plus active arithmetic never saturates or overflows long");
        var priorities = List.of(0, 3);
        check(CloudDispatchShares.share(100, empty, priorities, 0) == 20
                        && CloudDispatchShares.share(100, empty, priorities, 1) == 80,
                "Relative priority difference three gives weighted 1-to-4 admission without disabling either provider");
        check(CloudDispatchShares.share(100, empty, List.of(-400, -397), 1) == 80,
                "Adding the same priority offset does not change routing weights");
        check(CloudDispatchShares.share(Long.MAX_VALUE, empty, List.of(Integer.MIN_VALUE, Integer.MAX_VALUE), 0) > 0
                        && CloudDispatchShares.share(Long.MAX_VALUE, empty, List.of(Integer.MIN_VALUE, Integer.MAX_VALUE), 1) <= Long.MAX_VALUE,
                "Full signed priority range and long-max requests remain positive and representable");
        var huge = BigInteger.ONE.shiftLeft(160);
        check(CloudDispatchShares.share(Long.MAX_VALUE, List.of(huge, BigInteger.ZERO), equal, 0) == 0
                        && CloudDispatchShares.share(Long.MAX_VALUE, List.of(huge, BigInteger.ZERO), equal, 1) == Long.MAX_VALUE,
                "Huge existing backlog yields safely while an empty peer accepts up to the full requested long");
        check(CloudDispatchShares.share(0, empty, equal, 0) == 0 && CloudDispatchShares.share(-1, empty, equal, 0) == 0,
                "Empty requests never gain a routing allowance");
        rejects(() -> CloudDispatchShares.repetitions(-1, 0, 1), "Negative remaining work is rejected");
        rejects(() -> CloudDispatchShares.repetitions(0, -1, 1), "Negative active work is rejected");
        rejects(() -> CloudDispatchShares.repetitions(1, 0, 0), "Zero frozen scale is rejected");
        rejects(() -> CloudDispatchShares.repetitions(1, 0, -1), "Negative frozen scale is rejected");
        rejects(() -> CloudDispatchShares.share(1, List.of(BigInteger.valueOf(-1)), List.of(0), 0), "Negative peer backlog is rejected");
        rejects(() -> CloudDispatchShares.share(1, empty, List.of(0), 0), "Mismatched routing identities are rejected");
        rejects(() -> CloudDispatchShares.share(1, empty, equal, 2), "A caller outside the eligible routing group is rejected");
        properties();
        return assertions;
    }
    private static void properties() {
        var random = new Random(20261105L);
        for (int sample = 0; sample < 1200; sample++) {
            int count = 1 + random.nextInt(24);
            var pending = new ArrayList<BigInteger>(); var priorities = new ArrayList<Integer>();
            for (int i = 0; i < count; i++) {
                pending.add(new BigInteger(96, random)); priorities.add(random.nextInt());
            }
            long requested = sample % 3 == 0 ? Long.MAX_VALUE : 1L + random.nextInt(10000);
            boolean possible = false;
            for (int i = 0; i < count; i++) {
                long allowance = CloudDispatchShares.share(requested, pending, priorities, i);
                check(allowance >= 0 && allowance <= requested, "Every routing allowance stays inside the current physical request");
                possible |= allowance > 0;
                var largerOwn = new ArrayList<>(pending); largerOwn.set(i, largerOwn.get(i).add(BigInteger.ONE.shiftLeft(80)));
                check(CloudDispatchShares.share(requested, largerOwn, priorities, i) <= allowance,
                        "Adding already accepted work to this provider cannot increase its new allowance");
            }
            check(possible, "Any positive request has at least one positive allowance in its ready provider group");
            int own = random.nextInt(count);
            var reversedPending = new ArrayList<>(pending); Collections.reverse(reversedPending);
            var reversedPriorities = new ArrayList<>(priorities); Collections.reverse(reversedPriorities);
            check(CloudDispatchShares.share(requested, pending, priorities, own)
                            == CloudDispatchShares.share(requested, reversedPending, reversedPriorities, count - own - 1),
                    "Provider enumeration order does not change a provider's weighted entitlement");
        }
        for (long scale : new long[]{1, 2, 8, 128, 1_000_000}) for (long patterns : new long[]{1, 17, 128, 1_000_000}) {
            long operations = Math.multiplyExact(scale, patterns), active = operations / 3;
            check(CloudDispatchShares.repetitions(operations - active, active, scale).equals(BigInteger.valueOf(patterns)),
                    "All supported frozen scales preserve the original complete logical pattern quantity");
        }
    }
    public static void main(String[] args) { System.out.println("Cloud weighted routing assertions: " + run()); }
    private static void rejects(Runnable work, String reason) {
        boolean rejected = false; try { work.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, reason);
    }
    private static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }
}
