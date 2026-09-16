package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

public final class RetryExecutor {

    private RetryExecutor() { }

    public static StageHandler withBoundedRetry(StageHandler handler, int maxRetries, long backoffMillis) {
        return withBoundedRetry(handler, maxRetries, backoffMillis, attempt -> { });
    }

    // Independent review amendments item 5: lets a caller (WorkflowEngine) observe each retry as it
    // happens — e.g. to persist a retry counter — without duplicating this class's retry/backoff
    // logic. Called only when a retry is actually about to occur (never on the final, exhausting
    // failure), matching "retries actually performed" semantics.
    public static StageHandler withBoundedRetry(StageHandler handler, int maxRetries, long backoffMillis,
                                                 IntConsumer onRetry) {
        return withBoundedRetry(handler, maxRetries, backoffMillis, onRetry, RetryExecutor::sleep);
    }

    // Independent review amendments item 5: a pluggable sleep function is the seam that makes backoff
    // deterministically testable — a test can pass a recording LongConsumer instead of a real
    // Thread.sleep, proving the exact backoff values RetryExecutor computed without any
    // wall-clock-based assertion (which would be inherently flaky under load).
    public static StageHandler withBoundedRetry(StageHandler handler, int maxRetries, long backoffMillis,
                                                 IntConsumer onRetry, LongConsumer sleepFn) {
        return ctx -> {
            RuntimeException lastException = null;
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                try {
                    return handler.run(ctx);
                } catch (PermanentStageException e) {
                    throw e;
                } catch (TransientStageException e) {
                    lastException = e;
                    if (attempt < maxRetries) {
                        onRetry.accept(attempt);
                        sleepFn.accept(backoffMillis * (1L << attempt));
                        continue;
                    }
                    throw e;
                }
            }
            throw lastException; // unreachable, satisfies the compiler's flow analysis
        };
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
