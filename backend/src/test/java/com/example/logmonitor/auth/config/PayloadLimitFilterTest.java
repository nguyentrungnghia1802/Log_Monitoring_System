package com.example.logmonitor.auth.config;

import com.example.logmonitor.ingestion.config.IngestionLimitsProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadLimitFilterTest {

    @Test
    void rejectsContentLengthBeforeReadingOrCallingTheChain() throws Exception {
        IngestionLimitsProperties properties = new IngestionLimitsProperties();
        properties.setMaxHttpBodyBytes(10);
        PayloadLimitFilter filter = new PayloadLimitFilter(properties, new SimpleMeterRegistry());
        MockHttpServletRequest request = request("12345678901");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> called.set(true));

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("PAYLOAD_TOO_LARGE"));
        assertTrue(!called.get());
    }

    @Test
    void enforcesLimitForChunkedBodiesWhileAllowingBodyWithinLimit() throws Exception {
        IngestionLimitsProperties properties = new IngestionLimitsProperties();
        properties.setMaxHttpBodyBytes(10);
        PayloadLimitFilter filter = new PayloadLimitFilter(properties, new SimpleMeterRegistry());
        MockHttpServletRequest request = chunkedRequest("12345678901");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (wrappedRequest, ignoredResponse) -> {
            while (wrappedRequest.getInputStream().read() != -1) {
                // Drain the request to exercise the streaming guard.
            }
        });

        assertEquals(413, response.getStatus());
    }

    @Test
    void passesARequestAtTheConfiguredBoundary() throws Exception {
        IngestionLimitsProperties properties = new IngestionLimitsProperties();
        properties.setMaxHttpBodyBytes(10);
        PayloadLimitFilter filter = new PayloadLimitFilter(properties, new SimpleMeterRegistry());
        MockHttpServletRequest request = request("1234567890");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, response, (wrappedRequest, ignoredResponse) -> {
            while (wrappedRequest.getInputStream().read() != -1) {
                // Drain the request to confirm exactly-max payloads are accepted.
            }
            called.set(true);
        });

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest request(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ingest/logs");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        return request;
    }

    private MockHttpServletRequest chunkedRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ingest/logs") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        return request;
    }
}
