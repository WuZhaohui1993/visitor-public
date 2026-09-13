package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class HikvisionGrantAccessCommand {
    private final String personId;
    private final LocalDateTime beginTime;
    private final LocalDateTime endTime;
    private final String resourceType;
    private final List<HikvisionAccessResourceInfo> resourceInfos;
}
