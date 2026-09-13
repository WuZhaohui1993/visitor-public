package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HikvisionRevokeAccessCommand {
    private final String personId;
    private final String resourceType;
    private final List<HikvisionAccessResourceInfo> resourceInfos;
}
