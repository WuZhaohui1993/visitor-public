package com.visitor.system.visitor.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.dto.DingTalkOaMessage;
import com.visitor.system.visitor.dto.DingTalkUserDto;
import com.visitor.system.visitor.service.DingTalkClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component("httpDingTalkClient")
public class HttpDingTalkClient implements DingTalkClient {

    private final DingTalkHttpSupport httpSupport;
    private final VisitorProperties properties;
    private volatile CachedUsers cachedUsers;

    public HttpDingTalkClient(DingTalkHttpSupport httpSupport, VisitorProperties properties) {
        this.httpSupport = httpSupport;
        this.properties = properties;
    }

    @Override
    public List<DingTalkUserDto> searchUsers(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        return getCachedUsers().stream()
            .filter(user -> user.getName().contains(keyword)
                || user.getDeptName().contains(keyword)
                || user.getUserId().contains(normalizedKeyword))
            .limit(50)
            .toList();
    }

    public List<DingTalkUserDto> fetchAllUsers() {
        String token = httpSupport.getAccessToken();
        List<Long> deptIds = fetchDeptIds(token);
        Map<Long, String> deptNames = fetchDeptNames(token, deptIds);
        Map<String, DingTalkUserDto> users = new LinkedHashMap<>();
        for (Long deptId : deptIds) {
            int cursor = 0;
            boolean hasMore = true;
            while (hasMore) {
                JsonNode jsonNode = httpSupport.post(
                    "https://oapi.dingtalk.com/topapi/v2/user/list",
                    token,
                    httpSupport.body("dept_id", deptId, "cursor", cursor, "size", 100, "contain_access_limit", false),
                    "获取钉钉用户列表失败"
                );
                JsonNode result = jsonNode.path("result");
                JsonNode listNode = result.path("list");
                if (listNode.isArray()) {
                    for (JsonNode item : listNode) {
                        String userId = item.path("userid").asText();
                        if (userId.isBlank()) {
                            continue;
                        }
                        String name = item.path("name").asText();
                        String deptName = deptNames.getOrDefault(
                            deptId,
                            deptId.equals(properties.getDingtalk().getRootDeptId()) ? "根部门" : "部门-" + deptId
                        );
                        users.putIfAbsent(userId, DingTalkUserDto.builder().userId(userId).name(name).deptName(deptName).build());
                    }
                }
                hasMore = result.path("has_more").asBoolean(false);
                cursor = result.path("next_cursor").asInt(cursor + 100);
            }
        }
        return users.values().stream().toList();
    }

    private List<DingTalkUserDto> getCachedUsers() {
        CachedUsers currentCache = cachedUsers;
        if (currentCache != null && currentCache.expireAt().isAfter(LocalDateTime.now())) {
            return currentCache.users();
        }
        synchronized (this) {
            currentCache = cachedUsers;
            if (currentCache != null && currentCache.expireAt().isAfter(LocalDateTime.now())) {
                return currentCache.users();
            }
            List<DingTalkUserDto> users = fetchAllUsers();
            int cacheMinutes = properties.getDingtalk().getUserCacheMinutes() == null
                ? 10
                : Math.max(properties.getDingtalk().getUserCacheMinutes(), 1);
            cachedUsers = new CachedUsers(users, LocalDateTime.now().plusMinutes(cacheMinutes));
            return users;
        }
    }

    @Override
    public DingTalkUserDto getUserByAuthCode(String authCode) {
        if (properties.getDingtalk().getCorpId() == null || properties.getDingtalk().getCorpId().isBlank()) {
            throw new BusinessException("缺少 DINGTALK_CORP_ID，暂时无法完成 authCode 换 userId");
        }
        String token = httpSupport.getAccessToken();
        JsonNode jsonNode = httpSupport.post(
            "https://oapi.dingtalk.com/topapi/v2/user/getuserinfo",
            token,
            httpSupport.body("code", authCode),
            "通过 authCode 获取钉钉用户失败"
        );
        JsonNode result = jsonNode.path("result");
        return DingTalkUserDto.builder()
            .userId(result.path("userid").asText())
            .name(result.path("name").asText(""))
            .deptName("")
            .build();
    }

    @Override
    public void sendOaMessage(String userId, DingTalkOaMessage message) {
        if (properties.getDingtalk().getAgentId() == null) {
            throw new BusinessException("缺少 DINGTALK_AGENT_ID，无法发送工作通知");
        }
        String token = httpSupport.getAccessToken();
        Map<String, Object> messageBody = httpSupport.body(
            "userid_list", userId,
            "agent_id", properties.getDingtalk().getAgentId(),
            "to_all_user", false,
            "msg", httpSupport.body(
                "msgtype", "oa",
                "oa", httpSupport.body(
                    "head", httpSupport.body("bgcolor", "FFF97316", "text", message.getHeadText()),
                    "body", httpSupport.body(
                        "title", message.getTitle(),
                        "content", message.getContent(),
                        "author", message.getAuthor(),
                        "form", message.getForms().stream()
                            .map(item -> httpSupport.body("key", item.getKey(), "value", item.getValue()))
                            .toList()
                    ),
                    "message_url", message.getActionUrl(),
                    "pc_message_url", message.getActionUrl()
                )
            )
        );
        httpSupport.post(
            "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2",
            token,
            messageBody,
            "发送钉钉工作通知失败"
        );
    }

    private List<Long> fetchDeptIds(String token) {
        Long rootDeptId = Objects.requireNonNullElse(properties.getDingtalk().getRootDeptId(), 1L);
        List<Long> deptIds = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        collectDeptIds(token, rootDeptId, deptIds, visited);
        return deptIds;
    }

    private Map<Long, String> fetchDeptNames(String token, List<Long> deptIds) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Long deptId : deptIds) {
            JsonNode jsonNode = httpSupport.post(
                "https://oapi.dingtalk.com/topapi/v2/department/get",
                token,
                httpSupport.body("dept_id", deptId),
                "获取钉钉部门详情失败"
            );
            JsonNode result = jsonNode.path("result");
            String name = result.path("name").asText();
            if (!name.isBlank()) {
                names.put(deptId, name);
            }
        }
        return names;
    }

    private void collectDeptIds(String token, Long deptId, List<Long> deptIds, Set<Long> visited) {
        if (deptId == null || !visited.add(deptId)) {
            return;
        }
        deptIds.add(deptId);
        JsonNode jsonNode = httpSupport.post(
            "https://oapi.dingtalk.com/topapi/v2/department/listsubid",
            token,
            httpSupport.body("dept_id", deptId),
            "获取钉钉部门列表失败"
        );
        JsonNode deptNode = jsonNode.path("result").path("dept_id_list");
        if (deptNode.isArray()) {
            deptNode.forEach(item -> collectDeptIds(token, item.asLong(), deptIds, visited));
        }
    }

    private record CachedUsers(List<DingTalkUserDto> users, LocalDateTime expireAt) {
    }
}
