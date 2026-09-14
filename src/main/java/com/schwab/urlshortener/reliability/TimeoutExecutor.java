package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.Map;
import java.util.concurrent.*;

public final class TimeoutExecutor {

    private TimeoutExecutor() { }

    public static StageHandler withTimeout(StageHandler handler, long timeoutMillis) {
        return ctx -> {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Map<String, Object>> future = pool.submit(() -> handler.run(ctx));
                return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                throw new StageTimeoutException("stage exceeded " + timeoutMillis + "ms");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) throw re;
                throw new RuntimeException(cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                pool.shutdownNow();
            }
        };
    }
}
