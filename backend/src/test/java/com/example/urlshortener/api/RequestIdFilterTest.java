package com.example.urlshortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsChunkedBodyOverLimitWithoutCallingTheApplication() throws Exception {
        MockHttpServletRequest request = createCreateRequest();
        doReturn(-1L).when(request).getContentLengthLong();
        doReturn(inputStream(new byte[(int) RequestIdFilter.MAX_REQUEST_BODY_BYTES + 1])).when(request).getInputStream();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new RequestIdFilter().doFilter(request, response, chain);

        assertError(response, 413, "REQUEST_TOO_LARGE", "Request body is too large");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsTruncatedBodyReadFailureToStableInvalidRequest() throws Exception {
        MockHttpServletRequest request = createCreateRequest();
        doReturn(-1L).when(request).getContentLengthLong();
        doReturn(new FailingInputStream()).when(request).getInputStream();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new RequestIdFilter().doFilter(request, response, chain);

        assertError(response, 400, "INVALID_REQUEST", "Request is invalid");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

    private void assertError(MockHttpServletResponse response, int status, String errorCode, String message)
            throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("errorCode").asText()).isEqualTo(errorCode);
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
        assertThat(body.path("timestamp").asText()).isNotBlank();
    }

    private static final class FailingInputStream extends ServletInputStream {
        @Override
        public int read() throws IOException {
            throw new IOException("truncated request");
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            throw new IOException("truncated request");
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
