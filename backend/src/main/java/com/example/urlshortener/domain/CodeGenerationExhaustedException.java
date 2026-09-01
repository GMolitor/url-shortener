package com.example.urlshortener.domain;

public class CodeGenerationExhaustedException extends RuntimeException {
    public CodeGenerationExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
