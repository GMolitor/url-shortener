package com.example.urlshortener.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final long MAX_REQUEST_BODY_BYTES = 4096;
    public static final long MAX_ORCHESTRATION_REQUEST_BODY_BYTES = MAX_REQUEST_BODY_BYTES;

    /** Assigns a correlation ID and bounds create-request bytes before deserialization. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = ensureRequestId(request, response);

        if (!isBoundedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long maxBodyBytes = maxBodyBytes(request);
        if (request.getContentLengthLong() > maxBodyBytes) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "REQUEST_TOO_LARGE",
                    "Request body is too large", requestId);
            return;
        }

        byte[] body;
        try {
            body = readBodyAtMostLimit(request.getInputStream(), maxBodyBytes);
        } catch (IOException exception) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "INVALID_REQUEST", "Request is invalid", requestId);
            return;
        }
        if (body == null) {
            writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "REQUEST_TOO_LARGE",
                    "Request body is too large", requestId);
            return;
        }
        filterChain.doFilter(new ReplayableRequest(request, body), response);
    }

    static String ensureRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);
        return requestId;
    }

    private boolean isBoundedRequest(HttpServletRequest request) {
        if (!request.getMethod().equalsIgnoreCase("POST")) return false;
        return request.getRequestURI().equals("/api/links")
                || request.getRequestURI().startsWith("/api/orchestration/");
    }

    private long maxBodyBytes(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/orchestration/")
                ? MAX_ORCHESTRATION_REQUEST_BODY_BYTES
                : MAX_REQUEST_BODY_BYTES;
    }

    /** Returns null after reading only one byte beyond the permitted body size. */
    private byte[] readBodyAtMostLimit(InputStream inputStream, long maxBodyBytes) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream((int) maxBodyBytes + 1);
        byte[] buffer = new byte[1024];
        while (true) {
            int remaining = (int) maxBodyBytes + 1 - body.size();
            int read = inputStream.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) {
                return body.toByteArray();
            }
            if (read == 0) {
                int singleByte = inputStream.read();
                if (singleByte == -1) {
                    return body.toByteArray();
                }
                if (body.size() == maxBodyBytes) {
                    return null;
                }
                body.write(singleByte);
                continue;
            }
            body.write(buffer, 0, read);
            if (body.size() > MAX_REQUEST_BODY_BYTES) {
                return null;
            }
        }
    }

    private void writeError(HttpServletResponse response, int status, String errorCode, String message, String requestId)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":"
                + status
                + ",\"errorCode\":\""
                + errorCode
                + "\",\"message\":\""
                + message
                + "\",\"requestId\":\""
                + requestId
                + "\",\"timestamp\":\""
                + Instant.now()
                + "\"}");
    }

    private static final class ReplayableRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final ReplayableServletInputStream inputStream;

        private ReplayableRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
            this.inputStream = new ReplayableServletInputStream(body);
        }

        @Override
        public ServletInputStream getInputStream() {
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), encoding == null ? StandardCharsets.UTF_8.name() : encoding));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class ReplayableServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream delegate;

        private ReplayableServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            return delegate.read(bytes, offset, length);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Asynchronous request reading is not supported");
        }
    }
}
