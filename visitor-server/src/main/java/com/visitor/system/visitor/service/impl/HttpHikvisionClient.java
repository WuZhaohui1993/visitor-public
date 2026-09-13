package com.visitor.system.visitor.service.impl;

import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.dto.HikvisionAddFaceCommand;
import com.visitor.system.visitor.dto.HikvisionAddPersonCommand;
import com.visitor.system.visitor.dto.HikvisionFaceResult;
import com.visitor.system.visitor.dto.HikvisionGrantAccessCommand;
import com.visitor.system.visitor.dto.HikvisionRevokeAccessCommand;
import com.visitor.system.visitor.service.HikvisionClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 基于 Artemis SDK 的海康接口实现。
 *
 * <p>这里不直接暴露“原始 HTTP 调用”，而是把海康 PMAS/FRS/ACPS 接口包装成访客业务动作。
 * 上层同步服务通过这些动作编排审批通过、到期退场和权限回收流程。
 */
@Service
public class HttpHikvisionClient implements HikvisionClient {

    private static final DateTimeFormatter HIK_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final HikvisionHttpSupport httpSupport;
    private final VisitorProperties properties;

    public HttpHikvisionClient(HikvisionHttpSupport httpSupport, VisitorProperties properties) {
        this.httpSupport = httpSupport;
        this.properties = properties;
    }

    @Override
    public String addPerson(HikvisionAddPersonCommand command) {
        // PMAS 新增人员是访客入场链路的第一步；personIndexCode 使用本系统稳定编码，
        // 后续查询、复用、退场删除都依赖这个编码保持幂等。
        Map<String, Object> body = Map.of(
            // 1. 海康人员姓名，对应访客姓名。
            "personName", command.getPersonName(),
            // 2. 海康人员唯一编码，使用本系统生成的稳定 personCode。
            "personIndexCode", command.getPersonCode(),
            // 3. 性别未知时固定传 0，避免因为空字段导致海康校验失败。
            "gender", "0",
            // 4. 访客手机号，便于海康平台侧查看人员信息。
            "phoneNo", command.getPhone(),
            // 5. 111 是身份证证件类型，配合 certificateNo 做实名唯一判断。
            "certificateType", "111",
            // 6. 访客身份证号，海康可能据此判断“同实名人员已存在”。
            "certificateNo", command.getIdCardNo(),
            // 7. 人员所属组织，由现场海康组织编码配置决定。
            "orgIndexCode", command.getOrgIndexCode(),
            // 8. 工号也使用稳定 personCode，便于现场按编码检索。
            "jobNo", command.getPersonCode(),
            // 9. 海康人员应用标识，部分 PMAS 环境要求携带。
            "appId", properties.getHikvision().getPersonAppId()
        );
        // 10. 调 PMAS 新增人员接口；includeDefaultTagId=false，因为这个接口使用 userId 请求头，不使用 FRS tagId。
        var root = httpSupport.post(
            // 11. 新增人员接口路径，默认 /artemis/api/pmas/v1/person/single/add。
            properties.getHikvision().getAddPersonPath(),
            // 12. 上面组装好的海康人员请求体。
            body,
            // 13. 海康 PMAS 要求 userId 请求头，当前从配置读取。
            Map.of("userId", properties.getHikvision().getUserId()),
            // 14. false 表示不自动加 tagId，避免把 FRS 评分头带到 PMAS 接口。
            false,
            // 15. 统一错误前缀，底层会拼上海康返回的 code/msg。
            "海康新增人员失败"
        );
        // 16. HTTP/网关成功不等于业务成功，还要检查 data.failures。
        ensurePersonAddSucceeded(root);
        // 不同 PMAS 版本返回的人员标识字段不完全一致，按真实 personId -> indexCode -> successes 逐级兼容。
        // 17. 优先读取 data.personId，这是后续 personFace 最希望使用的真实人员 ID。
        String personId = root.path("data").path("personId").asText();
        if (!StringUtils.hasText(personId)) {
            // 18. 如果没有 personId，兼容 data.indexCode。
            personId = root.path("data").path("indexCode").asText();
        }
        if (!StringUtils.hasText(personId)) {
            // 19. 如果是批量结构返回，兼容 data.successes[0].indexCode。
            personId = root.path("data").path("successes").path(0).path("indexCode").asText();
        }
        // 20. 如果仍拿不到真实 ID，就回退返回本系统 personCode；上层会马上 detailV1 补查，查不到就延迟重试。
        return StringUtils.hasText(personId) ? personId : command.getPersonCode();
    }

    @Override
    public String findPersonId(String personCode) {
        // 1. personCode 为空时没有查询条件，直接返回 null。
        if (!StringUtils.hasText(personCode)) {
            return null;
        }
        // 查询人员用于两类补偿：新增人员后平台异步落库，以及同一实名人员已存在时复用旧记录。
        // 2. lastFailure 只保存非“人员不存在”的最后一次失败，便于最后抛出真实原因。
        String lastFailure = null;
        // 3. 现场可能有带 appId 和不带 appId 两种查询口径，所以逐个请求体尝试。
        for (Map<String, Object> queryBody : buildPersonQueryBodies(personCode)) {
            // 4. detailV1 需要上层自己判断 code，所以这里用 postRaw，不在底层直接 assertSuccess。
            var root = httpSupport.postRaw(
                // 5. 人员详情查询接口路径，默认 /artemis/api/pmas/v1/person/detailV1。
                properties.getHikvision().getQueryPersonPath(),
                // 6. 当前查询请求体，至少包含 personIndexCode。
                queryBody,
                // 7. 查询接口也带 userId 请求头。
                Map.of("userId", properties.getHikvision().getUserId()),
                // 8. 不带 FRS tagId。
                false,
                // 9. 查询失败时的统一错误前缀。
                "海康查询人员失败"
            );
            if (isSuccess(root)) {
                // 10. 查询成功时，从不同可能的 data/list/rows 结构里提取真实 personId。
                return extractPersonId(root);
            }
            if (isPersonNotFound(root)) {
                // 11. 查询明确返回人员不存在时，不立即失败，继续尝试下一种查询口径。
                continue;
            }
            // 12. 其他错误先记下来，所有查询口径都失败后再抛给上层。
            lastFailure = extractFailure(root);
        }
        if (StringUtils.hasText(lastFailure)) {
            // 13. 如果有非“人员不存在”的失败，说明不是简单异步落库问题，抛出真实查询失败。
            throw new IllegalStateException("海康查询人员失败：" + lastFailure);
        }
        // 14. 所有查询口径都只是“人员不存在”，返回 null，让上层进入延迟重试逻辑。
        return null;
    }

    @Override
    public void enablePerson(String personCode) {
        // 复用旧人员时先尝试启用，避免旧访客到期后状态仍停留在不可用。
        updatePersonStatus(
            properties.getHikvision().getEnablePersonPath(),
            personCode,
            "1",
            "海康启用人员失败"
        );
    }

    private void ensurePersonAddSucceeded(com.fasterxml.jackson.databind.JsonNode root) {
        // PMAS 可能 HTTP 成功但在 data.failures 中返回业务失败，必须按业务结果判定。
        com.fasterxml.jackson.databind.JsonNode data = root.path("data");
        com.fasterxml.jackson.databind.JsonNode failures = data.path("failures");
        if (failures.isArray() && !failures.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            failures.forEach(item -> {
                String indexCode = item.path("indexCode").asText();
                String reason = item.path("reason").asText();
                if (StringUtils.hasText(indexCode) && StringUtils.hasText(reason)) {
                    reasons.add(indexCode + ":" + reason);
                } else if (StringUtils.hasText(reason)) {
                    reasons.add(reason);
                } else if (StringUtils.hasText(indexCode)) {
                    reasons.add(indexCode);
                }
            });
            throw new IllegalStateException("海康新增人员失败：" + String.join("; ", reasons));
        }
        com.fasterxml.jackson.databind.JsonNode successes = data.path("successes");
        boolean hasExplicitSuccess = (successes.isArray() && !successes.isEmpty())
            || StringUtils.hasText(data.path("personId").asText())
            || StringUtils.hasText(data.path("indexCode").asText());
        if (!hasExplicitSuccess) {
            throw new IllegalStateException("海康新增人员成功响应中缺少有效成功结果");
        }
    }

    private boolean isSuccess(com.fasterxml.jackson.databind.JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        String code = root.path("code").asText();
        return !StringUtils.hasText(code) || "0".equals(code) || "200".equals(code);
    }

    private boolean isPersonNotFound(com.fasterxml.jackson.databind.JsonNode root) {
        // “人员不存在”在新增后短时间内通常是平台异步同步未完成，上层会把它转成延迟重试。
        String failure = extractFailure(root);
        String lowerCaseFailure = failure == null ? "" : failure.toLowerCase();
        return failure != null
            && (failure.contains("人员在平台上不存在")
            || failure.contains("人员不存在")
            || (lowerCaseFailure.contains("person")
            && (lowerCaseFailure.contains("not exist")
            || lowerCaseFailure.contains("not found")
            || lowerCaseFailure.contains("does not exist"))));
    }

    private String extractFailure(com.fasterxml.jackson.databind.JsonNode root) {
        String message = root.path("msg").asText();
        if (!StringUtils.hasText(message)) {
            message = root.path("message").asText();
        }
        if (!StringUtils.hasText(message)) {
            message = root.toString();
        }
        return root.path("code").asText() + " " + message;
    }

    private String extractPersonId(com.fasterxml.jackson.databind.JsonNode root) {
        // 海康不同接口/版本对人员详情返回结构不一致，集中在这里做兼容，避免上层散落字段判断。
        List<com.fasterxml.jackson.databind.JsonNode> candidates = new ArrayList<>();
        com.fasterxml.jackson.databind.JsonNode data = root.path("data");
        candidates.add(data);
        candidates.add(data.path("list").path(0));
        candidates.add(data.path("rows").path(0));
        candidates.add(data.path("records").path(0));
        candidates.add(data.path("persons").path(0));
        candidates.add(data.path("successes").path(0));

        for (com.fasterxml.jackson.databind.JsonNode candidate : candidates) {
            if (candidate == null || candidate.isMissingNode() || candidate.isNull()) {
                continue;
            }
            String personId = firstNonBlank(
                candidate.path("personId").asText(),
                candidate.path("personIndexCode").asText(),
                candidate.path("id").asText(),
                candidate.path("indexCode").asText()
            );
            if (StringUtils.hasText(personId)) {
                return personId;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private List<Map<String, Object>> buildPersonQueryBodies(String personCode) {
        // 现场网关存在带 appId 与不带 appId 两种查询口径，按最小条件优先，再带租户 appId 补查。
        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(Map.of("personIndexCode", personCode));

        String personAppId = properties.getHikvision().getPersonAppId();
        if (StringUtils.hasText(personAppId)) {
            candidates.add(Map.of(
                "personIndexCode", personCode,
                "appId", personAppId
            ));
        }
        return candidates;
    }

    @Override
    public void disablePerson(String personCode) {
        // 退场链路统一走 PMAS 批量删人接口，确保到期访客从平台人员侧失效。
        var root = httpSupport.post(
            properties.getHikvision().getDisablePersonPath(),
            Map.of("personIndexCodes", List.of(personCode)),
            Map.of("userId", properties.getHikvision().getUserId()),
            false,
            "海康禁用人员失败"
        );
        ensureBatchDeleteSucceeded(root);
    }

    @Override
    public HikvisionFaceResult addFace(HikvisionAddFaceCommand command) {
        // 人脸绑定走 PMAS personFace；faceData 使用服务端预处理后的 JPG 字节 Base64，
        // 不把浏览器原始上传文件直接透传给海康。
        Map<String, Object> body = Map.of(
            // 1. chgFlag=true 表示本次请求会变更人员人脸信息。
            "chgFlag", true,
            // 2. personId 必须是海康真实人员 ID；如果人员尚未落库，接口会返回“人员不存在”。
            "personId", command.getPersonId(),
            // 3. faceData 是服务端预处理后的 JPG 字节 Base64，不能带 data:image 前缀。
            "faceData", Base64.getEncoder().encodeToString(command.getFaceImageBytes())
        );
        // 4. 调 PMAS personFace 接口；如果这里抛“人员不存在”，上层 addFaceWhenPersonReady 会识别并延迟重试。
        var root = httpSupport.post(
            // 5. 人脸绑定接口路径，默认 /artemis/api/pmas/v1/personFace。
            properties.getHikvision().getAddFacePath(),
            // 6. 上面组装好的人脸请求体。
            body,
            // 7. PMAS 接口使用 userId 请求头。
            Map.of("userId", properties.getHikvision().getUserId()),
            // 8. 不带 FRS tagId。
            false,
            // 9. 统一错误前缀，底层会附上海康返回的 code/msg。
            "海康新增访客人脸失败"
        );
        // 部分环境只返回文本 data，部分环境返回 data.indexCode；统一收敛为本地记录可保存的 faceIndexCode。
        // 10. 优先读取 data.indexCode 作为人脸索引，退场删人脸时会用到。
        String faceIndexCode = root.path("data").path("indexCode").asText();
        if (!StringUtils.hasText(faceIndexCode) && root.path("data").isTextual()) {
            // 11. 兼容 data 直接是字符串的返回结构。
            String textualData = root.path("data").asText();
            if (StringUtils.hasText(textualData) && !"null".equalsIgnoreCase(textualData)) {
                // 12. 字符串 data 有值且不是 "null" 时，作为 faceIndexCode 保存。
                faceIndexCode = textualData;
            }
        }
        // 13. 如果海康返回了人脸图片 URL，也保存下来方便排查。
        String facePicUrl = root.path("data").path("facePic").path("faceUrl").asText();
        // 14. 统一封装成本系统使用的结果对象。
        return HikvisionFaceResult.builder()
            // 15. 空字符串转 null，避免后续误以为有可删除的人脸索引。
            .faceIndexCode(StringUtils.hasText(faceIndexCode) ? faceIndexCode : null)
            // 16. 空 URL 转 null。
            .facePicUrl(StringUtils.hasText(facePicUrl) ? facePicUrl : null)
            .build();
    }

    @Override
    public void deleteFace(String faceGroupIndexCode, String faceIndexCode) {
        // 删除人脸仍使用 FRS 分组删除接口；退场时先删权限再删人脸，减少过期访客继续刷脸的风险。
        Map<String, Object> body = Map.of(
            "faceGroupIndexCode", faceGroupIndexCode,
            "indexCodes", java.util.List.of(faceIndexCode)
        );
        httpSupport.post(properties.getHikvision().getDeleteFacePath(), body, "海康删除访客人脸失败");
    }

    @Override
    public void grantAccess(HikvisionGrantAccessCommand command) {
        VisitorProperties.Access access = properties.getHikvision().getAccess();
        if (!access.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(access.getGrantPath())) {
            throw new IllegalStateException("未配置海康权限下发接口路径");
        }
        if (command.getResourceInfos() == null || command.getResourceInfos().isEmpty()) {
            throw new IllegalStateException("未配置海康权限下发资源列表");
        }
        // 权限新增统一走 ACPS“添加权限配置”：把人员、门禁资源和访客有效时间一次性写入海康。
        Map<String, Object> body = Map.of(
            "personDatas", List.of(Map.of(
                "indexCodes", List.of(command.getPersonId()),
                "personDataType", "person"
            )),
            "resourceInfos", command.getResourceInfos().stream().map(resourceInfo -> Map.of(
                "resourceIndexCode", resourceInfo.getResourceIndexCode(),
                "resourceType", command.getResourceType(),
                "channelNos", resourceInfo.getChannelNos()
            )).toList(),
            "startTime", HIK_TIME_FORMATTER.format(command.getBeginTime().atZone(java.time.ZoneId.systemDefault())),
            "endTime", HIK_TIME_FORMATTER.format(command.getEndTime().atZone(java.time.ZoneId.systemDefault()))
        );
        httpSupport.post(access.getGrantPath(), body, "海康门禁权限下发失败");
    }

    @Override
    public void revokeAccess(HikvisionRevokeAccessCommand command) {
        VisitorProperties.Access access = properties.getHikvision().getAccess();
        if (!access.isEnabled()) {
            return;
        }
        if (command.getResourceInfos() == null || command.getResourceInfos().isEmpty()) {
            throw new IllegalStateException("未配置海康权限回收资源列表");
        }
        if (!StringUtils.hasText(access.getRevokePath())) {
            throw new IllegalStateException("未配置海康权限回收接口路径");
        }
        // 权限回收统一走 ACPS“删除权限配置”，按下发时相同的人员和资源维度精准删除。
        Map<String, Object> body = Map.of(
            "personDatas", List.of(Map.of(
                "indexCodes", List.of(command.getPersonId()),
                "personDataType", "person"
            )),
            "resourceInfos", command.getResourceInfos().stream().map(resourceInfo -> Map.of(
                "resourceIndexCode", resourceInfo.getResourceIndexCode(),
                "resourceType", command.getResourceType(),
                "channelNos", resourceInfo.getChannelNos().stream().map(String::valueOf).toList()
            )).toList()
        );
        httpSupport.post(access.getRevokePath(), body, "海康门禁权限回收失败");
    }

    private void ensureBatchDeleteSucceeded(com.fasterxml.jackson.databind.JsonNode root) {
        // 批量删人同样可能出现部分失败；只要 failures 有值，就保留原始原因便于现场排障。
        com.fasterxml.jackson.databind.JsonNode failures = root.path("data").path("failures");
        if (failures.isArray() && !failures.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            failures.forEach(item -> {
                String indexCode = item.path("indexCode").asText();
                String name = item.path("name").asText();
                String reason = item.path("reason").asText();
                String detail = firstNonBlank(reason, name, indexCode);
                if (StringUtils.hasText(detail)) {
                    reasons.add(detail);
                }
            });
            throw new IllegalStateException("海康禁用人员失败：" + String.join("; ", reasons));
        }
    }

    private void updatePersonStatus(String path, String personCode, String personStatus, String errorMessage) {
        Map<String, Object> body = Map.of(
            "personIndexCode", personCode,
            "personStatus", personStatus
        );
        httpSupport.post(path, body, errorMessage);
    }
}
