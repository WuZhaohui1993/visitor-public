package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HikvisionFaceResult {
    private final String faceIndexCode;
    private final String facePicUrl;
}
