package com.schwab.urlshortener.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "short_links")
public class ShortLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = true, length = 16)
    private String code;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "redirect_count", nullable = false)
    private int redirectCount = 0;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLongUrl() { return longUrl; }
    public void setLongUrl(String longUrl) { this.longUrl = longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public int getRedirectCount() { return redirectCount; }
    public void setRedirectCount(int redirectCount) { this.redirectCount = redirectCount; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
}
