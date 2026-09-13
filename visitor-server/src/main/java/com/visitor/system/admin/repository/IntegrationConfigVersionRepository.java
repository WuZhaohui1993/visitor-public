package com.visitor.system.admin.repository;

import com.visitor.system.admin.domain.IntegrationConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface IntegrationConfigVersionRepository extends JpaRepository<IntegrationConfigVersion, Long> {
    Optional<IntegrationConfigVersion> findTopByOrderByVersionNoDesc();
    Optional<IntegrationConfigVersion> findTopByStatusOrderByVersionNoDesc(String status);
    List<IntegrationConfigVersion> findAllByOrderByVersionNoDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from IntegrationConfigVersion version where version.id = :id")
    Optional<IntegrationConfigVersion> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IntegrationConfigVersion> findFirstByOrderByVersionNoDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IntegrationConfigVersion> findFirstByStatusOrderByVersionNoDesc(String status);
}
