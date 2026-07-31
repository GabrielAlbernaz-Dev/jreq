package com.jreq.shared.concurrent;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutorServiceTaskExecutorTest {
    @Test
    void executesTasksOnTheConfiguredBackgroundThread() {
        try (ExecutorServiceTaskExecutor executor =
                     ExecutorServiceTaskExecutor.singleThread("jreq-task-test")) {
            String threadName = executor.submit(() -> Thread.currentThread().getName()).join();

            assertThat(threadName).isEqualTo("jreq-task-test");
        }
    }

    @Test
    void preservesRuntimeExceptionsAndWrapsCheckedExceptions() {
        try (ExecutorServiceTaskExecutor executor =
                     ExecutorServiceTaskExecutor.singleThread("jreq-task-failure-test")) {
            assertThatThrownBy(() -> executor.submit(() -> {
                throw new IllegalArgumentException("invalid");
            }).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> executor.submit(() -> {
                throw new IOException("unavailable");
            }).join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .cause()
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    @Test
    void rejectsBlankThreadNamesBeforeCreatingTheExecutor() {
        assertThatThrownBy(() -> ExecutorServiceTaskExecutor.singleThread("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("threadName is required");
    }
}
