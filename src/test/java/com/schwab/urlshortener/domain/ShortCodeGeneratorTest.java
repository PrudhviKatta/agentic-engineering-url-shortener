package com.schwab.urlshortener.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    @Test
    void toBase62Zero() {
        assertThat(ShortCodeGenerator.toBase62(0)).isEqualTo("0");
    }

    @Test
    void toBase62RoundtripValues() {
        assertThat(ShortCodeGenerator.toBase62(61)).isEqualTo("Z");
        assertThat(ShortCodeGenerator.toBase62(62)).isEqualTo("10");
    }

    @Test
    void generateCodeIsZeroPaddedAndDeterministic() {
        assertThat(ShortCodeGenerator.generateCode(1)).isEqualTo("0000001");
        assertThat(ShortCodeGenerator.generateCode(62)).isEqualTo("0000010");
        assertThat(ShortCodeGenerator.generateCode(1)).isEqualTo(ShortCodeGenerator.generateCode(1));
    }
}
