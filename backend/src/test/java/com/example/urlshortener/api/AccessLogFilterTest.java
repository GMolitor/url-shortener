package com.example.urlshortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccessLogFilterTest {
    private static final String DESTINATION_MARKER = "https://secret.example/destination";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Logger accessLogger = (Logger) LoggerFactory.getLogger(AccessLogFilter.class);
    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();

    @BeforeEach
    void captureAccessLogs() {
        logEvents.start();
        accessLogger.addAppender(logEvents);
    }

    @AfterEach
    void stopCapturingAccessLogs() {
        accessLogger.detachAppender(logEvents);
        logEvents.stop();
    }

    @Test
    void logsFixedLengthOversizedResponseExactlyOnceWithoutBodyLeakage() throws Exception {
        MockHttpServletRequest request = createCreateRequest();
        request.setContent(("{\"url\":\"" + DESTINATION_MARKER + "\"}"
                + "x".repeat((int) RequestIdFilter.MAX_REQUEST_BODY_BYTES + 1)).getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain application = mock(FilterChain.class);

        runThroughBothFilters(request, response, application);

        assertStableError(response, 413, "REQUEST_TOO_LARGE", "Request body is too large");
        verify(application, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertSingleSafeAccessEvent(response, DESTINATION_MARKER);
    }

    @Test
    void logsChunkedOversizedResponseExactlyOnceWithoutBodyLeakage() throws Exception {
        MockHttpServletRequest request = createCreateRequest();
        doReturn(-1L).when(request).getContentLengthLong();
        doReturn(inputStream(("{\"url\":\"" + DESTINATION_MARKER + "\"}"
                + "x".repeat((int) RequestIdFilter.MAX_REQUEST_BODY_BYTES + 1)).getBytes()))
                .when(request)
                .getInputStream();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain application = mock(FilterChain.class);

        runThroughBothFilters(request, response, application);

        assertStableError(response, 413, "REQUEST_TOO_LARGE", "Request body is too large");
        verify(application, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertSingleSafeAccessEvent(response, DESTINATION_MARKER);
    }

    @Test
    void logsBodyReadFailureExactlyOnceWithoutExceptionOrBodyLeakage() throws Exception {
        MockHttpServletRequest request = createCreateRequest();
        doReturn(-1L).when(request).getContentLengthLong();
        doReturn(new FailingInputStream()).when(request).getInputStream();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain application = mock(FilterChain.class);

        runThroughBothFilters(request, response, application);

        assertStableError(response, 400, "INVALID_REQUEST", "Request is invalid");
        verify(application, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertSingleSafeAccessEvent(response, "truncated request");
    }

    @Test
    void logsNormalRequestExactlyOnceAfterItReachesTheApplication() throws Exception {
        MockHttpServletRequest request = createCreateRequest();
        request.setContent("{\"url\":\"https://example.test\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain application = mock(FilterChain.class);
        doAnswer(invocation -> {
                    ((HttpServletResponse) invocation.getArgument(1)).setStatus(201);
                    return null;
                })
                .when(application)
                .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        runThroughBothFilters(request, response, application);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(logEvents.list).hasSize(1);
        assertThat(logEvents.list.get(0).getFormattedMessage())
                .matches("event=http_request method=POST path=/api/links status=201 durationMs=\\d+ requestId=[0-9a-f-]{36}");
        assertThat(logEvents.list.get(0).getFormattedMessage()).doesNotContain("https://example.test");
        verify(application).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private void runThroughBothFilters(
            MockHttpServletRequest request, MockHttpServletResponse response, FilterChain application)
            throws ServletException, IOException {
        Filter requestIdFilter = new RequestIdFilter();
        FilterChain requestIdChain = (filteredRequest, filteredResponse) ->
                requestIdFilter.doFilter(filteredRequest, filteredResponse, application);
        new AccessLogFilter().doFilter(request, response, requestIdChain);
    }

    private void assertSingleSafeAccessEvent(MockHttpServletResponse response, String forbiddenValue) {
        assertThat(logEvents.list).hasSize(1);
        ILoggingEvent event = logEvents.list.get(0);
        assertThat(event.getFormattedMessage())
                .matches("event=http_request method=POST path=/api/links status=(400|413) durationMs=\\d+ requestId=[0-9a-f-]{36}");
        assertThat(event.getFormattedMessage()).doesNotContain(forbiddenValue);
        assertThat(event.getFormattedMessage()).contains("requestId=" + response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }

    private void assertStableError(MockHttpServletResponse response, int status, String errorCode, String message)
            throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("errorCode").asText()).isEqualTo(errorCode);
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
        assertThat(body.path("timestamp").asText()).isNotBlank();
        assertThat(response.getContentAsString()).doesNotContain(DESTINATION_MARKER);
    }

    private static MockHttpServletRequest createCreateRequest() {
        MockHttpServletRequest request = org.mockito.Mockito.spy(new MockHttpServletRequest());
        request.setMethod("POST");
        request.setRequestURI("/api/links");
        return request;
    }

    private static ServletInputStream inputStream(byte[] content) {
        ByteArrayInputStream delegate = new ByteArrayInputStream(content);
        return new ServletInputStream() {
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
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class FailingInputStream extends ServletInputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("truncated request with " + DESTINATION_MARKER);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            throw new IOException("truncated request with " + DESTINATION_MARKER);
        }

        @Override
        public boolean isFinished() {
            return false;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }
}
