package com.visitor.system.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.repository.AdminUserRepository;
import com.visitor.system.admin.service.AdminAccessService;
import com.visitor.system.common.ApiResponse;
import com.visitor.system.common.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private final AdminTokenService tokenService;
    private final AdminUserRepository userRepository;
    private final AdminAccessService accessService;
    private final ObjectMapper objectMapper;

    public AdminAuthenticationFilter(AdminTokenService tokenService,
                                     AdminUserRepository userRepository,
                                     AdminAccessService accessService,
                                     ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/admin/");
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
            AdminTokenService.TokenClaims claims = tokenService.parse(authorization.substring(7).trim());
            AdminUser user = userRepository.findById(claims.userId())
                .orElseThrow(() -> new UnauthorizedException("管理员账号不存在"));
            if (!user.isEnabled() || !user.getSecurityVersion().equals(claims.securityVersion())) {
                throw new UnauthorizedException("管理员登录状态已失效，请重新登录");
            }
            AdminPrincipal principal = accessService.principal(user);
            if (principal.mustChangePassword() && !isPasswordSetupRequest(request.getRequestURI())) {
                throw new UnauthorizedException("首次登录必须先修改密码");
            }
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
            principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                authorization.substring(7).trim(),
                authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException exception) {
            writeUnauthorized(response, exception.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isPasswordSetupRequest(String uri) {
        return uri.equals("/api/admin/auth/me")
            || uri.equals("/api/admin/auth/password")
            || uri.equals("/api/admin/auth/logout");
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(message));
    }
}
