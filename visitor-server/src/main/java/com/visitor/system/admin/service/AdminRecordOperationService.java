package com.visitor.system.admin.service;

import com.visitor.system.admin.dto.AdminVisitorRecordResp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminRecordOperationService {

    private final AdminIdempotencyService idempotencyService;
    private final AdminVisitorService visitorService;

    public AdminRecordOperationService(AdminIdempotencyService idempotencyService,
                                       AdminVisitorService visitorService) {
        this.idempotencyService = idempotencyService;
        this.visitorService = visitorService;
    }

    @Transactional
    public AdminVisitorRecordResp retryHikvision(String bizId, Long actorUserId, String idempotencyKey) {
        idempotencyService.claim(actorUserId, idempotencyKey, "/records/" + bizId + "/operations/hikvision-retry");
        return visitorService.retryHikvision(bizId);
    }

    @Transactional
    public AdminVisitorRecordResp retryAccess(String bizId, Long actorUserId, String idempotencyKey) {
        idempotencyService.claim(actorUserId, idempotencyKey, "/records/" + bizId + "/operations/access-retry");
        return visitorService.retryAccess(bizId);
    }

    @Transactional
    public AdminVisitorRecordResp revokeAccess(String bizId, Long actorUserId, String idempotencyKey) {
        idempotencyService.claim(actorUserId, idempotencyKey, "/records/" + bizId + "/operations/revoke");
        return visitorService.revokeAccess(bizId);
    }
}
