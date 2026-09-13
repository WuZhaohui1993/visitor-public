package com.visitor.system.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "integration_config_version",
    indexes = {
        @Index(name = "idx_integration_config_status", columnList = "status, versionNo"),
        @Index(name = "idx_integration_config_create_time", columnList = "createTime")
    }
)
public class IntegrationConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long versionNo;

    @Column(nullable = false, length = 30)
    private String status;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String encryptedPayload;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Column(length = 20)
    private String testStatus;

    @Column(length = 1000)
    private String testSummary;

    private LocalDateTime testedTime;

    @Column(nullable = false)
    private Long createdBy;

    private Long submittedBy;

    private LocalDateTime submittedTime;

    private Long reviewedBy;

    private LocalDateTime reviewedTime;

    private LocalDateTime publishedTime;

    private Long previousVersionId;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createTime = now;
        updateTime = now;
    }

    @PreUpdate
    void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
