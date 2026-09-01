package com.example.urlshortener.service;

import com.example.urlshortener.domain.CodeCollisionException;
import com.example.urlshortener.domain.CodeGenerationExhaustedException;
import com.example.urlshortener.domain.CodeGenerator;
import com.example.urlshortener.domain.Link;
import com.example.urlshortener.domain.UrlPolicy;
import com.example.urlshortener.repository.LinkRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LinkCreationService {
    public static final int MAX_COLLISION_RETRIES = 5;

    private final LinkRepository linkRepository;
    private final CodeGenerator codeGenerator;
    private final Clock clock;

    @Autowired
    public LinkCreationService(LinkRepository linkRepository, CodeGenerator codeGenerator) {
        this(linkRepository, codeGenerator, Clock.systemUTC());
    }

    LinkCreationService(LinkRepository linkRepository, CodeGenerator codeGenerator, Clock clock) {
        this.linkRepository = linkRepository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    public Link create(String destinationUrl) {
        // Validate once, then let the unique database constraint decide each generated-code collision.
        UrlPolicy.validate(destinationUrl);
        CodeCollisionException lastCollision = null;
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String code = codeGenerator.generate();
            Link link = new Link(null, code, destinationUrl, Instant.now(clock));
            try {
                return linkRepository.save(link);
            } catch (CodeCollisionException exception) {
                lastCollision = exception;
            }
        }
        throw new CodeGenerationExhaustedException("Unable to allocate a unique short code", lastCollision);
    }
}
