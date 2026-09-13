package com.visitor.system.visitor.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hikvision.artemis.sdk.Client;
import com.hikvision.artemis.sdk.Request;
import com.hikvision.artemis.sdk.Response;
import com.hikvision.artemis.sdk.constant.Constants;
import com.hikvision.artemis.sdk.enums.Method;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 海康 OpenAPI 统一请求支撑。
 *
 * <p>所有海康接口都通过这里完成 Artemis 签名、网关地址拼接、超时设置、日志脱敏和通用成功码判断。
 * 具体业务服务只负责组装各自接口的业务参数。
 */
@Component
public class HikvisionHttpSupport {

    private static final Logger log = LoggerFactory.getLogger(HikvisionHttpSupport.class);
    private static final int LOG_BODY_LIMIT = 2000;

    private final ObjectMapper objectMapper;
    private final VisitorProperties properties;

    public HikvisionHttpSupport(ObjectMapper objectMapper, VisitorProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public JsonNode post(String path, Object body, String errorMessage) {
        // 普通业务接口只接受海康 code=0/200，失败直接转成本系统业务异常。
        JsonNode root = postRaw(path, body, Map.of(), true, errorMessage);
        assertSuccess(root, errorMessage);
        return root;
    }

    public JsonNode postRaw(String path, Object body, String errorMessage) {
        // 人脸评分、人员查询等接口需要自行解读海康业务 code，因此返回原始 JSON 给上层判断。
        return postRaw(path, body, Map.of(), true, errorMessage);
    }

    public JsonNode post(String path, Object body, Map<String, String> headers, boolean includeDefaultTagId, String errorMessage) {
        // 带自定义请求头的接口仍默认走通用成功码校验，适合 PMAS/ACPS 的强成功场景。
        JsonNode root = postRaw(path, body, headers, includeDefaultTagId, errorMessage);
        assertSuccess(root, errorMessage);
        return root;
    }

    public JsonNode postRaw(String path, Object body, Map<String, String> headers, boolean includeDefaultTagId, String errorMessage) {
        VisitorProperties.Hikvision hikvision = properties.getHikvision();
        validateConfig(hikvision);
        try {
            applyTimeouts(hikvision);
            String bodyJson = toJson(body);
            Request<String> request = buildPostRequest(hikvision, path, bodyJson, headers, includeDefaultTagId);
            log.info("hikvision request path={} headers={} body={}",
                request.getPath(),
                summarizeHeaders(request.getHeaders()),
                abbreviate(bodyJson));
            Response response = Client.execute(request);
            String responseBody = response == null ? null : response.getBody();
            log.info("hikvision response path={} body={}", request.getPath(), abbreviate(responseBody));
            if (isBlank(responseBody)) {
                throw new BusinessException(errorMessage + "：" + defaultIfBlank(response == null ? null : response.getErrorMessage(), "海康返回为空"));
            }
            return readTree(responseBody);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("hikvision request failed path={} reason={}", normalizePath(path), exception.getMessage(), exception);
            throw new BusinessException(errorMessage + "：" + exception.getMessage());
        }
    }

    private Request<String> buildPostRequest(
        VisitorProperties.Hikvision hikvision,
        String path,
        String bodyJson,
        Map<String, String> headers,
        boolean includeDefaultTagId
    ) {
        // baseUrl 允许配置到网关根路径或带 /artemis 前缀；parseEndpoint 会合并最终签名 path。
        ParsedEndpoint endpoint = parseEndpoint(hikvision.getBaseUrl(), normalizePath(path));
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("Accept", MediaType.APPLICATION_JSON_VALUE);
        requestHeaders.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        Request<String> request = new Request<>(Method.POST_STRING,
            endpoint.schemaPrefix() + endpoint.hostPort(),
            endpoint.path(),
            hikvision.getAppKey(),
            hikvision.getAppSecret(),
            0);
        // FRS 评分需要默认 tagId；PMAS/ACPS 多数接口使用 userId 等自定义头，所以可按调用方关闭。
        if (includeDefaultTagId && !isBlank(hikvision.getTagId())) {
            requestHeaders.put("tagId", hikvision.getTagId().trim());
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (!isBlank(key) && !isBlank(value)) {
                    requestHeaders.put(key.trim(), value.trim());
                }
            });
        }
        request.setHeaders(requestHeaders);
        request.setStringBody(bodyJson);
        return request;
    }

    private ParsedEndpoint parseEndpoint(String baseUrl, String requestPath) {
        try {
            // 兼容配置中只写 IP:PORT 的情况，默认按 https 海康网关处理。
            String normalizedBaseUrl = baseUrl.trim();
            if (!normalizedBaseUrl.contains("://")) {
                normalizedBaseUrl = "https://" + normalizedBaseUrl;
            }
            URI uri = URI.create(normalizedBaseUrl);
            String scheme = defaultIfBlank(uri.getScheme(), "https").toLowerCase();
            if (isBlank(uri.getHost())) {
                throw new BusinessException("海康 baseUrl 缺少主机地址");
            }
            int port = uri.getPort() > 0 ? uri.getPort() : ("http".equals(scheme) ? 80 : 443);
            String hostPort = uri.getHost() + ":" + port;
            String mergedPath = mergePathPrefix(uri.getPath(), requestPath);
            return new ParsedEndpoint(scheme + "://", hostPort, mergedPath);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("海康 baseUrl 格式非法");
        }
    }

    private String mergePathPrefix(String basePath, String requestPath) {
        // 如果 baseUrl 已带 /artemis，且接口 path 也从 /artemis 开始，避免重复拼接。
        String normalizedBasePath = normalizeOptionalPath(basePath);
        if (normalizedBasePath.isEmpty()) {
            return requestPath;
        }
        if (requestPath.equals(normalizedBasePath) || requestPath.startsWith(normalizedBasePath + "/")) {
            return requestPath;
        }
        return normalizedBasePath + requestPath;
    }

    private String normalizeOptionalPath(String path) {
        if (isBlank(path) || "/".equals(path.trim())) {
            return "";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void validateConfig(VisitorProperties.Hikvision hikvision) {
        if (isBlank(hikvision.getBaseUrl())) {
            throw new BusinessException("缺少海康 baseUrl 配置");
        }
        if (isBlank(hikvision.getAppKey())) {
            throw new BusinessException("缺少海康 appKey 配置");
        }
        if (isBlank(hikvision.getAppSecret())) {
            throw new BusinessException("缺少海康 appSecret 配置");
        }
    }

    private void applyTimeouts(VisitorProperties.Hikvision hikvision) {
        if (hikvision.getConnectTimeoutSeconds() != null && hikvision.getConnectTimeoutSeconds() > 0) {
            Constants.DEFAULT_TIMEOUT = hikvision.getConnectTimeoutSeconds() * 1000;
        }
        if (hikvision.getReadTimeoutSeconds() != null && hikvision.getReadTimeoutSeconds() > 0) {
            Constants.SOCKET_TIMEOUT = hikvision.getReadTimeoutSeconds() * 1000;
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new BusinessException("序列化海康请求体失败");
        }
    }

    private JsonNode readTree(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw new BusinessException("解析海康返回结果失败");
        }
    }

    private void assertSuccess(JsonNode root, String errorMessage) {
        // 海康常见成功码是 0 或 200；缺省 code 的旧接口也按成功处理，其余保留远端错误信息。
        if (root == null || root.isNull()) {
            throw new BusinessException(errorMessage + "：海康返回为空");
        }
        JsonNode codeNode = root.path("code");
        if (codeNode.isMissingNode() || codeNode.isNull()) {
            return;
        }
        String code = codeNode.asText();
        if ("0".equals(code) || "200".equals(code)) {
            return;
        }
        throw new BusinessException(errorMessage + "：" + code + " " + extractRemoteMessage(root.toString(), "unknown error"));
    }

    private String extractRemoteMessage(String responseBody, String fallback) {
        if (isBlank(responseBody)) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("msg").asText();
            if (isBlank(message)) {
                message = root.path("message").asText();
            }
            if (isBlank(message)) {
                message = root.path("errorMsg").asText();
            }
            if (isBlank(message)) {
                message = root.path("detail").asText();
            }
            return isBlank(message) ? fallback : message;
        } catch (Exception exception) {
            return responseBody;
        }
    }

    private String normalizePath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return "/";
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private Map<String, String> summarizeHeaders(Map<String, String> headers) {
        Map<String, String> summary = new LinkedHashMap<>();
        if (headers == null) {
            return summary;
        }
        headers.forEach((key, value) -> {
            if ("appSecret".equalsIgnoreCase(key)) {
                return;
            }
            summary.put(key, abbreviate(value));
        });
        return summary;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= LOG_BODY_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, LOG_BODY_LIMIT) + "...(truncated)";
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ParsedEndpoint(String schemaPrefix, String hostPort, String path) {
    }
}
