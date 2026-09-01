package com.example.urlshortener.api;

import com.example.urlshortener.domain.Link;
import java.time.Instant;

public record CreateLinkResponse(String code, String shortUrl, String destinationUrl, Instant createdAt) {
    /** Builds the public URL from trusted configuration rather than request host headers. */
    public static CreateLinkResponse from(Link link, String publicOrigin) {
        return new CreateLinkResponse(
                link.code(),
                publicOrigin.replaceFirst("/+$", "") + "/" + link.code(),
                link.destinationUrl(),
                link.createdAt());
    }
}
