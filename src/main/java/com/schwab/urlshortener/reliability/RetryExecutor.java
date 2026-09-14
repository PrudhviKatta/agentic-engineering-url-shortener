package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;

public final class RetryExecutor {

    private RetryExecutor() { }

    public static StageHandler withBoundedRetry(StageHandler handler, int maxRetries, long backoffMillis) {
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
                        sleep(backoffMillis * (1L << attempt));
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
