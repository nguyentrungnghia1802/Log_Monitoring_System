package com.example.logmonitor.auth.config;

import com.example.logmonitor.ingestion.api.PayloadTooLargeException;
import com.example.logmonitor.ingestion.config.IngestionLimitsProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class PayloadLimitFilter extends OncePerRequestFilter {

    private final IngestionLimitsProperties limits;
    private final Counter payloadTooLargeCounter;

    public PayloadLimitFilter(IngestionLimitsProperties limits, MeterRegistry meterRegistry) {
        this.limits = limits;
        this.payloadTooLargeCounter = Counter.builder("ingestion.rejected.payload_too_large")
            .description("Ingestion requests rejected because the HTTP body exceeded its limit")
            .register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/ingest");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        jakarta.servlet.FilterChain filterChain
    ) throws ServletException, IOException {
        int maxBytes = effectiveMaxBodyBytes();
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            reject(response);
            return;
        }

        SizeLimitedRequest limitedRequest = new SizeLimitedRequest(request, maxBytes);
        try {
            filterChain.doFilter(limitedRequest, response);
        } catch (RuntimeException ex) {
            if (hasCause(ex, PayloadTooLargeException.class)) {
                reject(response);
                return;
            }
            throw ex;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        payloadTooLargeCounter.increment();
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.getWriter().write(
            "{\"accepted\":false,\"error\":{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"Request body exceeds the configured maximum size\"}}"
        );
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private int effectiveMaxBodyBytes() {
        return Math.min(Math.max(1, limits.getMaxHttpBodyBytes()), 10 * 1024 * 1024);
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {
        private final int maxBytes;
        private LimitedServletInputStream inputStream;

        private SizeLimitedRequest(HttpServletRequest request, int maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                getInputStream(), encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final int maxBytes;
        private int bytesRead;

        private LimitedServletInputStream(ServletInputStream delegate, int maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (bytesRead >= maxBytes) {
                int extra = delegate.read();
                if (extra != -1) {
                    throw new PayloadTooLargeException();
                }
                return -1;
            }
            int value = delegate.read();
            if (value != -1) {
                bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (bytesRead >= maxBytes) {
                return read();
            }
            int allowed = Math.min(length, maxBytes - bytesRead);
            int count = delegate.read(buffer, offset, allowed);
            if (count > 0) {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public int available() throws IOException {
            return Math.min(delegate.available(), Math.max(0, maxBytes - bytesRead));
        }

        @Override
        public void close() throws IOException { delegate.close(); }

        @Override
        public boolean isFinished() { return delegate.isFinished(); }

        @Override
        public boolean isReady() { return delegate.isReady(); }

        @Override
        public void setReadListener(ReadListener readListener) { delegate.setReadListener(readListener); }
    }
}
