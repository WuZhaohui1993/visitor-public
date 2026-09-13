package com.visitor.system.visitor.dto;

import com.visitor.system.common.BusinessException;

import java.util.Locale;

public enum UploadScene {
    GENERAL,
    FACE;

    public static UploadScene from(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "general", "default" -> GENERAL;
            case "face" -> FACE;
            default -> throw new BusinessException("不支持的上传场景：" + value);
        };
    }
}
