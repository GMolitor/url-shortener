package com.example.urlshortener.domain;

public class CodeNotFoundException extends RuntimeException {
    public CodeNotFoundException(String code) {
        super("Short code was not found: " + code);
    }
}
