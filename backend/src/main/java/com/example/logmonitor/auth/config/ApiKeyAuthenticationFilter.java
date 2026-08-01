package com.example.logmonitor.auth.config;

import com.example.logmonitor.apikey.application.ApiKeyService;
import com.example.logmonitor.apikey.domain.ApiKey;
import com.example.logmonitor.apikey.config.ApiKeyProperties;
import com.example.logmonitor.common.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;
    private final ApiKeyProperties properties;
    private final RateLimiterService rateLimiterService;

    public ApiKeyAuthenticationFilter(
        ApiKeyService apiKeyService,
        ApiKeyProperties properties,
        RateLimiterService rateLimiterService
    ) {
        this.apiKeyService = apiKeyService;
        this.properties = properties;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/ingest")) {
            String apiKeyHeader = request.getHeader("X-API-Key");
            if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
                Optional<ApiKey> validKey = apiKeyService.validateApiKey(apiKeyHeader);
                if (validKey.isPresent()) {
                    ApiKey key = validKey.get();
                    String limiterKey = key.getId() != null ? key.getId() : key.getPublicId();
                    int capacity = Math.max(1, properties.getBurstCapacity());
                    int refillRate = Math.max(1, properties.getRequestsPerSecond());
                    if (!rateLimiterService.tryAcquire(limiterKey, capacity, refillRate)) {
                        response.setHeader("Retry-After", "1");
                        writeError(response, 429,
                            "RATE_LIMITED", "API key request rate exceeded");
                        return;
                    }
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        new ApiKeyPrincipal(key.getId(), key.getProjectId(), key.getOrganizationId()),
                        null,
                        List.of(() -> "ROLE_INGESTION_CLIENT")
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED", "Invalid or revoked API key");
                    return;
                }
            } else {
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "Missing X-API-Key header");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    public record ApiKeyPrincipal(String apiKeyId, String projectId, String organizationId) {
        public ApiKeyPrincipal(String apiKeyId, String projectId) {
            this(apiKeyId, projectId, null);
        }
    }
}
