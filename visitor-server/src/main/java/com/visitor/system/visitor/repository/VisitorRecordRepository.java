package com.visitor.system.visitor.repository;

import com.visitor.system.visitor.domain.VisitorRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VisitorRecordRepository extends JpaRepository<VisitorRecord, Long>, JpaSpecificationExecutor<VisitorRecord> {

    Optional<VisitorRecord> findByBizId(String bizId);

    Optional<VisitorRecord> findTopByIdCardNoAndIdNotAndHikPersonIdIsNotNullOrderByCreateTimeDesc(String idCardNo, Long id);

    List<VisitorRecord> findTop10ByPhoneAndIdCardSuffixOrderByCreateTimeDesc(String phone, String idCardSuffix);

    @Query("""
        select record from VisitorRecord record
        where record.status = 1
          and record.plannedExitTime > :now
          and (
            record.hikSyncStatus is null
            or record.hikSyncStatus in :statuses
          )
        order by record.createTime asc
        """)
    List<VisitorRecord> findRecordsForHikSyncRetry(@Param("statuses") Collection<com.visitor.system.visitor.domain.HikSyncStatus> statuses,
                                                   @Param("now") LocalDateTime now,
                                                   Pageable pageable);

    @Query("""
        select record from VisitorRecord record
        where record.status = 1
          and record.hikSyncStatus = :status
          and record.plannedExitTime <= :now
          and record.hikDisabledTime is null
        order by record.plannedExitTime asc
        """)
    List<VisitorRecord> findRecordsForHikDisable(@Param("status") com.visitor.system.visitor.domain.HikSyncStatus status,
                                                 @Param("now") LocalDateTime now,
                                                 Pageable pageable);

    @Query("""
        select record from VisitorRecord record
        where record.status = 1
          and record.plannedExitTime > :now
          and record.hikSyncStatus = com.visitor.system.visitor.domain.HikSyncStatus.SUCCESS
          and (
            record.hikAccessStatus is null
            or record.hikAccessStatus in :statuses
          )
        order by record.createTime asc
        """)
    List<VisitorRecord> findRecordsForHikAccessRetry(@Param("statuses") Collection<com.visitor.system.visitor.domain.HikSyncStatus> statuses,
                                                     @Param("now") LocalDateTime now,
                                                     Pageable pageable);
}
