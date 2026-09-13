package com.visitor.system.admin.controller;

import com.visitor.system.admin.dto.AdminPasswordResetReq;
import com.visitor.system.admin.dto.AdminPermissionResp;
import com.visitor.system.admin.dto.AdminRoleResp;
import com.visitor.system.admin.dto.AdminRoleSaveReq;
import com.visitor.system.admin.dto.AdminUserCreateReq;
import com.visitor.system.admin.dto.AdminUserResp;
import com.visitor.system.admin.dto.AdminUserUpdateReq;
import com.visitor.system.admin.security.AdminPrincipal;
import com.visitor.system.admin.service.AdminAuditService;
import com.visitor.system.admin.service.AdminIdentityService;
import com.visitor.system.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminIdentityController {

    private final AdminIdentityService identityService;
    private final AdminAuditService auditService;

    public AdminIdentityController(AdminIdentityService identityService, AdminAuditService auditService) {
        this.identityService = identityService;
        this.auditService = auditService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<List<AdminUserResp>> users() {
        return ApiResponse.success(identityService.users());
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<AdminUserResp> createUser(@Valid @RequestBody AdminUserCreateReq request,
                                                 Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        AdminUserResp result = identityService.createUser(request);
        audit(authentication, servletRequest, "ADMIN_USER_CREATE", "ADMIN_USER", String.valueOf(result.getId()), "创建管理员 " + result.getUsername());
        return ApiResponse.success(result);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<AdminUserResp> updateUser(@PathVariable Long userId,
                                                 @Valid @RequestBody AdminUserUpdateReq request,
                                                 Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        AdminPrincipal actor = principal(authentication);
        AdminUserResp result = identityService.updateUser(userId, actor.userId(), request);
        auditService.success(actor, "ADMIN_USER_UPDATE", "ADMIN_USER", String.valueOf(userId), "修改管理员账号和角色", clientIp(servletRequest), servletRequest.getHeader("User-Agent"));
        return ApiResponse.success(result);
    }

    @PostMapping("/users/{userId}/password-reset")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<AdminUserResp> resetPassword(@PathVariable Long userId,
                                                    @Valid @RequestBody AdminPasswordResetReq request,
                                                    Authentication authentication,
                                                    HttpServletRequest servletRequest) {
        AdminUserResp result = identityService.resetPassword(userId, request);
        audit(authentication, servletRequest, "ADMIN_USER_PASSWORD_RESET", "ADMIN_USER", String.valueOf(userId), "重置管理员密码并强制首次改密");
        return ApiResponse.success(result);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:view')")
    public ApiResponse<List<AdminRoleResp>> roles() {
        return ApiResponse.success(identityService.roles());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<AdminRoleResp> createRole(@Valid @RequestBody AdminRoleSaveReq request,
                                                 Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        AdminRoleResp result = identityService.createRole(request);
        audit(authentication, servletRequest, "ADMIN_ROLE_CREATE", "ADMIN_ROLE", String.valueOf(result.getId()), "创建角色 " + result.getCode());
        return ApiResponse.success(result);
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<AdminRoleResp> updateRole(@PathVariable Long roleId,
                                                 @Valid @RequestBody AdminRoleSaveReq request,
                                                 Authentication authentication,
                                                 HttpServletRequest servletRequest) {
        AdminRoleResp result = identityService.updateRole(roleId, request);
        audit(authentication, servletRequest, "ADMIN_ROLE_UPDATE", "ADMIN_ROLE", String.valueOf(roleId), "修改角色权限 " + result.getCode());
        return ApiResponse.success(result);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('role:view')")
    public ApiResponse<List<AdminPermissionResp>> permissions() {
        return ApiResponse.success(identityService.permissions());
    }

    private void audit(Authentication authentication, HttpServletRequest request, String action,
                       String targetType, String targetId, String summary) {
        auditService.success(principal(authentication), action, targetType, targetId, summary, clientIp(request), request.getHeader("User-Agent"));
    }

    private AdminPrincipal principal(Authentication authentication) {
        return (AdminPrincipal) authentication.getPrincipal();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
