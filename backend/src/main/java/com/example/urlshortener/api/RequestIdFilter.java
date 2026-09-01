package com.example.urlshortener.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final long MAX_REQUEST_BODY_BYTES = 4096;

    /** Assigns a correlation ID and rejects oversized create requests before deserialization. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        if (request.getContentLengthLong() > MAX_REQUEST_BODY_BYTES
                && request.getRequestURI().equals("/api/links")
                && request.getMethod().equalsIgnoreCase("POST")) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":413,\"errorCode\":\"REQUEST_TOO_LARGE\","
                    + "\"message\":\"Request body is too large\",\"requestId\":\""
                    + requestId
                    + "\",\"timestamp\":\""
                    + Instant.now()
                    + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
