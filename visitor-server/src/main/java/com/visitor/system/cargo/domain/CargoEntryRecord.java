package com.visitor.system.cargo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "cargo_entry_record",
    indexes = {
        @Index(name = "idx_cargo_entry_entry_time", columnList = "entryTime"),
        @Index(name = "idx_cargo_entry_create_time", columnList = "createTime")
    }
)
public class CargoEntryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36, unique = true)
    private String bizId;

    @Column(nullable = false, length = 100)
    private String supplierUnit;

    @Column(nullable = false, length = 100)
    private String receivingUnit;

    @Column(nullable = false, length = 100)
    private String goodsName;

    @Column(nullable = false, length = 50)
    private String quantity;

    @Column(nullable = false, length = 50)
    private String receiverName;

    @Column(nullable = false, length = 100)
    private String storageLocation;

    @Column(nullable = false)
    private LocalDateTime entryTime;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
