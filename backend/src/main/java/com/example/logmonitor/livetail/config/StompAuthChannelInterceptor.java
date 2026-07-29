package com.example.logmonitor.livetail.config;

import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.domain.ProjectMembership;
import com.example.logmonitor.auth.domain.ProjectMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^/topic/projects/([^/]+)/livetail$");

    private final JwtService jwtService;
    private final ProjectMembershipRepository membershipRepository;

    public StompAuthChannelInterceptor(JwtService jwtService, ProjectMembershipRepository membershipRepository) {
        this.jwtService = jwtService;
        this.membershipRepository = membershipRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                Optional<JwtService.UserPrincipal> principal = jwtService.validateAndExtractPrincipal(token);
                if (principal.isPresent()) {
                    JwtService.UserPrincipal user = principal.get();
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(() -> "ROLE_USER")
                    );
                    accessor.setUser(auth);
                    log.info("WebSocket STOMP connected for user: {}", user.username());
                } else {
                    log.warn("Invalid JWT token on STOMP CONNECT");
                    throw new AccessDeniedException("Invalid JWT token");
                }
            }
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null) {
                Matcher matcher = TOPIC_PATTERN.matcher(destination);
                if (matcher.matches()) {
                    String projectId = matcher.group(1);
                    Principal userPrincipal = accessor.getUser();

                    if (userPrincipal == null) {
                        log.warn("Unauthenticated STOMP subscription attempt to: {}", destination);
                        throw new AccessDeniedException("Unauthenticated WebSocket subscription");
                    }

                    if (userPrincipal instanceof UsernamePasswordAuthenticationToken auth
                        && auth.getPrincipal() instanceof JwtService.UserPrincipal user) {

                        Optional<ProjectMembership> membership = membershipRepository.findByUserIdAndProjectId(user.userId(), projectId);
                        if (membership.isEmpty()) {
                            log.warn("User {} unauthorized to subscribe to project {}", user.username(), projectId);
                            throw new AccessDeniedException("User not authorized for project livetail stream");
                        }
                    }
                }
            }
        }
        return message;
    }
}
