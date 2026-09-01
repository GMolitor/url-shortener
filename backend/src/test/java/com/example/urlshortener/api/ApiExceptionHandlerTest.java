package com.example.urlshortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.urlshortener.domain.CodeGenerationExhaustedException;
import com.example.urlshortener.domain.CodeNotFoundException;
import com.example.urlshortener.domain.InvalidUrlException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void mapsInvalidUrlAndPreservesRequestId() {
        when(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).thenReturn("request-123");

        var response = new ApiExceptionHandler().invalidUrl(new InvalidUrlException("URL is malformed"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(
                new ErrorResponse(400, "INVALID_URL", "URL is malformed", "request-123", response.getBody().timestamp()));
    }

    @Test
    void mapsNotFoundAndDoesNotExposeSubmittedCode() {
        var response = new ApiExceptionHandler().codeNotFound(new CodeNotFoundException("secret1"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().errorCode()).isEqualTo("CODE_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Short code was not found");
        assertThat(response.getBody().requestId()).isEqualTo("unknown");
    }

    @Test
    void mapsAvailabilityAndStorageFailuresToSafeEnvelopes() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var unavailable = handler.codeGenerationFailure(
                new CodeGenerationExhaustedException("collision details", new RuntimeException("secret")), request);
        var storage = handler.storageFailure(new DataAccessResourceFailureException("SQL details"), request);
        var unexpected = handler.unexpectedFailure(new RuntimeException("private details"), request);

        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(unavailable.getBody().errorCode()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(unavailable.getBody().message()).doesNotContain("collision", "secret");
        assertThat(storage.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(storage.getBody().message()).isEqualTo("Storage operation failed");
        assertThat(unexpected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(unexpected.getBody().message()).isEqualTo("Request could not be completed");
    }
}
