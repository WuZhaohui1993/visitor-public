package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminIdempotencyRecord;
import com.visitor.system.admin.repository.AdminIdempotencyRecordRepository;
import com.visitor.system.common.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AdminIdempotencyService {

    private final AdminIdempotencyRecordRepository repository;

    public AdminIdempotencyService(AdminIdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    public void claim(Long actorUserId, String idempotencyKey, String requestPath) {
        String normalizedKey = StringUtils.trimToNull(idempotencyKey);
        if (normalizedKey == null || normalizedKey.length() < 16 || normalizedKey.length() > 100) {
            throw new BusinessException("Idempotency-Key 必须为16至100位随机字符串");
        }
        String keyHash = sha256(normalizedKey);
        if (repository.existsByActorUserIdAndIdempotencyKeyHash(actorUserId, keyHash)) {
            throw new BusinessException("操作已提交，请勿重复执行");
        }
        AdminIdempotencyRecord record = new AdminIdempotencyRecord();
        record.setActorUserId(actorUserId);
        record.setIdempotencyKeyHash(keyHash);
        record.setRequestPath(StringUtils.abbreviate(requestPath, 160));
        repository.saveAndFlush(record);
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void cleanupExpiredRecords() {
        repository.deleteByCreateTimeBefore(LocalDateTime.now().minusDays(1));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成幂等键摘要", exception);
        }
    }
}
