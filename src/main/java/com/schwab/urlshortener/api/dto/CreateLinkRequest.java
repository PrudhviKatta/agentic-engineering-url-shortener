package com.schwab.urlshortener.api.dto;

import java.time.Instant;

public record CreateLinkRequest(String longUrl, Instant expiresAt) { }
