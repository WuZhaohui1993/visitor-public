package com.visitor.system.admin.repository;

import com.visitor.system.admin.domain.AdminPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminPasswordHistoryRepository extends JpaRepository<AdminPasswordHistory, Long> {
    List<AdminPasswordHistory> findByUserIdOrderByCreateTimeDesc(Long userId);
}
