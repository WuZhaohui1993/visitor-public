package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminIntegrationStatusResp {
    private final Integration dingtalk;
    private final Integration hikvision;
    private final Integration accessControl;
    private final Storage storage;
    private final int cachedAccessTargets;

    @Getter
    @Builder
    public static class Integration {
        private final boolean enabled;
        private final boolean configured;
        private final String mode;
        private final String endpoint;
    }

    @Getter
    @Builder
    public static class Storage {
        private final String provider;
        private final String bucket;
        private final boolean configured;
    }
}
