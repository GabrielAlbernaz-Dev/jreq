package com.jreq.shared.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public interface AsyncTaskExecutor {
    <T> CompletableFuture<T> submit(Callable<T> task);
}
