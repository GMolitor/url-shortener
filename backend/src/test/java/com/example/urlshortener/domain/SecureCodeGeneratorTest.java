package com.example.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class SecureCodeGeneratorTest {
    private static final Pattern BASE62_CODE = Pattern.compile("[A-Za-z0-9]{7}");

    private final SecureCodeGenerator generator = new SecureCodeGenerator();

    @RepeatedTest(100)
    void generatesSevenCharacterBase62Codes() {
        String code = generator.generate();

        assertThat(code).matches(BASE62_CODE);
        assertThat(ReservedPathPolicy.isReservedCode(code)).isFalse();
    }

    @Test
    void reservedPrefixesAreRecognized() {
        assertThat(ReservedPathPolicy.isReservedCode("api1234")).isTrue();
        assertThat(ReservedPathPolicy.isReservedCode("actuator")).isTrue();
        assertThat(ReservedPathPolicy.isReservedCode("assets1")).isTrue();
        assertThat(ReservedPathPolicy.isReservedCode("abc1234")).isFalse();
    }
}
