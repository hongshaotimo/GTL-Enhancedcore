package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.CloudMeTargetPolicy;

public final class CloudMeTargetRegression {
    public static int run() {
        int checks = 0;
        check(CloudMeTargetPolicy.registered("gtl_enhancedcore", "cloud_pattern_library"), "central library is a native wireless target"); checks++;
        check(CloudMeTargetPolicy.registered("gtl_enhancedcore", "cloud_pattern_buffer"), "cloud buffer is a native wireless target"); checks++;
        check(!CloudMeTargetPolicy.registered("gtceu", "cloud_pattern_library"), "foreign namespace cannot gain cloud registration"); checks++;
        check(!CloudMeTargetPolicy.registered("gtl_enhancedcore", "cloud_pattern_library_other"), "similarly named machines are not opted in"); checks++;
        check(!CloudMeTargetPolicy.registered(null, null), "missing registration is not a target"); checks++;
        return checks;
    }
    public static void main(String[] args) { System.out.println("Cloud ME target checks: " + run()); }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
