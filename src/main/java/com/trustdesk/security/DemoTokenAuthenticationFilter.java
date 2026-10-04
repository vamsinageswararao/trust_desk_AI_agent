package com.trustdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying {@code Authorization: Bearer <demo token>}.
 * Requests without a valid token proceed unauthenticated and are rejected by the security chain (401).
 */
public class DemoTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final byte[] agentToken;
    private final byte[] managerToken; // null = demo mode: the single token also has the manager role

    public DemoTokenAuthenticationFilter(String agentToken) {
        this(agentToken, null);
    }

    public DemoTokenAuthenticationFilter(String agentToken, String managerToken) {
        if (agentToken == null || agentToken.isBlank()) {
            throw new IllegalArgumentException("Demo token must not be blank");
        }
        this.agentToken = agentToken.getBytes(StandardCharsets.UTF_8);
        this.managerToken = managerToken == null || managerToken.isBlank()
                ? null : managerToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            byte[] provided = header.substring(BEARER_PREFIX.length()).trim().getBytes(StandardCharsets.UTF_8);
            boolean isManager = managerToken != null && MessageDigest.isEqual(provided, managerToken); // constant-time
            boolean isAgent = MessageDigest.isEqual(provided, agentToken);
            if (isManager || isAgent) {
                List<SimpleGrantedAuthority> roles = new ArrayList<>(List.of(new SimpleGrantedAuthority("ROLE_SUPPORT_AGENT")));
                if (isManager || managerToken == null) {
                    roles.add(new SimpleGrantedAuthority("ROLE_SUPPORT_MANAGER"));
                }
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(
                        isManager ? "demo-support-manager" : "demo-support-agent", null, roles));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}
