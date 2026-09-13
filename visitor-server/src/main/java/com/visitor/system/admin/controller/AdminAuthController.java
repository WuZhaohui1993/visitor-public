package com.visitor.system.admin.controller;

import com.visitor.system.admin.dto.AdminChangePasswordReq;
import com.visitor.system.admin.dto.AdminCaptchaResp;
import com.visitor.system.admin.dto.AdminLoginReq;
import com.visitor.system.admin.dto.AdminLoginResp;
import com.visitor.system.admin.dto.AdminProfileResp;
import com.visitor.system.admin.dto.AdminRouteResp;
import com.visitor.system.admin.security.AdminPrincipal;
import com.visitor.system.admin.service.AdminAuthService;
import com.visitor.system.admin.service.AdminCaptchaService;
import com.visitor.system.admin.service.AdminRouteService;
import com.visitor.system.common.ApiResponse;
import com.visitor.system.config.VisitorProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    public static final String REFRESH_COOKIE = "visitor_admin_refresh";

    private final AdminAuthService authService;
    private final AdminCaptchaService captchaService;
    private final AdminRouteService routeService;
    private final VisitorProperties properties;

    public AdminAuthController(AdminAuthService authService,
                               AdminCaptchaService captchaService,
                               AdminRouteService routeService,
                               VisitorProperties properties) {
        this.authService = authService;
        this.captchaService = captchaService;
        this.routeService = routeService;
        this.properties = properties;
    }

    @GetMapping("/captcha")
    public ApiResponse<AdminCaptchaResp> captcha(@RequestParam String username,
                                                 HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponse.success(captchaService.issue(username));
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginResp> login(@Valid @RequestBody AdminLoginReq request,
                                             HttpServletRequest servletRequest,
                                             HttpServletResponse servletResponse) {
        AdminAuthService.AuthBundle bundle = authService.login(request, clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        setRefreshCookie(servletResponse, bundle.refreshToken(), bundle.refreshExpiresAt());
        return ApiResponse.success(bundle.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<AdminLoginResp> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookie(request, REFRESH_COOKIE);
        AdminAuthService.AuthBundle bundle = authService.refresh(refreshToken, clientIp(request), request.getHeader("User-Agent"));
        setRefreshCookie(response, bundle.refreshToken(), bundle.refreshExpiresAt());
        return ApiResponse.success(bundle.response());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        AdminPrincipal principal = authentication != null && authentication.getPrincipal() instanceof AdminPrincipal actor
            ? actor
            : null;
        authService.logout(cookie(request, REFRESH_COOKIE), principal, clientIp(request), request.getHeader("User-Agent"));
        clearRefreshCookie(response);
        return ApiResponse.successMessage("已安全退出");
    }

    @GetMapping("/me")
    public ApiResponse<AdminProfileResp> me(Authentication authentication) {
        AdminPrincipal principal = principal(authentication);
        return ApiResponse.success(authService.profile(principal.userId()));
    }

    @GetMapping("/routes")
    public ApiResponse<List<AdminRouteResp>> routes(Authentication authentication) {
        return ApiResponse.success(routeService.routes(principal(authentication)));
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AdminChangePasswordReq request,
                                            Authentication authentication,
                                            HttpServletRequest servletRequest,
                                            HttpServletResponse servletResponse) {
        AdminPrincipal principal = principal(authentication);
        authService.changePassword(principal.userId(), request, clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        clearRefreshCookie(servletResponse);
        return ApiResponse.successMessage("密码已修改，请重新登录");
    }

    private AdminPrincipal principal(Authentication authentication) {
        return (AdminPrincipal) authentication.getPrincipal();
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken, LocalDateTime expiresAt) {
        long maxAge = Math.max(1, Duration.between(LocalDateTime.now(), expiresAt).toSeconds());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(properties.getAdmin().isSecureCookie())
            .sameSite("Strict")
            .path("/api/admin/auth")
            .maxAge(maxAge)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(properties.getAdmin().isSecureCookie())
            .sameSite("Strict")
            .path("/api/admin/auth")
            .maxAge(0)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
