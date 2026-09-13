package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntegrationConfigViewResp {
    private final Long activeVersionId;
    private final Long activeVersionNo;
    private final IntegrationConfigPayload config;
}
