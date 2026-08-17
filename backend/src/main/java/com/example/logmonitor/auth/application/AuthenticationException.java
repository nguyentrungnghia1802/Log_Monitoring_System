package com.example.logmonitor.auth.application;

public class AuthenticationException extends RuntimeException {

    private final String code;
    private final Kind kind;

    public AuthenticationException(String code, Kind kind, String message) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public String getCode() { return code; }
    public Kind getKind() { return kind; }

    public enum Kind {
        UNAUTHORIZED,
        RATE_LIMITED
    }
}
