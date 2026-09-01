package com.example.urlshortener.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UrlPolicyTest {
    @Test
    void acceptsHttpAndHttpsUrls() {
        assertThat(UrlPolicy.validate("http://example.com/path?value=1")).isEqualTo(URI.create("http://example.com/path?value=1"));
        assertThat(UrlPolicy.validate("HTTPS://example.com")).isEqualTo(URI.create("HTTPS://example.com"));
    }

    @ParameterizedTest
    @MethodSource("invalidUrls")
    void rejectsInvalidDestinationUrls(String url) {
        assertThatThrownBy(() -> UrlPolicy.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    private static Stream<Arguments> invalidUrls() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("ftp://example.com"),
                Arguments.of("https://user:password@example.com"),
                Arguments.of("https://example.com/a\n/b"),
                Arguments.of("https://example.com/%0a"),
                Arguments.of("https://example.com:99999"),
                Arguments.of("https://"),
                Arguments.of("https://example.com/" + "x".repeat(2048)));
    }
}
