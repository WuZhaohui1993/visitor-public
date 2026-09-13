package com.visitor.system.admin.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class IntegrationConfigPayload {
    private Dingtalk dingtalk = new Dingtalk();
    private Hikvision hikvision = new Hikvision();
    private Storage storage = new Storage();

    @Getter
    @Setter
    public static class Dingtalk {
        private boolean enabled;
        private boolean mockMode;
        private String appKey;
        private String appSecret;
        private String corpId;
        private Long agentId;
        private Long rootDeptId = 1L;
    }

    @Getter
    @Setter
    public static class Hikvision {
        private boolean enabled;
        private String baseUrl;
        private String appKey;
        private String appSecret;
        private String tagId;
        private String userId;
        private String orgIndexCode;
        private String personAppId;
        private String faceGroupIndexCode;
        private boolean faceScoreEnabled;
        private Integer connectTimeoutSeconds;
        private Integer readTimeoutSeconds;
        private boolean trustAll;
        private Access access = new Access();
    }

    @Getter
    @Setter
    public static class Access {
        private boolean enabled;
        private String resourceType;
        private List<String> resourceIndexCodes = new ArrayList<>();
        private boolean autoDiscoverResources;
        private String resourceQueryPath;
        private String resourceQueryType;
        private Integer resourceQueryPageSize;
        private List<Integer> channelNos = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Storage {
        private String provider;
        private String bucket;
        private String localRoot;
        private String previewBaseUrl;
        private Minio minio = new Minio();
    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
    }
}
