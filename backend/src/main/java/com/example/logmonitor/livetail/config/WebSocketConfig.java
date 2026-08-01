package com.example.logmonitor.livetail.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(LiveTailProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final LiveTailHandshakeInterceptor handshakeInterceptor;
    private final LiveTailProperties properties;

    public WebSocketConfig(
        StompAuthChannelInterceptor stompAuthChannelInterceptor,
        LiveTailHandshakeInterceptor handshakeInterceptor,
        LiveTailProperties properties
    ) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.handshakeInterceptor = handshakeInterceptor;
        this.properties = properties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-logs")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new))
            .withSockJS();
        registry.addEndpoint("/ws-logs")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOriginPatterns(properties.getAllowedOriginPatterns().toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
        registration.taskExecutor()
            .corePoolSize(properties.getInboundCorePoolSize())
            .maxPoolSize(Math.max(properties.getInboundCorePoolSize(), properties.getInboundMaxPoolSize()))
            .queueCapacity(properties.getInboundQueueCapacity());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
            .corePoolSize(properties.getOutboundCorePoolSize())
            .maxPoolSize(Math.max(properties.getOutboundCorePoolSize(), properties.getOutboundMaxPoolSize()))
            .queueCapacity(properties.getOutboundQueueCapacity());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
            .setMessageSizeLimit(properties.getMessageSizeLimitBytes())
            .setSendTimeLimit(properties.getSendTimeLimitMs())
            .setSendBufferSizeLimit(properties.getSendBufferSizeLimitBytes());
    }
}
