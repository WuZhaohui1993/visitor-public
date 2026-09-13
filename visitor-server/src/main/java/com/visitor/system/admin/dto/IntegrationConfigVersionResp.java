package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class IntegrationConfigVersionResp {
    private final Long id;
    private final Long versionNo;
    private final String status;
    private final String testStatus;
    private final String testSummary;
    private final LocalDateTime testedTime;
    private final Long createdBy;
    private final Long submittedBy;
    private final Long reviewedBy;
    private final LocalDateTime submittedTime;
    private final LocalDateTime publishedTime;
    private final LocalDateTime createTime;
}
