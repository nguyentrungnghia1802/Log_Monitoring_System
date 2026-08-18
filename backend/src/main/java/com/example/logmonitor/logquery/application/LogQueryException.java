package com.example.logmonitor.logquery.application;

public class LogQueryException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        UNPROCESSABLE_ENTITY
    }

    private final String code;
    private final Kind kind;

    public LogQueryException(String code, String message, Kind kind) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public String getCode() {
        return code;
    }

    public Kind getKind() {
        return kind;
    }
}
