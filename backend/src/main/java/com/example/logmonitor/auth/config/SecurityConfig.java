package com.example.logmonitor.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .anonymous(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((request, response, exception) -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    boolean unauthenticated = authentication == null
                        || !authentication.isAuthenticated()
                        || "anonymousUser".equals(authentication.getPrincipal());
                    response.sendError(unauthenticated
                        ? HttpServletResponse.SC_UNAUTHORIZED
                        : HttpServletResponse.SC_FORBIDDEN);
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/ws-logs/**").permitAll() // Handled via StompAuthChannelInterceptor
                .requestMatchers("/api/v1/ingest/**").permitAll() // Handled via ApiKeyAuthenticationFilter
                .requestMatchers("/api/v1/system/**").authenticated()
                .requestMatchers("/api/v1/**").permitAll() // Handled via ProjectSecurityInterceptor & JwtAuthenticationFilter fallback
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type", "X-API-Key"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
