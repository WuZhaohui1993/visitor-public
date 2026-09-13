package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminPermissionResp {
    private final Long id;
    private final String code;
    private final String name;
    private final String type;
    private final String routePath;
    private final Integer sortOrder;
}
