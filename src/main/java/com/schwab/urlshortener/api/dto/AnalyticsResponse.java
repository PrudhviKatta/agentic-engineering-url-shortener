package com.schwab.urlshortener.api.dto;

import java.time.Instant;

public record AnalyticsResponse(String code, int redirectCount, Instant lastAccessedAt) { }
