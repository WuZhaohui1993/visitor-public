package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VisitorRegisterResp {
    private final String bizId;
    private final String resultAccessToken;
    private final String resultUrl;
}
