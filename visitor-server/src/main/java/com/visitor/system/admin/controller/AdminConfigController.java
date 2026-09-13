package com.visitor.system.admin.controller;

import com.visitor.system.admin.dto.IntegrationConfigPayload;
import com.visitor.system.admin.dto.IntegrationConfigVersionResp;
import com.visitor.system.admin.dto.IntegrationConfigViewResp;
import com.visitor.system.admin.security.AdminPrincipal;
import com.visitor.system.admin.service.AdminAuditService;
import com.visitor.system.admin.service.IntegrationConfigService;
import com.visitor.system.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/configs")
public class AdminConfigController {

    private final IntegrationConfigService configService;
    private final AdminAuditService auditService;

    public AdminConfigController(IntegrationConfigService configService, AdminAuditService auditService) {
        this.configService = configService;
        this.auditService = auditService;
    }

    @GetMapping("/effective")
    @PreAuthorize("hasAuthority('config:view')")
    public ApiResponse<IntegrationConfigViewResp> effective() {
        return ApiResponse.success(configService.effective());
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('config:view')")
    public ApiResponse<List<IntegrationConfigVersionResp>> versions() {
        return ApiResponse.success(configService.versions());
    }

    @PostMapping("/drafts")
    @PreAuthorize("hasAuthority('config:edit')")
    public ApiResponse<IntegrationConfigVersionResp> createDraft(@RequestBody IntegrationConfigPayload request,
                                                                 Authentication authentication,
                                                                 HttpServletRequest servletRequest) {
        AdminPrincipal actor = principal(authentication);
        IntegrationConfigVersionResp result = configService.createDraft(request, actor.userId());
        audit(actor, servletRequest, "CONFIG_DRAFT_CREATE", result, "创建集成配置草稿");
        return ApiResponse.success(result);
    }

    @PostMapping("/{versionId}/test")
    @PreAuthorize("hasAuthority('config:test')")
    public ApiResponse<IntegrationConfigVersionResp> test(@PathVariable Long versionId,
                                                          Authentication authentication,
                                                          HttpServletRequest servletRequest) {
        AdminPrincipal actor = principal(authentication);
        IntegrationConfigVersionResp result = configService.test(versionId, actor.userId());
        audit(actor, servletRequest, "CONFIG_TEST", result, "测试集成配置：" + result.getTestStatus());
        return ApiResponse.success(result);
    }

    @PostMapping("/{versionId}/submit")
    @PreAuthorize("hasAuthority('config:submit')")
    public ApiResponse<IntegrationConfigVersionResp> submit(@PathVariable Long versionId,
                                                            Authentication authentication,
                                                            HttpServletRequest servletRequest) {
        AdminPrincipal actor = principal(authentication);
        IntegrationConfigVersionResp result = configService.submit(versionId, actor.userId());
        audit(actor, servletRequest, "CONFIG_SUBMIT", result, "提交集成配置复核");
        return ApiResponse.success(result);
    }

    @PostMapping("/{versionId}/publish")
    @PreAuthorize("hasAuthority('config:publish')")
    public ApiResponse<IntegrationConfigVersionResp> publish(@PathVariable Long versionId,
                                                             Authentication authentication,
                                                             HttpServletRequest servletRequest) {
        AdminPrincipal actor = principal(authentication);
        IntegrationConfigVersionResp result = configService.publish(versionId, actor.userId());
        audit(actor, servletRequest, "CONFIG_PUBLISH", result, "复核并发布集成配置");
        return ApiResponse.success(result);
    }

    @PostMapping("/{versionId}/rollback-draft")
    @PreAuthorize("hasAuthority('config:submit')")
    public ApiResponse<IntegrationConfigVersionResp> rollbackDraft(@PathVariable Long versionId,
                                                                   Authentication authentication,
                                                                   HttpServletRequest servletRequest) {
        AdminPrincipal actor = principal(authentication);
        IntegrationConfigVersionResp result = configService.createRollbackDraft(versionId, actor.userId());
        audit(actor, servletRequest, "CONFIG_ROLLBACK_SUBMIT", result, "提交配置回滚复核");
        return ApiResponse.success(result);
    }

    private void audit(AdminPrincipal actor, HttpServletRequest request, String action,
                       IntegrationConfigVersionResp version, String summary) {
        auditService.success(actor, action, "INTEGRATION_CONFIG", String.valueOf(version.getId()),
            summary + " v" + version.getVersionNo(), clientIp(request), request.getHeader("User-Agent"));
    }

    private AdminPrincipal principal(Authentication authentication) {
        return (AdminPrincipal) authentication.getPrincipal();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
