package com.example.logmonitor.ingestion.application;

public class IngestionValidationException extends RuntimeException {

    public IngestionValidationException(String message) {
        super(message);
    }
}
