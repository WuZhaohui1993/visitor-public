package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCaptchaResp {
    private final boolean required;
    private final String challengeId;
    private final String imageData;
}
