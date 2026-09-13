package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class AdminProfileResp {
    private final Long userId;
    private final String username;
    private final String displayName;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final boolean mustChangePassword;
}
