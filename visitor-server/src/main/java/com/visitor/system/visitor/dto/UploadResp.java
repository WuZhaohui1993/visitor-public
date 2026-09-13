package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadResp {
    private final String fileKey;
    private final String previewUrl;
    private final String objectKey;
}
