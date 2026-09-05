package com.example.urlshortener.api;

import com.example.urlshortener.domain.CodeGenerationExhaustedException;
import com.example.urlshortener.domain.CodeNotFoundException;
import com.example.urlshortener.domain.InvalidUrlException;
import com.example.urlshortener.orchestration.OrchestrationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InvalidUrlException.class)
    ResponseEntity<ErrorResponse> invalidUrl(InvalidUrlException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_URL", exception.getMessage(), request);
    }

    @ExceptionHandler(CodeNotFoundException.class)
    ResponseEntity<ErrorResponse> codeNotFound(CodeNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CODE_NOT_FOUND", "Short code was not found", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalidRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json", request);
    }

    @ExceptionHandler({
        HttpRequestMethodNotSupportedException.class,
        NoHandlerFoundException.class,
        NoResourceFoundException.class
    })
    ResponseEntity<ErrorResponse> unsupportedRoute(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request is invalid", request);
    }

    @ExceptionHandler(CodeGenerationExhaustedException.class)
    ResponseEntity<ErrorResponse> codeGenerationFailure(
            CodeGenerationExhaustedException exception, HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "Short-code service is unavailable", request);
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> storageFailure(DataAccessException exception, HttpServletRequest request) {
        logFailure("storage_failure", exception, request);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILURE", "Storage operation failed", request);
    }

    @ExceptionHandler(OrchestrationException.class)
    ResponseEntity<ErrorResponse> orchestrationFailure(OrchestrationException exception, HttpServletRequest request) {
        return error(exception.status(), exception.errorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpectedFailure(Exception exception, HttpServletRequest request) {
        logFailure("unexpected_failure", exception, request);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILURE", "Request could not be completed", request);
    }

    private void logFailure(String event, Exception exception, HttpServletRequest request) {
        LOGGER.error(
                "event={} exception={} method={} path={} requestId={}",
                event,
                exception.getClass().getSimpleName(),
                request.getMethod(),
                request.getRequestURI(),
                request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
    }

    static ResponseEntity<ErrorResponse> error(
            HttpStatus status, String errorCode, String message, HttpServletRequest request) {
        // Keep the response safe for clients while preserving the request ID needed to correlate the full server-side failure.
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null) {
            requestId = "unknown";
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), errorCode, message, requestId, Instant.now()));
    }
}
