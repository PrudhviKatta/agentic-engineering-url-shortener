package com.schwab.urlshortener.observability.dto;

import java.time.Instant;

public record AuditEventResponse(String actorType, String action, Instant timestamp, String result, String reason) { }
