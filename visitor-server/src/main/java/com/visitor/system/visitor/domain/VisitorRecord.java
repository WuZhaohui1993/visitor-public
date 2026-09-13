package com.visitor.system.visitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    name = "visitor_record",
    indexes = {
        @Index(name = "idx_visitor_record_status_create_time", columnList = "status, createTime"),
        @Index(name = "idx_visitor_record_hik_sync_status", columnList = "hikSyncStatus, plannedExitTime, createTime"),
        @Index(name = "idx_visitor_record_hik_access_status", columnList = "hikAccessStatus, plannedExitTime, createTime"),
        @Index(name = "idx_visitor_record_planned_exit_time", columnList = "plannedExitTime")
    }
)
public class VisitorRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36, unique = true)
    private String bizId;

    @Column(nullable = false, length = 30, unique = true)
    private String recordNo;

    @Column(nullable = false, length = 20)
    private String visitorName;

    @Column(nullable = false, length = 128)
    private String idCardNo;

    @Column(nullable = false, length = 6)
    private String idCardSuffix;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 200)
    private String idCardFront;

    @Column(length = 200)
    private String idCardBack;

    @Column(length = 200)
    private String facePhoto;

    @Column(nullable = false, length = 64)
    private String visitedUserId;

    @Column(length = 50)
    private String visitedUserName;

    @Column(length = 100)
    private String visitedDeptName;

    @Column(length = 200)
    private String visitReason;

    @Column(nullable = false)
    private LocalDateTime plannedEntryTime;

    @Column(nullable = false)
    private LocalDateTime plannedExitTime;

    @Column(nullable = false)
    private Integer status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private HikSyncStatus hikSyncStatus;

    @Column(length = 64)
    private String hikPersonCode;

    @Column(length = 64)
    private String hikPersonId;

    @Column(length = 64)
    private String hikFaceIndexCode;

    @Column(length = 500)
    private String hikFacePicUrl;

    @Column(length = 500)
    private String hikSyncError;

    private LocalDateTime hikSyncTime;

    private Integer hikRetryCount;

    private LocalDateTime hikLastRetryTime;

    private LocalDateTime hikDisabledTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private HikSyncStatus hikAccessStatus;

    @Column(length = 500)
    private String hikAccessError;

    private LocalDateTime hikAccessSyncTime;

    private Integer hikAccessRetryCount;

    private LocalDateTime hikAccessLastRetryTime;

    private LocalDateTime hikAccessDisabledTime;

    @Column(length = 200)
    private String approveRemark;

    @Column(length = 400)
    private String approveToken;

    @Column(nullable = false)
    private LocalDateTime tokenExpireTime;

    @Column(nullable = false)
    private LocalDateTime expireTime;

    @Column(nullable = false)
    private LocalDateTime createTime;

    private LocalDateTime approveTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (hikRetryCount == null) {
            hikRetryCount = 0;
        }
        if (hikAccessRetryCount == null) {
            hikAccessRetryCount = 0;
        }
    }
}
