package com.jreq.shared.concurrent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncTaskExecutorTest {
    @Test
    void directExecutorCompletesSuccessfulAndFailedTasksImmediately() {
        AsyncTaskExecutor executor = AsyncTaskExecutor.direct();

        assertThat(executor.submit(() -> "done")).isCompletedWithValue("done");
        assertThat(executor.submit(() -> {
            throw new Exception("failure");
        })).isCompletedExceptionally();
    }
}
