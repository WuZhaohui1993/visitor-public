package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLoginResp {
    private final String accessToken;
    private final long expiresInSeconds;
    private final AdminProfileResp user;
}
