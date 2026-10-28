package com.gtl.enhancedcore.common.cloud;

import java.math.BigInteger;
import java.util.*;

/** Split only not-yet-accepted complete pattern repetitions; never rebalance a paid job. */
public final class CloudDispatchFairness {
    private CloudDispatchFairness() {}
    public static <T> List<T> recipients(List<T> eligible, int queueSpace, int cursor) {
        if(queueSpace<1||eligible.isEmpty())return List.of();
        var result=new ArrayList<T>();
        int start=Math.floorMod(cursor,eligible.size());
        for(int i=0;i<Math.min(queueSpace,eligible.size());i++)result.add(eligible.get((start+i)%eligible.size()));
        return List.copyOf(result);
    }
    /** Once inputs are owned, telemetry failure must never turn the AE acknowledgment into a retry. */
    public static boolean acknowledged(Runnable committedTelemetry) {
        try { committedTelemetry.run(); } catch(Throwable telemetryFailure) { /* Commit is already durable-owned. */ }
        return true;
    }
    public static long[] shares(long repetitions, int executors, int cursor) {
        if (repetitions < 1 || executors < 1) throw new IllegalArgumentException("Empty dispatch");
        long[] result = new long[executors];
        Arrays.fill(result, repetitions / executors);
        for (int i=0;i<repetitions%executors;i++) result[Math.floorMod(cursor+i,executors)]++;
        return result;
    }
    public static <K> List<Map<K,Long>> stock(Map<K,Long> received, long repetitions, long[] shares) {
        long sum=0;for(long share:shares){if(share<0)throw new IllegalArgumentException("Negative share");sum=Math.addExact(sum,share);}
        if(sum!=repetitions)throw new IllegalArgumentException("Dispatch share mismatch");
        var output=new ArrayList<Map<K,Long>>();for(long ignored:shares)output.add(new LinkedHashMap<>());
        for(var entry:received.entrySet()){
            if(entry.getValue()<0)throw new IllegalArgumentException("Negative stock");
            long assigned=0;
            for(int i=0;i<shares.length;i++){
                long count=BigInteger.valueOf(entry.getValue()).multiply(BigInteger.valueOf(shares[i]))
                        .divide(BigInteger.valueOf(repetitions)).longValueExact();
                if(count>0)output.get(i).put(entry.getKey(),count);assigned=Math.addExact(assigned,count);
            }
            // Indivisible nonconsumed circuit tokens remain physical in exactly one partition.
            long remainder=entry.getValue()-assigned;
            for(int i=0;remainder>0&&i<shares.length;i++)if(shares[i]>0){
                output.get(i).merge(entry.getKey(),remainder,Math::addExact);remainder=0;
            }
        }
        return output;
    }
}
