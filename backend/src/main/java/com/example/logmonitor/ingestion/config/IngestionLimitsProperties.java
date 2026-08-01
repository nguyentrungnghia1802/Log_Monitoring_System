package com.example.logmonitor.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion.limits")
public class IngestionLimitsProperties {

    private int maxHttpBodyBytes = 1_048_576;
    private int maxBatchSize = 500;
    private int maxMessageLength = 4_000;
    private int maxStackTraceLength = 16_000;
    private int maxExceptionMessageLength = 4_000;
    private int maxFieldLength = 256;
    private int maxContextSerializedBytes = 32_768;
    private int maxTagsSerializedBytes = 16_384;
    private int maxContextKeys = 50;
    private int maxTagKeys = 50;
    private int maxKeyLength = 100;
    private int maxNestingDepth = 5;
    private int maxCollectionEntries = 100;
    private int maxStringValueLength = 4_000;

    public int getMaxHttpBodyBytes() { return maxHttpBodyBytes; }
    public void setMaxHttpBodyBytes(int maxHttpBodyBytes) { this.maxHttpBodyBytes = maxHttpBodyBytes; }

    public int getMaxBatchSize() { return maxBatchSize; }
    public void setMaxBatchSize(int maxBatchSize) { this.maxBatchSize = maxBatchSize; }

    public int getMaxMessageLength() { return maxMessageLength; }
    public void setMaxMessageLength(int maxMessageLength) { this.maxMessageLength = maxMessageLength; }

    public int getMaxStackTraceLength() { return maxStackTraceLength; }
    public void setMaxStackTraceLength(int maxStackTraceLength) { this.maxStackTraceLength = maxStackTraceLength; }

    public int getMaxExceptionMessageLength() { return maxExceptionMessageLength; }
    public void setMaxExceptionMessageLength(int maxExceptionMessageLength) {
        this.maxExceptionMessageLength = maxExceptionMessageLength;
    }

    public int getMaxFieldLength() { return maxFieldLength; }
    public void setMaxFieldLength(int maxFieldLength) { this.maxFieldLength = maxFieldLength; }

    public int getMaxContextSerializedBytes() { return maxContextSerializedBytes; }
    public void setMaxContextSerializedBytes(int maxContextSerializedBytes) {
        this.maxContextSerializedBytes = maxContextSerializedBytes;
    }

    public int getMaxTagsSerializedBytes() { return maxTagsSerializedBytes; }
    public void setMaxTagsSerializedBytes(int maxTagsSerializedBytes) {
        this.maxTagsSerializedBytes = maxTagsSerializedBytes;
    }

    public int getMaxContextKeys() { return maxContextKeys; }
    public void setMaxContextKeys(int maxContextKeys) { this.maxContextKeys = maxContextKeys; }

    public int getMaxTagKeys() { return maxTagKeys; }
    public void setMaxTagKeys(int maxTagKeys) { this.maxTagKeys = maxTagKeys; }

    public int getMaxKeyLength() { return maxKeyLength; }
    public void setMaxKeyLength(int maxKeyLength) { this.maxKeyLength = maxKeyLength; }

    public int getMaxNestingDepth() { return maxNestingDepth; }
    public void setMaxNestingDepth(int maxNestingDepth) { this.maxNestingDepth = maxNestingDepth; }

    public int getMaxCollectionEntries() { return maxCollectionEntries; }
    public void setMaxCollectionEntries(int maxCollectionEntries) {
        this.maxCollectionEntries = maxCollectionEntries;
    }

    public int getMaxStringValueLength() { return maxStringValueLength; }
    public void setMaxStringValueLength(int maxStringValueLength) {
        this.maxStringValueLength = maxStringValueLength;
    }
}
