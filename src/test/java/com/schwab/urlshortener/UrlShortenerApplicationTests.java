package com.schwab.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UrlShortenerApplicationTests {
    @Test
    void contextLoads() {
    }

    @Test
    void recognizesOnlyExplicitCommandLineModes() {
        assertThat(UrlShortenerApplication.isCliMode(new String[]{"--metrics-report"})).isTrue();
        assertThat(UrlShortenerApplication.isCliMode(new String[]{"--scenario=greenfield"})).isTrue();
        assertThat(UrlShortenerApplication.isCliMode(new String[]{"--server.port=0"})).isFalse();
    }
}
