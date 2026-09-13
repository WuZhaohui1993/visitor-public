package com.visitor.system.admin.repository;

import com.visitor.system.admin.domain.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminPermissionRepository extends JpaRepository<AdminPermission, Long> {
    Optional<AdminPermission> findByCode(String code);
}
