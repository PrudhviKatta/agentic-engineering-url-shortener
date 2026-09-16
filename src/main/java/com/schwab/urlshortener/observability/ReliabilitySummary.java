package com.schwab.urlshortener.observability;

public record ReliabilitySummary(
        double successRate,
        double failureRate,
        double retryFrequency,
        double rollbackOrCompensationFrequency,
        double fallbackFrequency,
        Double endToEndLatencySeconds,
        MttrReport mttr,
        boolean isDemonstrationData
) { }
