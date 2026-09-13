package com.schwab.urlshortener.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkTest {

    @Test
    void isExpiredWhenPastExpiry() {
        ShortLink link = new ShortLink(1L, "abc1234", "https://x.com",
                Instant.now(), Instant.parse("2000-01-01T00:00:00Z"), 0, null);
        assertThat(link.isExpired()).isTrue();
    }

    @Test
    void notExpiredWhenNoExpirySet() {
        ShortLink link = new ShortLink(1L, "abc1234", "https://x.com",
                Instant.now(), null, 0, null);
        assertThat(link.isExpired()).isFalse();
    }
}
