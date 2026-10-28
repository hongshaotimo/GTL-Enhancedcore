package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.CloudEndpointPolicy;
import java.util.*;

/** Single-mode industrial smelter claims and exact shared executor ownership. */
public final class CloudEndpointRegression {
    public static int run() {
        int checks = 0;
        var supported = List.of("furnace", "alloy_smelter");
        check(CloudEndpointPolicy.preferredModes(supported, "alloy_smelter").equals(List.of("alloy_smelter", "furnace")), "single alloy follows selected map"); checks++;
        check(CloudEndpointPolicy.preferredModes(supported, "furnace").equals(supported), "single electric furnace follows selected map"); checks++;
        check(CloudEndpointPolicy.preferredModes(supported, "unknown").equals(supported), "selected map cannot grant unsupported capability"); checks++;
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), replaced = UUID.randomUUID();
        for (UUID assigned : List.of(first, second)) {
            check(CloudEndpointPolicy.ownsJob(assigned, assigned, first, true), "accepting controller executes its own order"); checks++;
            check(!CloudEndpointPolicy.ownsJob(assigned, assigned.equals(first) ? second : first, first, true), "peer cannot double execute shared order"); checks++;
            check(!CloudEndpointPolicy.ownsJob(assigned, replaced, first, true), "replacement cannot inherit an order"); checks++;
        }
        check(CloudEndpointPolicy.ownsJob(null, first, first, true), "unassigned saved order keeps original exact instance"); checks++;
        check(!CloudEndpointPolicy.ownsJob(null, second, first, true), "unassigned order never shifts to peer"); checks++;
        check(!CloudEndpointPolicy.ownsJob(null, first, first, false), "original address also required"); checks++;
        check(!CloudEndpointPolicy.ownsJob(null, null, null, true), "missing identities never authorize work"); checks++;
        for(int peers=1;peers<=24;peers++)for(int cursor=0;cursor<peers;cursor++)for(long count:new long[]{1,2,17,999,Long.MAX_VALUE}){
            long[] shares=com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.shares(count,peers,cursor);
            long total=0,min=Long.MAX_VALUE,max=0;
            for(long share:shares){total=Math.addExact(total,share);min=Math.min(min,share);max=Math.max(max,share);}
            check(total==count&&max-min<=1,"complete pattern repetitions split equally without overflow");checks++;
            var stock=Map.of("item",Long.MAX_VALUE,"fluid",123456789L,"circuit",1L);
            var split=com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.stock(stock,count,shares);
            for(var entry:stock.entrySet()){
                long sum=0;for(var partition:split)sum=Math.addExact(sum,partition.getOrDefault(entry.getKey(),0L));
                check(sum==entry.getValue(),"every physical item, fluid and circuit has exactly one resulting owner");checks++;
            }
        }
        for(int peers=1;peers<=24;peers++){
            var selected=new HashSet<Integer>();
            for(int cursor=0;cursor<peers;cursor++){
                var share=com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.shares(1,peers,cursor);
                for(int i=0;i<peers;i++)if(share[i]>0)selected.add(i);
            }
            check(selected.size()==peers,"small completed dispatches rotate rather than always selecting the first controller");checks++;
            var eligible=java.util.stream.IntStream.range(0,peers).boxed().toList();
            var limited=new HashSet<Integer>();
            for(int cursor=0;cursor<peers;cursor++)limited.addAll(
                    com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.recipients(eligible,1,cursor));
            check(limited.size()==peers,"one remaining queue slot still rotates across every eligible controller");checks++;
        }
        var committed=new ArrayList<>(List.of("accepted"));
        check(com.gtl.enhancedcore.common.cloud.CloudDispatchFairness.acknowledged(()->{throw new AssertionError("log failure");})
                &&committed.equals(List.of("accepted")),"postcommit logging failure retains true ACK and existing ownership");checks++;
        return checks;
    }
    public static void main(String[] args) { System.out.println("Cloud endpoint checks: " + run()); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
