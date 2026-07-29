package com.jreq.shared.concurrent;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ExecutorServiceTaskExecutor implements AsyncTaskExecutor, AutoCloseable {
    private final ExecutorService executorService;

    public ExecutorServiceTaskExecutor(ExecutorService executorService) {
        this.executorService = Objects.requireNonNull(executorService, "executorService");
    }

    public static ExecutorServiceTaskExecutor singleThread(String threadName) {
        String validThreadName = Objects.requireNonNull(threadName, "threadName").strip();
        if (validThreadName.isEmpty()) {
            throw new IllegalArgumentException("threadName is required");
        }
        return new ExecutorServiceTaskExecutor(Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, validThreadName);
            thread.setDaemon(true);
            return thread;
        }));
    }

    @Override
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        return CompletableFuture.supplyAsync(() -> call(task), executorService);
    }

    @Override
    public void close() {
        executorService.shutdown();
    }

    private <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Asynchronous task failed", exception);
        }
    }
}
