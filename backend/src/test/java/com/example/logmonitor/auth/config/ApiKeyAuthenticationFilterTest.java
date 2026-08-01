package com.example.logmonitor.auth.config;

import com.example.logmonitor.apikey.application.ApiKeyService;
import com.example.logmonitor.apikey.config.ApiKeyProperties;
import com.example.logmonitor.apikey.domain.ApiKey;
import com.example.logmonitor.common.RateLimiterService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class ApiKeyAuthenticationFilterTest {

    private ApiKeyService apiKeyService;
    private ApiKeyProperties properties;
    private ApiKeyAuthenticationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        apiKeyService = mock(ApiKeyService.class);
        properties = new ApiKeyProperties();
        properties.setBurstCapacity(1);
        properties.setRequestsPerSecond(1);
        filter = new ApiKeyAuthenticationFilter(apiKeyService, properties, new RateLimiterService());
        filterChain = mock(FilterChain.class);

        ApiKey key = new ApiKey("project-a", "ingestion", "ak_filter", "hash", "last", "org-1", "user-1");
        key.setId("key-filter");
        when(apiKeyService.validateApiKey("raw-key")).thenReturn(Optional.of(key));
    }

    @AfterEach
    void clearContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void returns429AfterPerKeyBurstIsConsumed() throws Exception {
        MockHttpServletRequest firstRequest = request();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, filterChain);
        assertEquals(200, firstResponse.getStatus());

        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(request(), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
        assertEquals("1", secondResponse.getHeader("Retry-After"));
        verify(filterChain, times(1)).doFilter(any(), any());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ingest/logs");
        request.addHeader("X-API-Key", "raw-key");
        return request;
    }
}
