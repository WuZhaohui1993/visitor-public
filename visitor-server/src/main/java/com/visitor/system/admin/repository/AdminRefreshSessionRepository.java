package com.visitor.system.admin.repository;

import com.visitor.system.admin.domain.AdminRefreshSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AdminRefreshSessionRepository extends JpaRepository<AdminRefreshSession, Long> {
    Optional<AdminRefreshSession> findByTokenHashAndRevokedTimeIsNull(String tokenHash);
    List<AdminRefreshSession> findByUserIdAndRevokedTimeIsNullAndExpiresTimeAfter(Long userId, LocalDateTime now);
}
