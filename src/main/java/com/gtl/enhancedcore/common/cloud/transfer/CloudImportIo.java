package com.gtl.enhancedcore.common.cloud.transfer;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial durable-file worker. Callers must submit immutable snapshots, never live world state. */
public final class CloudImportIo {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "gtl-cloud-import-io"); thread.setDaemon(true); return thread;
    });
    private CloudImportIo() {}
    public static <T> CompletableFuture<T> submit(Callable<T> work) {
        Objects.requireNonNull(work); CompletableFuture<T> result = new CompletableFuture<>();
        try {
            WORKER.execute(() -> {
                try { result.complete(work.call()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException rejected) { result.completeExceptionally(rejected); }
        return result;
    }
    public static CompletableFuture<Void> run(Runnable work) {
        Objects.requireNonNull(work); return submit(() -> { work.run(); return null; });
    }
    /** Stop hook barrier: waits for already queued I/O, never for server-thread completion callbacks. */
    public static void drain() { submit(() -> null).join(); }
}
