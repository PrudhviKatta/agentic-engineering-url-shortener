package com.schwab.urlshortener.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "file:///etc/passwd", "data:text/html,<script>", "ftp://x.com"})
    void rejectsDisallowedSchemes(String raw) {
        assertThatThrownBy(() -> UrlValidator.validate(raw)).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsHttpAndHttps() {
        assertThat(UrlValidator.validate("https://example.com/path")).isEqualTo("https://example.com/path");
        assertThat(UrlValidator.validate("http://example.com")).isEqualTo("http://example.com");
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> UrlValidator.validate("not a url")).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void acceptsPublicIpv6Addresses() {
        assertThat(UrlValidator.validate("http://[2001:4860:4860::8888]/")).isEqualTo("http://[2001:4860:4860::8888]/");
    }
}
