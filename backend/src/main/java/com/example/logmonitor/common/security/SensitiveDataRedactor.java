package com.example.logmonitor.common.security;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SensitiveDataRedactor {

    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
        "(?i)(\\b(?:password|passwd|secret|token|api[-_]?key|authorization|cookie|private[-_]?key|client[-_]?secret)\\b\\s*[:=]\\s*)([^\\s,;]+)"
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)\\bBearer\\s+[^\\s,;]+");

    private final RedactionProperties properties;
    private final Set<String> configuredFields;

    public SensitiveDataRedactor(RedactionProperties properties) {
        this.properties = properties;
        this.configuredFields = properties.getFields().stream()
            .map(SensitiveDataRedactor::normalizeKey)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isSensitiveKey(String key) {
        return isEnabled() && key != null && configuredFields.contains(normalizeKey(key));
    }

    public String replacement() {
        return properties.getReplacement();
    }

    public String redactText(String value) {
        if (!isEnabled() || value == null || value.isEmpty()) {
            return value;
        }

        String redacted = replaceMatches(KEY_VALUE_SECRET, value);
        return BEARER_TOKEN.matcher(redacted).replaceAll(Matcher.quoteReplacement(
            "Bearer " + replacement()));
    }

    private String replaceMatches(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                matcher.group(1) + replacement()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String normalizeKey(String value) {
        return value == null
            ? ""
            : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
