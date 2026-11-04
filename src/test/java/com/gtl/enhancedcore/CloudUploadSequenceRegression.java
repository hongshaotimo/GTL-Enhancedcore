package com.gtl.enhancedcore;

import com.gtl.enhancedcore.common.cloud.transfer.CloudUploadSequence;
import java.util.*;
import java.util.concurrent.*;

/** Controllable disk completions prove no early ACK and no continuation after an I/O failure. */
public final class CloudUploadSequenceRegression {
    public static int run() {
        int checks = 0;
        for (int fail = -1; fail < 3; fail++) {
            var events = new ArrayList<String>();
            var debit = new CompletableFuture<Void>(); var payload = new CompletableFuture<Void>();
            var committed = new CompletableFuture<Void>();
            var done = CloudUploadSequence.run(Runnable::run, () -> { events.add("debit"); return debit; },
                    () -> { events.add("payload"); return payload; },
                    () -> { events.add("committed"); return committed; },
                    () -> { events.add("ACK"); return CompletableFuture.completedFuture(null); });
            check(events.equals(List.of("debit")) && !done.isDone(), "No server wait or premature ACK before debit durability"); checks++;
            var stages = List.of(debit, payload, committed);
            for (int phase = 0; phase < 3; phase++) {
                if (phase == fail) { stages.get(phase).completeExceptionally(new IllegalStateException("disk failure")); break; }
                stages.get(phase).complete(null);
                check(phase == 2 || !done.isDone(), "Success requires every durable stage"); checks++;
            }
            check(fail < 0 ? events.equals(List.of("debit", "payload", "committed", "ACK"))
                    : done.isCompletedExceptionally() && !events.contains("ACK"), "ACK only follows ordered durable success"); checks++;
        }
        var durability = new CompletableFuture<Void>(); var events = new ArrayList<String>();
        var visible = CloudUploadSequence.run(Runnable::run,
                () -> { events.add("debit"); return CompletableFuture.completedFuture(null); },
                () -> { events.add("payload"); return CompletableFuture.completedFuture(null); },
                () -> { events.add("committed"); return CompletableFuture.completedFuture(null); },
                () -> { events.add("ACK"); return durability; });
        check(events.equals(List.of("debit", "payload", "committed", "ACK")) && !visible.isDone(), "ACK publishes before final durability completes"); checks++;
        durability.complete(null); check(visible.isDone() && !visible.isCompletedExceptionally(), "Final durability completion releases upload future"); checks++;
        var failedDurability = new CompletableFuture<Void>(); var confirmed = new ArrayList<String>();
        var failed = CloudUploadSequence.run(Runnable::run, () -> CompletableFuture.completedFuture(null),
                () -> CompletableFuture.completedFuture(null), () -> CompletableFuture.completedFuture(null),
                () -> { confirmed.add("ACK"); return failedDurability; });
        failedDurability.completeExceptionally(new IllegalStateException("final disk failure"));
        check(failed.isCompletedExceptionally() && confirmed.equals(List.of("ACK")),
                "Final checkpoint failure propagates without replaying or rolling back confirmed ACK"); checks++;
        return checks;
    }
    public static void main(String[] args) { System.out.println("Cloud upload sequence checks: " + run()); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
