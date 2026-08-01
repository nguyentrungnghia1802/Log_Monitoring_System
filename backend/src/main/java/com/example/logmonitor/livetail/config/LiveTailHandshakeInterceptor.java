package com.example.logmonitor.livetail.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.Map;

@Component
public class LiveTailHandshakeInterceptor implements HandshakeInterceptor {

    public static final String REMOTE_ADDRESS_ATTRIBUTE = "livetail.remote-address";

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String address = "unknown";
        if (remoteAddress != null) {
            address = remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : remoteAddress.getHostString();
        }
        attributes.put(REMOTE_ADDRESS_ATTRIBUTE, address);
        return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        // No additional cleanup is needed here; SessionDisconnectEvent owns it.
    }
}
