package com.visitor.system.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.admin.dto.IntegrationConfigPayload;
import com.visitor.system.common.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class IntegrationConfigProbeService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public IntegrationConfigProbeService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public String test(IntegrationConfigPayload payload) {
        List<String> results = new ArrayList<>();
        testDingtalk(payload.getDingtalk(), results);
        testHikvision(payload.getHikvision(), results);
        testStorage(payload.getStorage(), results);
        return String.join("；", results);
    }

    private void testDingtalk(IntegrationConfigPayload.Dingtalk config, List<String> results) {
        if (!config.isEnabled()) {
            results.add("钉钉未启用");
            return;
        }
        if (config.isMockMode()) {
            results.add("钉钉 Mock 模式校验通过");
            return;
        }
        try {
            String response = restClient.get()
                .uri("https://oapi.dingtalk.com/gettoken?appkey={appkey}&appsecret={appsecret}", config.getAppKey(), config.getAppSecret())
                .retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.path("errcode").asInt(-1) != 0 || root.path("access_token").asText().isBlank()) {
                throw new BusinessException("钉钉凭据测试失败：" + root.path("errmsg").asText("unknown error"));
            }
            results.add("钉钉凭据测试通过");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("钉钉连通测试失败：" + exception.getMessage());
        }
    }

    private void testHikvision(IntegrationConfigPayload.Hikvision config, List<String> results) {
        if (!config.isEnabled()) {
            results.add("海康未启用");
            return;
        }
        try {
            String raw = config.getBaseUrl().contains("://") ? config.getBaseUrl() : "https://" + config.getBaseUrl();
            URI uri = URI.create(raw);
            int port = uri.getPort() > 0 ? uri.getPort() : ("http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), 3000);
            }
            results.add("海康网关网络连通");
        } catch (Exception exception) {
            throw new BusinessException("海康网关连通测试失败：" + exception.getMessage());
        }
    }

    private void testStorage(IntegrationConfigPayload.Storage config, List<String> results) {
        try {
            if ("local".equalsIgnoreCase(config.getProvider())) {
                Path root = Path.of(config.getLocalRoot()).toAbsolutePath().normalize();
                Files.createDirectories(root);
                Path probe = Files.createTempFile(root, ".admin-config-probe-", ".tmp");
                Files.deleteIfExists(probe);
                results.add("本地存储读写测试通过");
                return;
            }
            MinioClient client = MinioClient.builder()
                .endpoint(config.getMinio().getEndpoint())
                .credentials(config.getMinio().getAccessKey(), config.getMinio().getSecretKey())
                .build();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(config.getBucket()).build())) {
                throw new BusinessException("MinIO 存储桶不存在：" + config.getBucket());
            }
            results.add("MinIO 连接和存储桶测试通过");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("对象存储测试失败：" + exception.getMessage());
        }
    }
}
