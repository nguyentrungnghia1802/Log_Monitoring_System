package com.example.logmonitor.livetail.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "livetail")
public class LiveTailProperties {

    @Min(1)
    private int maxConnectionsPerUser = 3;

    @Min(1)
    private int maxConnectionsPerIp = 20;

    @Min(1)
    private int maxSubscriptionsPerSession = 8;

    @Min(1)
    private int inboundCorePoolSize = 2;

    @Min(1)
    private int inboundMaxPoolSize = 8;

    @Min(1)
    private int inboundQueueCapacity = 128;

    @Min(1)
    private int outboundCorePoolSize = 2;

    @Min(1)
    private int outboundMaxPoolSize = 8;

    @Min(1)
    private int outboundQueueCapacity = 256;

    @Min(1)
    private int brokerSendTimeoutMs = 100;

    @Min(1)
    private int messageSizeLimitBytes = 128 * 1024;

    @Min(1)
    private int sendTimeLimitMs = 10_000;

    @Min(1)
    private int sendBufferSizeLimitBytes = 512 * 1024;

    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
        "http://localhost:*",
        "http://127.0.0.1:*"
    ));

    public int getMaxConnectionsPerUser() {
        return maxConnectionsPerUser;
    }

    public void setMaxConnectionsPerUser(int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    public int getMaxConnectionsPerIp() {
        return maxConnectionsPerIp;
    }

    public void setMaxConnectionsPerIp(int maxConnectionsPerIp) {
        this.maxConnectionsPerIp = maxConnectionsPerIp;
    }

    public int getMaxSubscriptionsPerSession() {
        return maxSubscriptionsPerSession;
    }

    public void setMaxSubscriptionsPerSession(int maxSubscriptionsPerSession) {
        this.maxSubscriptionsPerSession = maxSubscriptionsPerSession;
    }

    public int getInboundCorePoolSize() {
        return inboundCorePoolSize;
    }

    public void setInboundCorePoolSize(int inboundCorePoolSize) {
        this.inboundCorePoolSize = inboundCorePoolSize;
    }

    public int getInboundMaxPoolSize() {
        return inboundMaxPoolSize;
    }

    public void setInboundMaxPoolSize(int inboundMaxPoolSize) {
        this.inboundMaxPoolSize = inboundMaxPoolSize;
    }

    public int getInboundQueueCapacity() {
        return inboundQueueCapacity;
    }

    public void setInboundQueueCapacity(int inboundQueueCapacity) {
        this.inboundQueueCapacity = inboundQueueCapacity;
    }

    public int getOutboundCorePoolSize() {
        return outboundCorePoolSize;
    }

    public void setOutboundCorePoolSize(int outboundCorePoolSize) {
        this.outboundCorePoolSize = outboundCorePoolSize;
    }

    public int getOutboundMaxPoolSize() {
        return outboundMaxPoolSize;
    }

    public void setOutboundMaxPoolSize(int outboundMaxPoolSize) {
        this.outboundMaxPoolSize = outboundMaxPoolSize;
    }

    public int getOutboundQueueCapacity() {
        return outboundQueueCapacity;
    }

    public void setOutboundQueueCapacity(int outboundQueueCapacity) {
        this.outboundQueueCapacity = outboundQueueCapacity;
    }

    public int getBrokerSendTimeoutMs() {
        return brokerSendTimeoutMs;
    }

    public void setBrokerSendTimeoutMs(int brokerSendTimeoutMs) {
        this.brokerSendTimeoutMs = brokerSendTimeoutMs;
    }

    public int getMessageSizeLimitBytes() {
        return messageSizeLimitBytes;
    }

    public void setMessageSizeLimitBytes(int messageSizeLimitBytes) {
        this.messageSizeLimitBytes = messageSizeLimitBytes;
    }

    public int getSendTimeLimitMs() {
        return sendTimeLimitMs;
    }

    public void setSendTimeLimitMs(int sendTimeLimitMs) {
        this.sendTimeLimitMs = sendTimeLimitMs;
    }

    public int getSendBufferSizeLimitBytes() {
        return sendBufferSizeLimitBytes;
    }

    public void setSendBufferSizeLimitBytes(int sendBufferSizeLimitBytes) {
        this.sendBufferSizeLimitBytes = sendBufferSizeLimitBytes;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns == null
            ? new ArrayList<>()
            : new ArrayList<>(allowedOriginPatterns);
    }
}
