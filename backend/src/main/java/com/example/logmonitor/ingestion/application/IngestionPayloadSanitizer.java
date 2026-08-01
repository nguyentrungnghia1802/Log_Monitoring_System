package com.example.logmonitor.ingestion.application;

import com.example.logmonitor.common.security.SensitiveDataRedactor;
import com.example.logmonitor.ingestion.api.BatchIngestionRequest;
import com.example.logmonitor.ingestion.api.IngestionRequest;
import com.example.logmonitor.ingestion.config.IngestionLimitsProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IngestionPayloadSanitizer {

    private static final Set<String> RESERVED_CONTEXT_KEYS = Set.of(
        "eventid",
        "timestamp",
        "level",
        "service",
        "environment",
        "eventtype",
        "message",
        "traceid",
        "requestid",
        "exception",
        "context",
        "tags",
        "receivedat",
        "expireat",
        "organizationid",
        "projectid",
        "apikeyid",
        "errorfingerprint",
        "apikey",
        "rawapikey",
        "authorization"
    );

    private final ObjectMapper objectMapper;
    private final IngestionLimitsProperties limits;
    private final SensitiveDataRedactor redactor;
    private final Counter validationRejectedCounter;

    public IngestionPayloadSanitizer(
        ObjectMapper objectMapper,
        IngestionLimitsProperties limits,
        SensitiveDataRedactor redactor,
        MeterRegistry meterRegistry
    ) {
        this.objectMapper = objectMapper;
        this.limits = limits;
        this.redactor = redactor;
        this.validationRejectedCounter = Counter.builder("ingestion.rejected.validation")
            .description("Ingestion requests rejected by schema, size, or privacy validation")
            .register(meterRegistry);
    }

    public IngestionRequest sanitize(IngestionRequest request) {
        if (request == null) {
            throw reject("Request body is required");
        }

        validateRequired(request.level(), "level");
        validateRequired(request.service(), "service");
        validateRequired(request.environment(), "environment");
        validateRequired(request.eventType(), "eventType");
        validateRequired(request.message(), "message");

        validateOptionalLength(request.eventId(), "eventId", effectiveMaxFieldLength());
        validateOptionalLength(request.traceId(), "traceId", effectiveMaxFieldLength());
        validateOptionalLength(request.requestId(), "requestId", effectiveMaxFieldLength());
        validateLength(request.message(), "message", effectiveMaxMessageLength());

        IngestionRequest.ExceptionRequest exception = sanitizeException(request.exception());
        Map<String, Object> context = sanitizeMap(
            request.context(), "context", effectiveMaxContextKeys(), effectiveMaxContextBytes());
        Map<String, Object> tags = sanitizeMap(
            request.tags(), "tags", effectiveMaxTagKeys(), effectiveMaxTagBytes());

        return new IngestionRequest(
            trimNullable(request.eventId()),
            request.timestamp(),
            request.level().trim(),
            request.service().trim(),
            request.environment().trim(),
            request.eventType().trim(),
            redactor.redactText(request.message().trim()),
            trimNullable(request.traceId()),
            trimNullable(request.requestId()),
            exception,
            context,
            tags
        );
    }

    public BatchIngestionRequest sanitize(BatchIngestionRequest request) {
        if (request == null || request.events() == null || request.events().isEmpty()) {
            throw reject("At least one event is required");
        }
        if (request.events().size() > effectiveMaxBatchSize()) {
            throw reject("Batch exceeds the configured maximum event count");
        }
        return new BatchIngestionRequest(request.events().stream().map(this::sanitize).toList());
    }

    private IngestionRequest.ExceptionRequest sanitizeException(IngestionRequest.ExceptionRequest exception) {
        if (exception == null) {
            return null;
        }
        validateOptionalLength(exception.type(), "exception.type", effectiveMaxFieldLength());
        validateOptionalLength(
            exception.message(), "exception.message", effectiveMaxExceptionMessageLength());
        validateOptionalLength(
            exception.stackTrace(), "exception.stackTrace", effectiveMaxStackTraceLength());
        return new IngestionRequest.ExceptionRequest(
            redactor.redactText(exception.type()),
            redactor.redactText(exception.message()),
            redactor.redactText(exception.stackTrace())
        );
    }

    private Map<String, Object> sanitizeMap(
        Map<String, Object> source,
        String rootName,
        int maxKeys,
        int maxSerializedBytes
    ) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Object sanitized = sanitizeValue(source, rootName, 0, maxKeys, false);
        if (!(sanitized instanceof Map<?, ?> map)) {
            throw reject(rootName + " must be an object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        try {
            if (objectMapper.writeValueAsBytes(result).length > maxSerializedBytes) {
                throw reject(rootName + " exceeds the configured serialized size");
            }
        } catch (JsonProcessingException ex) {
            throw reject(rootName + " contains unsupported values");
        }
        return result;
    }

    private Object sanitizeValue(
        Object value,
        String rootName,
        int depth,
        int rootMaxKeys,
        boolean nested
    ) {
        if (value == null) {
            return null;
        }
        if (depth > effectiveMaxDepth()) {
            throw reject(rootName + " exceeds the configured nesting depth");
        }
        if (value instanceof Map<?, ?> map) {
            int maxEntries = nested ? effectiveMaxCollectionEntries() : rootMaxKeys;
            if (map.size() > maxEntries) {
                throw reject(rootName + " exceeds the configured key count");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw reject(rootName + " contains a null key");
                }
                String key = String.valueOf(entry.getKey());
                if (key.isBlank() || key.length() > effectiveMaxKeyLength()) {
                    throw reject(rootName + " contains an invalid key");
                }
                boolean sensitiveKey = redactor.isSensitiveKey(key);
                if (!nested && RESERVED_CONTEXT_KEYS.contains(normalizeKey(key)) && !sensitiveKey) {
                    throw reject(rootName + " contains a reserved event field");
                }
                Object sanitizedValue = sanitizeValue(entry.getValue(), rootName, depth + 1, rootMaxKeys, true);
                result.put(key, sensitiveKey ? redactor.replacement() : sanitizedValue);
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            if (collection.size() > effectiveMaxCollectionEntries()) {
                throw reject(rootName + " contains too many collection entries");
            }
            return collection.stream()
                .map(item -> sanitizeValue(item, rootName, depth + 1, rootMaxKeys, true))
                .toList();
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length > effectiveMaxCollectionEntries()) {
                throw reject(rootName + " contains too many collection entries");
            }
            java.util.ArrayList<Object> result = new java.util.ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(sanitizeValue(Array.get(value, index), rootName, depth + 1, rootMaxKeys, true));
            }
            return result;
        }
        if (value instanceof String string) {
            validateLength(string, rootName + " value", effectiveMaxStringValueLength());
            return redactor.redactText(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        throw reject(rootName + " contains an unsupported value type");
    }

    private void validateRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw reject(field + " is required");
        }
        validateLength(value, field, effectiveMaxFieldLength());
    }

    private void validateOptionalLength(String value, String field, int maxLength) {
        if (value != null) {
            validateLength(value, field, maxLength);
        }
    }

    private void validateLength(String value, String field, int maxLength) {
        if (value != null && value.length() > Math.max(1, maxLength)) {
            throw reject(field + " exceeds the configured maximum length");
        }
    }

    private IngestionValidationException reject(String message) {
        validationRejectedCounter.increment();
        return new IngestionValidationException(message);
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private int effectiveMaxBatchSize() {
        return Math.min(Math.max(1, limits.getMaxBatchSize()), 5_000);
    }

    private int effectiveMaxMessageLength() {
        return Math.min(Math.max(1, limits.getMaxMessageLength()), 4_000);
    }

    private int effectiveMaxFieldLength() {
        return Math.min(Math.max(1, limits.getMaxFieldLength()), 4_096);
    }

    private int effectiveMaxStackTraceLength() {
        return Math.min(Math.max(1, limits.getMaxStackTraceLength()), 64_000);
    }

    private int effectiveMaxExceptionMessageLength() {
        return Math.min(Math.max(1, limits.getMaxExceptionMessageLength()), 16_000);
    }

    private int effectiveMaxContextBytes() {
        return Math.min(Math.max(1, limits.getMaxContextSerializedBytes()), 256 * 1024);
    }

    private int effectiveMaxTagBytes() {
        return Math.min(Math.max(1, limits.getMaxTagsSerializedBytes()), 256 * 1024);
    }

    private int effectiveMaxContextKeys() {
        return Math.min(Math.max(1, limits.getMaxContextKeys()), 2_000);
    }

    private int effectiveMaxTagKeys() {
        return Math.min(Math.max(1, limits.getMaxTagKeys()), 2_000);
    }

    private int effectiveMaxKeyLength() {
        return Math.min(Math.max(1, limits.getMaxKeyLength()), 512);
    }

    private int effectiveMaxDepth() {
        return Math.min(Math.max(0, limits.getMaxNestingDepth()), 10);
    }

    private int effectiveMaxCollectionEntries() {
        return Math.min(Math.max(1, limits.getMaxCollectionEntries()), 2_000);
    }

    private int effectiveMaxStringValueLength() {
        return Math.min(Math.max(1, limits.getMaxStringValueLength()), 16_000);
    }
}
