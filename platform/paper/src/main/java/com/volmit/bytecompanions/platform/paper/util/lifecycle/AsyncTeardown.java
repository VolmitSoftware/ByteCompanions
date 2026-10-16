package com.volmit.bytecompanions.platform.paper.util.lifecycle;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class AsyncTeardown {
    private AsyncTeardown() {
    }

    public static CompletableFuture<Void> run(List<? extends Supplier<CompletableFuture<Void>>> stages) {
        List<? extends Supplier<CompletableFuture<Void>>> snapshot = List.copyOf(stages);
        Failures failures = new Failures();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Supplier<CompletableFuture<Void>> stage : snapshot) {
            chain = chain.thenCompose(ignored -> attempt(stage).handle((value, failure) -> {
                failures.add(failure);
                return null;
            }));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        chain.whenComplete((ignored, failure) -> {
            failures.add(failure);
            if (failures.first == null) {
                result.complete(null);
            } else {
                result.completeExceptionally(failures.first);
            }
        });
        return result;
    }

    private static CompletableFuture<Void> attempt(Supplier<CompletableFuture<Void>> stage) {
        try {
            return Objects.requireNonNull(stage.get(), "Teardown stage returned no completion future");
        } catch (Exception | LinkageError failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static final class Failures {
        private Throwable first;

        private void add(Throwable failure) {
            if (failure == null) {
                return;
            }
            while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                    && failure.getCause() != null && failure.getCause() != failure) {
                failure = failure.getCause();
            }
            if (first == null) {
                first = failure;
            } else if (first != failure) {
                first.addSuppressed(failure);
            }
        }
    }
}
