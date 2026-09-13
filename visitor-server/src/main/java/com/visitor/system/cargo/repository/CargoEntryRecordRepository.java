package com.visitor.system.cargo.repository;

import com.visitor.system.cargo.domain.CargoEntryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CargoEntryRecordRepository extends JpaRepository<CargoEntryRecord, Long> {
}
