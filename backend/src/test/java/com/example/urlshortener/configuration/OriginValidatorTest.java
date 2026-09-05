package com.example.urlshortener.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.urlshortener.UrlShortenerApplication;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OriginValidatorTest {
    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("invalidOrigins")
    void rejectsOriginsThatCannotBeTrusted(String settingName, String origin) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> OriginValidator.normalize(settingName, origin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(settingName);
    }

    @ParameterizedTest(name = "{0} accepts boundary port {1}")
    @MethodSource("validBoundaryOrigins")
    void acceptsValidBoundaryPorts(String settingName, String origin) {
        assertThat(OriginValidator.normalize(settingName, origin)).isEqualTo(origin);
    }

    @Test
    void normalizesValidHttpAndHttpsOrigins() {
        assertThat(OriginValidator.normalize("ORIGIN", "HTTP://localhost:8080/")).isEqualTo("http://localhost:8080");
        assertThat(OriginValidator.normalize("ORIGIN", "https://short.example.test")).isEqualTo(
                "https://short.example.test");
    }

    @ParameterizedTest(name = "{0} rejects invalid configured origin {1} during startup")
    @MethodSource("invalidConfiguredOrigins")
    void rejectsInvalidConfiguredOriginDuringApplicationStartup(
            String propertyName, String settingName, String origin) {
        contextRunner()
                .withPropertyValues(propertyName + "=" + origin)
                .run(context -> assertStartupFailureContains(context.getStartupFailure(), settingName));
    }

    @Test
    void startsWithValidConfiguredOrigins() {
        contextRunner().run(context -> {
            assertThat(context.getStartupFailure()).isNull();
            assertThat(context).hasBean("linkController");
            assertThat(context).hasBean("webConfiguration");
        });
    }

    private static ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(UrlShortenerApplication.class)
                .withPropertyValues(
                        "spring.main.web-application-type=none",
                        "spring.datasource.url=jdbc:sqlite:file:originValidation?mode=memory&cache=shared",
                        "app.public-origin=https://short.example.test",
                        "app.frontend-origin=http://localhost:5173");
    }

    private static Stream<Arguments> invalidOrigins() {
        List<String> values = List.of(
                "",
                "ftp://short.example.test",
                "https://user:password@short.example.test",
                "https://short.example.test/path",
                "https://short.example.test?tenant=one",
                "https://short.example.test#fragment",
                "https://*.example.test",
                "//short.example.test",
                "http://[",
                "http://short.example.test:0",
                "https://short.example.test:-1",
                "http://short.example.test:65536",
                "https://short.example.test:65536x",
                "http://short.example.test:");
        return Stream.of("PUBLIC_ORIGIN", "FRONTEND_ORIGIN")
                .flatMap(setting -> values.stream().map(origin -> Arguments.of(setting, origin)));
    }

    private static Stream<Arguments> validBoundaryOrigins() {
        return Stream.of(
                Arguments.of("PUBLIC_ORIGIN", "http://short.example.test:1"),
                Arguments.of("PUBLIC_ORIGIN", "https://short.example.test:65535"),
                Arguments.of("FRONTEND_ORIGIN", "http://short.example.test:1"),
                Arguments.of("FRONTEND_ORIGIN", "https://short.example.test:65535"));
    }

    private static Stream<Arguments> invalidConfiguredOrigins() {
        return Stream.of(
                Arguments.of("app.public-origin", "PUBLIC_ORIGIN", "http://short.example.test:0"),
                Arguments.of("app.public-origin", "PUBLIC_ORIGIN", "https://short.example.test:-1"),
                Arguments.of("app.public-origin", "PUBLIC_ORIGIN", "http://short.example.test:65536"),
                Arguments.of("app.public-origin", "PUBLIC_ORIGIN", "https://short.example.test:65536x"),
                Arguments.of("app.public-origin", "PUBLIC_ORIGIN", "http://short.example.test:"),
                Arguments.of("app.frontend-origin", "FRONTEND_ORIGIN", "http://short.example.test:0"),
                Arguments.of("app.frontend-origin", "FRONTEND_ORIGIN", "https://short.example.test:-1"),
                Arguments.of("app.frontend-origin", "FRONTEND_ORIGIN", "http://short.example.test:65536"),
                Arguments.of("app.frontend-origin", "FRONTEND_ORIGIN", "https://short.example.test:65536x"),
                Arguments.of("app.frontend-origin", "FRONTEND_ORIGIN", "http://short.example.test:"));
    }

    private static void assertStartupFailureContains(Throwable failure, String expectedText) {
        assertThat(failure).as("application startup failure").isNotNull();
        String messages = Stream.iterate(failure, value -> value != null, Throwable::getCause)
                .map(Throwable::toString)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(messages).contains(expectedText);
    }
}
