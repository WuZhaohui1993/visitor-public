package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminVisitorRecordResp {
    private final String bizId;
    private final String recordNo;
    private final String visitorName;
    private final String idCardNo;
    private final String phone;
    private final String visitedUserId;
    private final String visitedUserName;
    private final String visitedDeptName;
    private final String visitReason;
    private final LocalDateTime plannedEntryTime;
    private final LocalDateTime plannedExitTime;
    private final Integer status;
    private final String statusText;
    private final String hikSyncStatus;
    private final String hikSyncError;
    private final LocalDateTime hikSyncTime;
    private final Integer hikRetryCount;
    private final String hikAccessStatus;
    private final String hikAccessError;
    private final LocalDateTime hikAccessSyncTime;
    private final Integer hikAccessRetryCount;
    private final String approveRemark;
    private final LocalDateTime approveTime;
    private final LocalDateTime createTime;
    private final boolean expired;
    private final boolean hasIdCardFront;
    private final boolean hasIdCardBack;
    private final boolean hasFacePhoto;
}
