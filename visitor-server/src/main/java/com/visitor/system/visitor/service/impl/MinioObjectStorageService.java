package com.visitor.system.visitor.service.impl;

import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.service.ObjectStorageService;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@ConditionalOnProperty(prefix = "visitor.storage", name = "provider", havingValue = "minio")
public class MinioObjectStorageService implements ObjectStorageService {

    private final VisitorProperties properties;
    private volatile ClientHolder clientHolder;

    public MinioObjectStorageService(VisitorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String uploadTemp(byte[] bytes, String objectKey, String contentType) {
        try {
            String normalizedObjectKey = normalizeObjectKey(objectKey);
            client().putObject(PutObjectArgs.builder()
                .bucket(bucket())
                .object(normalizedObjectKey)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(contentType)
                .build());
            return normalizedObjectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("上传 MinIO 失败", exception);
        }
    }

    @Override
    public String moveToBizPath(String sourceObjectKey, String targetObjectKey) {
        try {
            String normalizedSourceObjectKey = normalizeObjectKey(sourceObjectKey);
            String normalizedTargetObjectKey = normalizeObjectKey(targetObjectKey);
            client().copyObject(CopyObjectArgs.builder()
                .bucket(bucket())
                .object(normalizedTargetObjectKey)
                .source(CopySource.builder().bucket(bucket()).object(normalizedSourceObjectKey).build())
                .build());
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket()).object(normalizedSourceObjectKey).build());
            return normalizedTargetObjectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("归档 MinIO 文件失败", exception);
        }
    }

    @Override
    public String generatePreviewUrl(String objectKey) {
        return properties.getStorage().getPreviewBaseUrl() + "/" + objectKey;
    }

    @Override
    public byte[] readObject(String objectKey) {
        try (var stream = client().getObject(GetObjectArgs.builder().bucket(bucket()).object(normalizeObjectKey(objectKey)).build())) {
            return stream.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("读取 MinIO 文件失败", exception);
        }
    }

    @Override
    public InputStream openStream(String objectKey) {
        try {
            return client().getObject(GetObjectArgs.builder().bucket(bucket()).object(normalizeObjectKey(objectKey)).build());
        } catch (Exception exception) {
            throw new IllegalStateException("读取 MinIO 文件失败", exception);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket()).object(normalizeObjectKey(objectKey)).build());
        } catch (Exception exception) {
            throw new IllegalStateException("删除 MinIO 文件失败", exception);
        }
    }

    private String normalizeObjectKey(String objectKey) {
        String normalized = objectKey == null ? "" : objectKey.replace('\\', '/').replaceFirst("^/+", "").trim();
        if (normalized.isBlank()) {
            throw new BusinessException("附件路径不能为空");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new BusinessException("非法附件路径");
            }
        }
        return normalized;
    }

    private String bucket() {
        return properties.getStorage().getBucket();
    }

    private MinioClient client() {
        VisitorProperties.Minio config = properties.getStorage().getMinio();
        String signature = String.join("\n",
            String.valueOf(config.getEndpoint()),
            String.valueOf(config.getAccessKey()),
            String.valueOf(config.getSecretKey())
        );
        ClientHolder current = clientHolder;
        if (current != null && current.signature().equals(signature)) {
            return current.client();
        }
        synchronized (this) {
            current = clientHolder;
            if (current == null || !current.signature().equals(signature)) {
                MinioClient newClient = MinioClient.builder()
                    .endpoint(config.getEndpoint())
                    .credentials(config.getAccessKey(), config.getSecretKey())
                    .build();
                current = new ClientHolder(signature, newClient);
                clientHolder = current;
            }
            return current.client();
        }
    }

    private record ClientHolder(String signature, MinioClient client) {
    }
}
