package com.khetisetu.event.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Simple API key authentication filter for internal service-to-service communication.
 * Protects all /api/v1/** endpoints with an X-API-Key header check.
 *
 * <p>This allows the main khetisetu backend to call event service APIs securely
 * without a full OAuth/JWT setup.</p>
 */
@Component
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${api.internal.key:khetisetu-internal-api-key-2026}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getRequestURI();

        // Only protect /api/v1/** endpoints
        if (requestPath.startsWith("/api/v1/")) {
            String providedKey = request.getHeader(API_KEY_HEADER);

            if (providedKey == null || !providedKey.equals(internalApiKey)) {
                log.warn("Unauthorized API access attempt to {} from {}", requestPath, request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API key\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
