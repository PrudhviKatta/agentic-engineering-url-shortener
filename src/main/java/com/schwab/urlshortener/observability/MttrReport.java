package com.schwab.urlshortener.observability;

public record MttrReport(
        Double mttrSeconds,
        int recoveredCount,
        int unrecoveredCount,
        int excludedCount,
        String populationNote
) { }
