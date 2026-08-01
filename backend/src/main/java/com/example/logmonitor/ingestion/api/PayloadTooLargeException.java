package com.example.logmonitor.ingestion.api;

public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException() {
        super("Ingestion request exceeds the configured maximum body size");
    }
}
