package com.example.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshortener.domain.CodeCollisionException;
import com.example.urlshortener.domain.CodeGenerationExhaustedException;
import com.example.urlshortener.domain.CodeGenerator;
import com.example.urlshortener.domain.CodeGenerationException;
import com.example.urlshortener.domain.InvalidUrlException;
import com.example.urlshortener.domain.Link;
import com.example.urlshortener.repository.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkCreationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-31T19:30:00Z");

    @Mock
    private LinkRepository repository;

    @Mock
    private CodeGenerator codeGenerator;

    @Test
    void validatesBeforeGeneratingOrPersisting() {
        LinkCreationService service = service();

        assertThatThrownBy(() -> service.create("ftp://example.com"))
                .isInstanceOf(InvalidUrlException.class);
        verify(codeGenerator, never()).generate();
        verify(repository, never()).save(any());
    }

    @Test
    void retriesOnlyOnCodeCollision() {
        Link created = new Link(1L, "def5678", "https://example.com", NOW);
        when(codeGenerator.generate()).thenReturn("abc1234", "def5678");
        when(repository.save(any())).thenThrow(new CodeCollisionException("collision", null)).thenReturn(created);

        Link result = service().create("https://example.com");

        assertThat(result).isEqualTo(created);
        verify(codeGenerator, times(2)).generate();
        verify(repository, times(2)).save(any());
    }

    @Test
    void persistsGeneratedLinkWithCurrentUtcTimestamp() {
        Link created = new Link(1L, "abc1234", "https://example.com", NOW);
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(repository.save(any())).thenReturn(created);

        Link result = service().create(created.destinationUrl());

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(repository).save(linkCaptor.capture());
        assertThat(result).isEqualTo(created);
        assertThat(linkCaptor.getValue()).isEqualTo(new Link(null, "abc1234", created.destinationUrl(), NOW));
    }

    @Test
    void propagatesGeneratorFailureWithoutPersisting() {
        when(codeGenerator.generate()).thenThrow(new CodeGenerationException("generator failed"));

        assertThatThrownBy(() -> service().create("https://example.com"))
                .isInstanceOf(CodeGenerationException.class)
                .hasMessage("generator failed");
        verify(repository, never()).save(any());
    }

    @Test
    void propagatesNonCollisionStorageFailureWithoutRetrying() {
        org.springframework.dao.DataAccessResourceFailureException failure =
                new org.springframework.dao.DataAccessResourceFailureException("database unavailable");
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(repository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service().create("https://example.com"))
                .isSameAs(failure);
        verify(codeGenerator).generate();
        verify(repository).save(any());
    }

    @Test
    void stopsAfterBoundedCollisionRetries() {
        when(codeGenerator.generate()).thenReturn("abc1234");
        when(repository.save(any())).thenThrow(new CodeCollisionException("collision", null));

        assertThatThrownBy(() -> service().create("https://example.com"))
                .isInstanceOf(CodeGenerationExhaustedException.class);
        verify(codeGenerator, times(LinkCreationService.MAX_COLLISION_RETRIES)).generate();
        verify(repository, times(LinkCreationService.MAX_COLLISION_RETRIES)).save(any());
    }

    private LinkCreationService service() {
        return new LinkCreationService(repository, codeGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
