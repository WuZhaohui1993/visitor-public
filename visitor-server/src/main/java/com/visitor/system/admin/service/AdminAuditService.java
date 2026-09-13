package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminAuditLog;
import com.visitor.system.admin.repository.AdminAuditLogRepository;
import com.visitor.system.admin.security.AdminPrincipal;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {

    private final AdminAuditLogRepository repository;

    public AdminAuditService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    public void success(AdminPrincipal actor, String action, String targetType, String targetId,
                        String summary, String ipAddress, String userAgent) {
        record(actor, action, targetType, targetId, "SUCCESS", summary, ipAddress, userAgent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String username, String action, String summary, String ipAddress, String userAgent) {
        AdminAuditLog log = base(action, "FAILED", summary, ipAddress, userAgent);
        log.setActorUsername(StringUtils.abbreviate(username, 40));
        repository.save(log);
    }

    private void record(AdminPrincipal actor, String action, String targetType, String targetId,
                        String result, String summary, String ipAddress, String userAgent) {
        AdminAuditLog log = base(action, result, summary, ipAddress, userAgent);
        if (actor != null) {
            log.setActorUserId(actor.userId());
            log.setActorUsername(actor.username());
        }
        log.setTargetType(StringUtils.abbreviate(targetType, 40));
        log.setTargetId(StringUtils.abbreviate(targetId, 100));
        repository.save(log);
    }

    private AdminAuditLog base(String action, String result, String summary, String ipAddress, String userAgent) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAction(StringUtils.abbreviate(action, 80));
        log.setResult(result);
        log.setSummary(StringUtils.abbreviate(summary, 500));
        log.setIpAddress(StringUtils.abbreviate(ipAddress, 64));
        log.setUserAgent(StringUtils.abbreviate(userAgent, 300));
        return log;
    }
}
