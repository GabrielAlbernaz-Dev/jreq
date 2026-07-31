package com.jreq.shared.concurrent;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public interface AsyncTaskExecutor {
    <T> CompletableFuture<T> submit(Callable<T> task);

    static AsyncTaskExecutor direct() {
        return new AsyncTaskExecutor() {
            @Override
            public <T> CompletableFuture<T> submit(Callable<T> task) {
                Objects.requireNonNull(task, "task");
                try {
                    return CompletableFuture.completedFuture(task.call());
                } catch (Exception failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }
        };
    }
}
