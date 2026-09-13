package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VisitorApproveDetailResp {
    private final String bizId;
    private final String recordNo;
    private final String visitorName;
    private final String idCardMasked;
    private final String phoneMasked;
    private final String idCardFrontUrl;
    private final String idCardBackUrl;
    private final String facePhotoUrl;
    private final String visitedUserName;
    private final String visitedDeptName;
    private final String visitReason;
    private final LocalDateTime plannedEntryTime;
    private final LocalDateTime plannedExitTime;
    private final Integer status;
    private final String approveRemark;
    private final LocalDateTime createTime;
    private final LocalDateTime approveTime;
    private final boolean expired;
}
