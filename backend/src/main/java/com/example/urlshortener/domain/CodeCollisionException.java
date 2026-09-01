package com.example.urlshortener.domain;

import org.springframework.dao.DataAccessException;

public class CodeCollisionException extends DataAccessException {
    public CodeCollisionException(String message, Throwable cause) {
        super(message, cause);
    }
}
