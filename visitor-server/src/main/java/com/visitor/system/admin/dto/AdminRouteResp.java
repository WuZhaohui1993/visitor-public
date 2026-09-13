package com.visitor.system.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminRouteResp {
    private final String path;
    private final String name;
    private final String component;
    private final Meta meta;
    private final List<AdminRouteResp> children;

    @Getter
    @Builder
    public static class Meta {
        private final String title;
        private final String icon;
        private final Integer rank;
        private final List<String> permissions;
    }
}
