package com.visitor.system.admin.controller;

import com.visitor.system.admin.dto.AdminIntegrationStatusResp;
import com.visitor.system.admin.security.AdminPrincipal;
import com.visitor.system.admin.service.AdminAuditService;
import com.visitor.system.common.ApiResponse;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.dto.HikvisionAccessTargetResp;
import com.visitor.system.visitor.service.HikvisionAccessResourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/integrations")
public class AdminIntegrationController {

    private final VisitorProperties properties;
    private final HikvisionAccessResourceService accessResourceService;
    private final AdminAuditService auditService;

    public AdminIntegrationController(VisitorProperties properties,
                                      HikvisionAccessResourceService accessResourceService,
                                      AdminAuditService auditService) {
        this.properties = properties;
        this.accessResourceService = accessResourceService;
        this.auditService = auditService;
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('integration:view')")
    public ApiResponse<AdminIntegrationStatusResp> status() {
        VisitorProperties.Dingtalk dingtalk = properties.getDingtalk();
        VisitorProperties.Hikvision hikvision = properties.getHikvision();
        VisitorProperties.Storage storage = properties.getStorage();
        List<HikvisionAccessTargetResp> targets = accessResourceService.listAccessTargets();
        return ApiResponse.success(AdminIntegrationStatusResp.builder()
            .dingtalk(AdminIntegrationStatusResp.Integration.builder()
                .enabled(dingtalk.isEnabled())
                .configured(dingtalk.isMockMode() || (StringUtils.isNotBlank(dingtalk.getAppKey()) && StringUtils.isNotBlank(dingtalk.getAppSecret())))
                .mode(dingtalk.isMockMode() ? "MOCK" : "LIVE")
                .endpoint("钉钉开放平台")
                .build())
            .hikvision(AdminIntegrationStatusResp.Integration.builder()
                .enabled(hikvision.isEnabled())
                .configured(StringUtils.isNotBlank(hikvision.getBaseUrl()) && StringUtils.isNotBlank(hikvision.getAppKey()))
                .mode(hikvision.isTrustAll() ? "TRUST_ALL" : "TLS_VERIFY")
                .endpoint(hikvision.getBaseUrl())
                .build())
            .accessControl(AdminIntegrationStatusResp.Integration.builder()
                .enabled(hikvision.getAccess().isEnabled())
                .configured(!targets.isEmpty() || !hikvision.getAccess().getResourceIndexCodes().isEmpty())
                .mode(hikvision.getAccess().isAutoDiscoverResources() ? "AUTO_DISCOVERY" : "STATIC")
                .endpoint(hikvision.getAccess().getResourceQueryPath())
                .build())
            .storage(AdminIntegrationStatusResp.Storage.builder()
                .provider(storage.getProvider())
                .bucket(storage.getBucket())
                .configured("local".equalsIgnoreCase(storage.getProvider()) || StringUtils.isNotBlank(storage.getMinio().getEndpoint()))
                .build())
            .cachedAccessTargets(targets.size())
            .build());
    }

    @GetMapping("/hikvision/access-targets")
    @PreAuthorize("hasAuthority('integration:view')")
    public ApiResponse<List<HikvisionAccessTargetResp>> accessTargets() {
        return ApiResponse.success(accessResourceService.listAccessTargets());
    }

    @PostMapping("/hikvision/access-targets/refresh")
    @PreAuthorize("hasAuthority('integration:operate')")
    public ApiResponse<Void> refresh(Authentication authentication, HttpServletRequest request) {
        accessResourceService.refreshAccessDevices();
        AdminPrincipal principal = (AdminPrincipal) authentication.getPrincipal();
        auditService.success(principal, "INTEGRATION_ACCESS_TARGET_REFRESH", "HIKVISION", "ACCESS_TARGETS",
            "刷新海康门禁资源缓存", clientIp(request), request.getHeader("User-Agent"));
        return ApiResponse.successMessage("海康门禁资源缓存已刷新");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
