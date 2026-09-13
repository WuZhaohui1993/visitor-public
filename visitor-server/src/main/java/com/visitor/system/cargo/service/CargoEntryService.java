package com.visitor.system.cargo.service;

import com.visitor.system.cargo.domain.CargoEntryRecord;
import com.visitor.system.cargo.dto.CargoEntryCreateReq;
import com.visitor.system.cargo.dto.CargoEntryCreateResp;
import com.visitor.system.cargo.repository.CargoEntryRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CargoEntryService {

    private final CargoEntryRecordRepository repository;

    public CargoEntryService(CargoEntryRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CargoEntryCreateResp create(CargoEntryCreateReq request) {
        CargoEntryRecord record = new CargoEntryRecord();
        record.setBizId(UUID.randomUUID().toString());
        record.setSupplierUnit(request.getSupplierUnit().trim());
        record.setReceivingUnit(request.getReceivingUnit().trim());
        record.setGoodsName(request.getGoodsName().trim());
        record.setQuantity(request.getQuantity().trim());
        record.setReceiverName(request.getReceiverName().trim());
        record.setStorageLocation(request.getStorageLocation().trim());
        record.setEntryTime(request.getEntryTime());
        CargoEntryRecord saved = repository.save(record);

        return CargoEntryCreateResp.builder()
            .id(saved.getId())
            .bizId(saved.getBizId())
            .entryTime(saved.getEntryTime())
            .createTime(saved.getCreateTime())
            .build();
    }
}
