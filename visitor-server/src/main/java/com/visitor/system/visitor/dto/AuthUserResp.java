package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthUserResp {
    private final String userId;
    private final String accessToken;
    private final boolean mock;
}
