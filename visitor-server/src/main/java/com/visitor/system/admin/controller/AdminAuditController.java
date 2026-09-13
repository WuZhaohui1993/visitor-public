package com.visitor.system.admin.controller;

import com.visitor.system.admin.dto.AdminAuditLogResp;
import com.visitor.system.admin.dto.AdminPageResp;
import com.visitor.system.admin.service.AdminAuditQueryService;
import com.visitor.system.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

    private final AdminAuditQueryService auditQueryService;

    public AdminAuditController(AdminAuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:view')")
    public ApiResponse<AdminPageResp<AdminAuditLogResp>> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String result,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(auditQueryService.list(keyword, action, result, from, to, page, size));
    }
}
