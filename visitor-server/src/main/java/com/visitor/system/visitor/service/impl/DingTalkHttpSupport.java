package com.visitor.system.visitor.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class DingTalkHttpSupport {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final VisitorProperties properties;

    public DingTalkHttpSupport(RestClient restClient, ObjectMapper objectMapper, VisitorProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String getAccessToken() {
        String response = restClient.get()
            .uri("https://oapi.dingtalk.com/gettoken?appkey={appkey}&appsecret={appsecret}",
                properties.getDingtalk().getAppKey(), properties.getDingtalk().getAppSecret())
            .retrieve()
            .body(String.class);
        JsonNode jsonNode = readTree(response);
        assertSuccess(jsonNode, "获取钉钉 access_token 失败");
        return jsonNode.path("access_token").asText();
    }

    public JsonNode post(String url, String accessToken, Object body, String errorMessage) {
        String response = restClient.post()
            .uri(url + "?access_token={token}", accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
        JsonNode jsonNode = readTree(response);
        assertSuccess(jsonNode, errorMessage);
        return jsonNode;
    }

    public JsonNode postWithoutToken(String url, Object body, String errorMessage) {
        String response = restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class);
        JsonNode jsonNode = readTree(response);
        assertSuccess(jsonNode, errorMessage);
        return jsonNode;
    }

    public Map<String, Object> body(Object... keyValues) {
        java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return map;
    }

    private JsonNode readTree(String response) {
        try {
            return objectMapper.readTree(response);
        } catch (Exception exception) {
            throw new BusinessException("解析钉钉返回结果失败");
        }
    }

    private void assertSuccess(JsonNode jsonNode, String fallbackMessage) {
        int errcode = jsonNode.path("errcode").asInt(0);
        if (errcode != 0) {
            throw new BusinessException(fallbackMessage + "：" + jsonNode.path("errmsg").asText("unknown error"));
        }
    }
}
