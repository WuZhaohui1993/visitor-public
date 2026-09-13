package com.visitor.system.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.common.ApiResponse;
import com.visitor.system.common.UnauthorizedException;
import com.visitor.system.visitor.service.VisitorSecurityTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class VisitorAuthenticationFilter extends OncePerRequestFilter {

    private final VisitorSecurityTokenService visitorSecurityTokenService;
    private final ObjectMapper objectMapper;

    public VisitorAuthenticationFilter(VisitorSecurityTokenService visitorSecurityTokenService,
                                       ObjectMapper objectMapper) {
        this.visitorSecurityTokenService = visitorSecurityTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String accessToken = authorization.substring(7).trim();
            String userId = visitorSecurityTokenService.requireUserAccess(accessToken);
            UsernamePasswordAuthenticationToken authenticationToken = UsernamePasswordAuthenticationToken.authenticated(
                userId,
                accessToken,
                AuthorityUtils.createAuthorityList("ROLE_VISITOR_USER")
            );
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            writeUnauthorized(response, exception.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(message));
    }
}
