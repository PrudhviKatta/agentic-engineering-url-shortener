package com.schwab.urlshortener.reliability;

import com.schwab.urlshortener.orchestration.StageHandler;
import java.util.Map;
import java.util.concurrent.*;

public final class TimeoutExecutor {

    private TimeoutExecutor() { }

    public static StageHandler withTimeout(StageHandler handler, long timeoutMillis) {
        return ctx -> {
            // Review finding: shutdownNow() interrupts the worker thread but can't force-kill it —
            // a handler stuck in a non-interruptible operation (a blocking call with no read
            // timeout, a tight CPU loop) keeps running in the background after this method has
            // already thrown StageTimeoutException. That leak is an inherent limit of Java's
            // cooperative-interrupt model, not fixable here without redesigning every StageHandler
            // to be interrupt-aware. A daemon thread factory at least ensures a leaked worker never
            // blocks JVM shutdown, which the default (non-daemon) factory would otherwise do.
            ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "stage-timeout-worker");
                t.setDaemon(true);
                return t;
            });
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
