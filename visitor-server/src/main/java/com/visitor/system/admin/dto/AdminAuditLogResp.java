package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminAuditLogResp {
    private final Long id;
    private final String actorUsername;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final String result;
    private final String summary;
    private final String ipAddress;
    private final LocalDateTime createTime;
}
