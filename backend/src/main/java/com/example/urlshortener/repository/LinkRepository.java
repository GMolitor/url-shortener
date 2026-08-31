package com.example.urlshortener.repository;

import com.example.urlshortener.domain.Link;
import java.util.Optional;

public interface LinkRepository {
    Link save(Link link);

    Optional<Link> findByCode(String code);
}
