package com.schwab.urlshortener.observability;

import java.time.Instant;

public record RecoveryEvent(
        Instant failureDetectedAt,
        Instant recoveryStartAt,
        Instant recoveryCompleteAt,
        boolean recovered,
        String mechanism
) { }
