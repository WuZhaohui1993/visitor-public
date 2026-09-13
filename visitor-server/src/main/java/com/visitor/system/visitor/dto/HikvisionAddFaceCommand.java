package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HikvisionAddFaceCommand {
    private final String personId;
    private final byte[] faceImageBytes;
}
