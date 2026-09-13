package com.schwab.urlshortener.api.dto;

import java.time.Instant;

public record LinkResponse(String code, String longUrl, String shortUrl, Instant expiresAt) { }
