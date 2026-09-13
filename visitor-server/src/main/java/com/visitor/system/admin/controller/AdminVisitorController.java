package com.visitor.system.admin.controller;

import com.visitor.system.admin.dto.AdminDashboardResp;
import com.visitor.system.admin.dto.AdminPageResp;
import com.visitor.system.admin.dto.AdminVisitorRecordResp;
import com.visitor.system.admin.security.AdminPrincipal;
import com.visitor.system.admin.service.AdminAuditService;
import com.visitor.system.admin.service.AdminVisitorService;
import com.visitor.system.admin.service.AdminRecordOperationService;
import com.visitor.system.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminVisitorController {

    private final AdminVisitorService visitorService;
    private final AdminRecordOperationService operationService;
    private final AdminAuditService auditService;

    public AdminVisitorController(AdminVisitorService visitorService,
                                  AdminRecordOperationService operationService,
                                  AdminAuditService auditService) {
        this.visitorService = visitorService;
        this.operationService = operationService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public ApiResponse<AdminDashboardResp> dashboard() {
        return ApiResponse.success(visitorService.dashboard());
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('record:view')")
    public ApiResponse<AdminPageResp<AdminVisitorRecordResp>> records(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String hikState,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(visitorService.list(keyword, status, hikState, from, to, page, size));
    }

    @GetMapping("/records/export")
    @PreAuthorize("hasAuthority('record:export')")
    public ApiResponse<List<AdminVisitorRecordResp>> export(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) String hikState,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        Authentication authentication,
        HttpServletRequest request
    ) {
        List<AdminVisitorRecordResp> rows = visitorService.export(keyword, status, hikState, from, to);
        auditService.success(principal(authentication), "RECORD_EXPORT", "VISITOR_RECORD", null,
            "导出访客台账 " + rows.size() + " 条", clientIp(request), request.getHeader("User-Agent"));
        return ApiResponse.success(rows);
    }

    @GetMapping("/records/{bizId}")
    @PreAuthorize("hasAuthority('record:view')")
    public ApiResponse<AdminVisitorRecordResp> detail(@PathVariable String bizId,
                                                      Authentication authentication,
                                                      HttpServletRequest request) {
        AdminVisitorRecordResp detail = visitorService.detail(bizId);
        auditService.success(principal(authentication), "RECORD_VIEW_DETAIL", "VISITOR_RECORD", bizId,
            "查看访客明文详情", clientIp(request), request.getHeader("User-Agent"));
        return ApiResponse.success(detail);
    }

    @GetMapping("/records/{bizId}/attachments/{type}")
    @PreAuthorize("hasAuthority('record:view')")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> attachment(@PathVariable String bizId,
                                                                                      @PathVariable String type,
                                                                                      Authentication authentication,
                                                                                      HttpServletRequest request) {
        AdminVisitorService.Attachment attachment = visitorService.openAttachment(bizId, type);
        auditService.success(principal(authentication), "RECORD_VIEW_ATTACHMENT", "VISITOR_RECORD", bizId,
            "查看访客附件：" + type, clientIp(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(attachment.contentType()))
            .body(attachment.resource());
    }

    @PostMapping("/records/{bizId}/operations/hikvision-retry")
    @PreAuthorize("hasAuthority('record:operate')")
    public ApiResponse<AdminVisitorRecordResp> retryHikvision(@PathVariable String bizId,
                                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                              Authentication authentication,
                                                              HttpServletRequest request) {
        AdminPrincipal actor = principal(authentication);
        AdminVisitorRecordResp result = operationService.retryHikvision(bizId, actor.userId(), idempotencyKey);
        auditService.success(principal(authentication), "RECORD_HIKVISION_RETRY", "VISITOR_RECORD", bizId,
            "人工重试海康身份同步", clientIp(request), request.getHeader("User-Agent"));
        return ApiResponse.success(result);
    }

    @PostMapping("/records/{bizId}/operations/access-retry")
    @PreAuthorize("hasAuthority('record:operate')")
    public ApiResponse<AdminVisitorRecordResp> retryAccess(@PathVariable String bizId,
                                                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                           Authentication authentication,
                                                           HttpServletRequest request) {
        AdminPrincipal actor = principal(authentication);
        AdminVisitorRecordResp result = operationService.retryAccess(bizId, actor.userId(), idempotencyKey);
        auditService.success(principal(authentication), "RECORD_ACCESS_RETRY", "VISITOR_RECORD", bizId,
            "人工重试门禁权限下发", clientIp(request), request.getHeader("User-Agent"));
        return ApiResponse.success(result);
    }

    @PostMapping("/records/{bizId}/operations/revoke")
    @PreAuthorize("hasAuthority('record:operate')")
    public ApiResponse<AdminVisitorRecordResp> revoke(@PathVariable String bizId,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      Authentication authentication,
                                                      HttpServletRequest request) {
        AdminPrincipal actor = principal(authentication);
        AdminVisitorRecordResp result = operationService.revokeAccess(bizId, actor.userId(), idempotencyKey);
        auditService.success(principal(authentication), "RECORD_ACCESS_REVOKE", "VISITOR_RECORD", bizId,
            "人工回收海康和门禁权限", clientIp(request), request.getHeader("User-Agent"));
        return ApiResponse.success(result);
    }

    private AdminPrincipal principal(Authentication authentication) {
        return (AdminPrincipal) authentication.getPrincipal();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
