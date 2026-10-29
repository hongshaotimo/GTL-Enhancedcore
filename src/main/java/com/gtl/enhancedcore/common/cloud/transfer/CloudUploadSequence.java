package com.gtl.enhancedcore.common.cloud.transfer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Ordering only; a stage completes when its required durable writes really finished. */
public final class CloudUploadSequence {
    private CloudUploadSequence() {}
    public static CompletableFuture<Void> run(Executor server, Supplier<CompletableFuture<Void>> debit,
            Supplier<CompletableFuture<Void>> payload, Supplier<CompletableFuture<Void>> committed,
            Supplier<CompletableFuture<Void>> acknowledge) {
        return debit.get().thenComposeAsync(ignored -> payload.get(), server)
                .thenCompose(ignored -> committed.get()).thenComposeAsync(ignored -> acknowledge.get(), server);
    }
}
