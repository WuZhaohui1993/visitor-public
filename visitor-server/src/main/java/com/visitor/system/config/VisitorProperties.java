package com.visitor.system.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "visitor")
public class VisitorProperties {

    @NotBlank
    private String appBaseUrl;

    @NotNull
    private Integer resultPollSeconds = 5;

    private Security security = new Security();

    private Admin admin = new Admin();

    private Storage storage = new Storage();

    private Dingtalk dingtalk = new Dingtalk();

    private Hikvision hikvision = new Hikvision();

    @Getter
    @Setter
    public static class Security {
        @NotBlank
        private String jwtSecret;

        @NotBlank
        private String aesKey;

        @NotNull
        private Integer tokenHours = 24;

        @NotNull
        private Integer authTokenMinutes = 120;

        @NotNull
        private Integer fileTokenMinutes = 10;
    }

    @Getter
    @Setter
    public static class Admin {
        private boolean enabled;
        private String publicOrigin = "http://127.0.0.1:51767";
        private String jwtSecret;
        private String configEncryptionKey;
        private Integer accessTokenMinutes = 15;
        private Integer refreshTokenHours = 8;
        private Integer maxLoginFailures = 5;
        private Integer captchaAfterFailures = 3;
        private Integer lockMinutes = 30;
        private String bootstrapUsername;
        private String bootstrapPassword;
        private boolean secureCookie;
    }

    @Getter
    @Setter
    public static class Storage {
        @NotBlank
        private String provider = "local";

        @NotBlank
        private String bucket = "visitor";

        @NotBlank
        private String localRoot = "./data/storage";

        @NotBlank
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

    @Getter
    @Setter
    public static class Dingtalk {
        private boolean enabled;
        private boolean mockMode = true;
        private String appKey;
        private String appSecret;
        private String corpId;
        private Long agentId;
        private Long rootDeptId = 1L;
        private String mockAuthCodePrefix = "mock-";
        private Integer userCacheMinutes = 10;
    }

    /**
     * 当前海康链路固定为：PMAS 人员/人脸 + ACPS 权限配置。
     * 这里只保留主链路仍在使用的配置项。
     */
    @Getter
    @Setter
    public static class Hikvision {
        private boolean enabled;
        // Artemis OpenAPI 网关与签名凭据，所有海康接口统一从这里读取。
        private String baseUrl;
        private String appKey;
        private String appSecret;
        // FRS 人脸评分接口需要 tagId；PMAS/ACPS 业务接口主要使用 userId 请求头。
        private String tagId = "frs";
        private String userId = "admin";
        // 人员组织和应用标识用于 PMAS 新增人员，决定访客被创建到海康哪个组织/应用下。
        private String orgIndexCode;
        private String personAppId = "defaultPersonal";
        // 审批通过后的人员同步接口：新增、查询真实 personId、复用旧人员时启用、到期退场时删除。
        private String addPersonPath = "/artemis/api/pmas/v1/person/single/add";
        private String queryPersonPath = "/artemis/api/pmas/v1/person/detailV1";
        private Integer personReadyMaxAttempts = 20;
        private Integer personReadyWaitMillis = 1000;
        private String enablePersonPath = "/artemis/api/resource/v1/person/single/update";
        private String disablePersonPath = "/artemis/api/pmas/v1/person/batch/delete";
        // 退场删除人脸仍走现有 FRS 分组删除接口，因此分组编码继续保留。
        private String faceGroupIndexCode = "54544e408a5f4a7ebae52403425f85ee";
        // 上传阶段先做 FRS 评分；审批通过后再走 PMAS personFace 绑定人脸。
        private boolean faceScoreEnabled = true;
        private String faceScorePath = "/artemis/api/frs/v1/face/picture/check";
        private String addFacePath = "/artemis/api/pmas/v1/personFace";
        private Integer faceReadyMaxAttempts = 20;
        private Integer faceReadyWaitMillis = 1000;
        private String deleteFacePath = "/artemis/api/frs/v1/face/deletion";
        private Integer connectTimeoutSeconds = 5;
        private Integer readTimeoutSeconds = 10;
        private boolean trustAll = true;
        private Integer syncBatchSize = 50;
        // 门禁权限下发/回收配置，作为人员和人脸同步成功后的独立子链路。
        private Access access = new Access();
    }

    /**
     * 当前权限链路统一走“添加/删除权限配置”接口，不再保留旧版权限下载参数。
     */
    @Getter
    @Setter
    public static class Access {
        private boolean enabled;
        // ACPS 权限配置接口：审批通过下发，到期退场回收。
        private String grantPath = "/artemis/api/acps/v1/auth_config/add";
        private String revokePath = "/artemis/api/acps/v1/auth_config/delete";
        // 权限目标优先用显式资源编码；未配置时可从海康资源接口自动发现并缓存。
        private String resourceType = "acsDevice";
        private List<String> resourceIndexCodes = new ArrayList<>();
        private boolean autoDiscoverResources = true;
        private String resourceQueryPath;
        private String resourceQueryType = "door";
        private Integer resourceQueryPageSize = 1000;
        private Map<String, Object> resourceQueryBody = new LinkedHashMap<>();
        private boolean refreshResourcesOnStartup = true;
        private String resourceRefreshCron = "0 0 3 * * MON";
        private List<Integer> channelNos = new ArrayList<>(List.of(1));
    }
}
