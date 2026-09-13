package com.visitor.system.admin.repository;

import com.visitor.system.admin.domain.AdminIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AdminIdempotencyRecordRepository extends JpaRepository<AdminIdempotencyRecord, Long> {
    boolean existsByActorUserIdAndIdempotencyKeyHash(Long actorUserId, String idempotencyKeyHash);
    long deleteByCreateTimeBefore(LocalDateTime threshold);
}
