package com.visitor.system.visitor.repository;

import com.visitor.system.visitor.domain.UploadFileRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadFileRecordRepository extends JpaRepository<UploadFileRecord, Long> {

    Optional<UploadFileRecord> findByFileKey(String fileKey);

    List<UploadFileRecord> findByLinkedFalseAndCreateTimeBefore(LocalDateTime time);
}
