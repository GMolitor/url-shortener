package com.example.urlshortener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite::memory:")
class ApplicationStartupTest {
    @Test
    void applicationStarts(ApplicationContext context) {
        assertThat(context).isNotNull();
        assertThat(context.getBean(UrlShortenerApplication.class)).isNotNull();
    }
}
