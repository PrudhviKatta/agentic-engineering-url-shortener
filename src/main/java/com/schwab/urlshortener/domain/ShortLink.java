package com.schwab.urlshortener.domain;

import com.schwab.urlshortener.persistence.ShortLinkEntity;
import java.time.Instant;

public record ShortLink(
        Long id,
        String code,
        String longUrl,
        Instant createdAt,
        Instant expiresAt,
        int redirectCount,
        Instant lastAccessedAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public static ShortLink fromEntity(ShortLinkEntity entity) {
        return new ShortLink(
                entity.getId(), entity.getCode(), entity.getLongUrl(), entity.getCreatedAt(),
                entity.getExpiresAt(), entity.getRedirectCount(), entity.getLastAccessedAt());
    }
}
