package com.example.logmonitor.alerting.application;

public class AlertOperationException extends RuntimeException {

    public enum Kind {
        VALIDATION,
        CONFLICT
    }

    private final Kind kind;
    private final String code;

    public AlertOperationException(Kind kind, String code, String message) {
        super(message);
        this.kind = kind;
        this.code = code;
    }

    public Kind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }
}
