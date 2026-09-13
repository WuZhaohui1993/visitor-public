package com.visitor.system.visitor.repository;

import com.visitor.system.visitor.domain.VisitorRecordSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface VisitorRecordSequenceRepository extends JpaRepository<VisitorRecordSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sequence from VisitorRecordSequence sequence where sequence.sequenceDate = :sequenceDate")
    Optional<VisitorRecordSequence> findBySequenceDateForUpdate(@Param("sequenceDate") String sequenceDate);
}
