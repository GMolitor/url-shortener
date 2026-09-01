package com.example.urlshortener.api;

import com.example.urlshortener.domain.Link;
import com.example.urlshortener.domain.CodeNotFoundException;
import com.example.urlshortener.repository.LinkRepository;
import com.example.urlshortener.service.LinkCreationService;
import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class LinkController {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Za-z0-9]{7}");

    private final LinkCreationService creationService;
    private final LinkRepository linkRepository;
    private final String publicOrigin;

    public LinkController(
            LinkCreationService creationService,
            LinkRepository linkRepository,
            @Value("${app.public-origin}") String publicOrigin) {
        this.creationService = creationService;
        this.linkRepository = linkRepository;
        this.publicOrigin = publicOrigin;
    }

    /** Validates the request shape before delegating URL policy and persistence to the service. */
    @PostMapping(path = "/api/links", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateLinkResponse> create(@RequestBody CreateLinkRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.url() == null) {
            throw new IllegalArgumentException("Request must contain a url");
        }
        Link link = creationService.create(request.url());
        CreateLinkResponse response = CreateLinkResponse.from(link, publicOrigin);
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    /** Looks up the case-sensitive code and returns a non-cached temporary redirect without fetching the destination. */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Invalid short code");
        }
        Optional<Link> link = linkRepository.findByCode(code);
        if (link.isEmpty()) {
            throw new CodeNotFoundException(code);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(link.get().destinationUrl()));
        headers.setCacheControl(CacheControl.noStore());
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
