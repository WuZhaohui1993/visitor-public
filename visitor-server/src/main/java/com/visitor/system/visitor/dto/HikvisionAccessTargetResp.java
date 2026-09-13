package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HikvisionAccessTargetResp {
    private final String resourceIndexCode;
    private final Integer channelNo;
    private final String sourceIndexCode;
    private final String sourceName;
    private final String sourceResourceType;
}
