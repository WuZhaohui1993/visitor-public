package com.visitor.system.admin.repository;

import com.visitor.system.admin.domain.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRoleRepository extends JpaRepository<AdminRole, Long> {
    Optional<AdminRole> findByCode(String code);
}
