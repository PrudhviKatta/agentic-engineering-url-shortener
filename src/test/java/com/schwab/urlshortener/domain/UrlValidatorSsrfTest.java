package com.schwab.urlshortener.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorSsrfTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1/admin", "http://localhost:8080", "http://169.254.169.254/latest/meta-data",
        "http://10.0.0.5/internal", "http://192.168.1.1/", "http://[::127.0.0.1]/", "http://[::1]/",
        "http://[fc00::1]/internal", "http://[fd00::1]/internal"
    })
    void rejectsLocalAndLinkLocalTargets(String raw) {
        assertThatThrownBy(() -> UrlValidator.validate(raw)).isInstanceOf(InvalidUrlException.class);
    }
}
