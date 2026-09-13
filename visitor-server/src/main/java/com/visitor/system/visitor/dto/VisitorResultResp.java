package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VisitorResultResp {
    private final String bizId;
    private final String resultAccessToken;
    private final String recordNo;
    private final String visitorName;
    private final String idCardMasked;
    private final String phoneMasked;
    private final String visitedUserName;
    private final String visitedDeptName;
    private final String visitReason;
    private final LocalDateTime plannedEntryTime;
    private final LocalDateTime plannedExitTime;
    private final Integer status;
    private final String statusText;
    private final String hikSyncStatus;
    private final String hikSyncStatusText;
    private final String hikSyncError;
    private final String hikAccessStatus;
    private final String hikAccessStatusText;
    private final String hikAccessError;
    private final boolean entryAllowed;
    private final String entryMessage;
    private final String approveRemark;
    private final LocalDateTime approveTime;
    private final LocalDateTime createTime;
    private final boolean expired;
}
