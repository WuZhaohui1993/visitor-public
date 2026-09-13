package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class AdminRoleResp {
    private final Long id;
    private final String code;
    private final String name;
    private final boolean enabled;
    private final boolean builtIn;
    private final Set<String> permissionCodes;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;
}
