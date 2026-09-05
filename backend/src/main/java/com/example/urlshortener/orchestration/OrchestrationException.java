package com.example.urlshortener.orchestration;

import org.springframework.http.HttpStatus;

public class OrchestrationException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public OrchestrationException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }
}
