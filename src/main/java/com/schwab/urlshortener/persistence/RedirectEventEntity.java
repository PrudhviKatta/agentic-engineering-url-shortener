package com.schwab.urlshortener.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "redirect_events")
public class RedirectEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_link_id", nullable = false)
    private Long shortLinkId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(nullable = false, length = 16)
    private String outcome; // "redirected" | "expired" | "not_found"

    public Long getId() { return id; }
    public Long getShortLinkId() { return shortLinkId; }
    public void setShortLinkId(Long shortLinkId) { this.shortLinkId = shortLinkId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
}
