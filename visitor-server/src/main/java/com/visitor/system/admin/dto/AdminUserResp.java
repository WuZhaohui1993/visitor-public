package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class AdminUserResp {
    private final Long id;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final Set<String> roleCodes;
    private final LocalDateTime lockedUntil;
    private final LocalDateTime lastLoginTime;
    private final LocalDateTime createTime;
}
