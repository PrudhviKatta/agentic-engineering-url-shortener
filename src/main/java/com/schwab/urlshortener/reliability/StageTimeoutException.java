package com.schwab.urlshortener.reliability;

// Independent review amendments item 5: a timeout is a transient failure by nature — extending
// TransientStageException (rather than RuntimeException directly) means RetryExecutor's existing
// catch(TransientStageException) clause retries a timed-out attempt automatically, without needing
// a second, parallel catch clause wherever retry and timeout are composed.
public class StageTimeoutException extends TransientStageException {
    public StageTimeoutException(String message) { super(message); }
}
