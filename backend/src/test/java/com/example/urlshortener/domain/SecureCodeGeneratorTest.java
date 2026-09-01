package com.example.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
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

    @Test
    void retriesWhenRandomlyGeneratedCodeUsesReservedPrefix() {
        CountingSecureRandom random = new CountingSecureRandom(26, 41, 34, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6);

        String code = new SecureCodeGenerator(random).generate();

        assertThat(code).isEqualTo("ABCDEFG");
        assertThat(random.calls).isEqualTo(14);
    }

    @Test
    void failsAfterReservedCodeGenerationAttemptsAreExhausted() {
        CountingSecureRandom random = new CountingSecureRandom(26, 41, 34, 0, 0, 0, 0);

        assertThatThrownBy(() -> new SecureCodeGenerator(random).generate())
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("Unable to generate a non-reserved code");
        assertThat(random.calls).isEqualTo(32 * SecureCodeGenerator.CODE_LENGTH);
    }

    private static final class CountingSecureRandom extends SecureRandom {
        private final int[] values;
        private int calls;

        private CountingSecureRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[calls++ % values.length];
        }
    }
}
