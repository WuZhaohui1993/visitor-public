package com.visitor.system.visitor.service.impl;

import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.service.ObjectStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(prefix = "visitor.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {

    private final VisitorProperties properties;

    public LocalObjectStorageService(VisitorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String uploadTemp(byte[] bytes, String objectKey, String contentType) {
        try {
            Path path = resolvePath(objectKey);
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            return objectKey;
        } catch (IOException exception) {
            throw new IllegalStateException("写入本地文件失败", exception);
        }
    }

    @Override
    public String moveToBizPath(String sourceObjectKey, String targetObjectKey) {
        try {
            Path source = resolvePath(sourceObjectKey);
            Path target = resolvePath(targetObjectKey);
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return targetObjectKey;
        } catch (IOException exception) {
            throw new IllegalStateException("归档附件失败", exception);
        }
    }

    @Override
    public String generatePreviewUrl(String objectKey) {
        return properties.getStorage().getPreviewBaseUrl() + "/" + objectKey;
    }

    @Override
    public byte[] readObject(String objectKey) {
        try {
            return Files.readAllBytes(resolvePath(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("读取文件失败", exception);
        }
    }

    @Override
    public InputStream openStream(String objectKey) {
        try {
            return Files.newInputStream(resolvePath(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("读取文件失败", exception);
        }
    }

    @Override
    public void deleteObject(String objectKey) {
        try {
            Files.deleteIfExists(resolvePath(objectKey));
        } catch (IOException exception) {
            throw new IllegalStateException("删除文件失败", exception);
        }
    }

    private Path resolvePath(String objectKey) {
        String normalizedObjectKey = normalizeObjectKey(objectKey);
        Path relativePath = Path.of(normalizedObjectKey).normalize();
        if (relativePath.isAbsolute() || normalizedObjectKey.startsWith("..") || normalizedObjectKey.contains("/../")) {
            throw new BusinessException("非法附件路径");
        }
        Path rootPath = Path.of(properties.getStorage().getLocalRoot()).toAbsolutePath().normalize();
        Path resolvedPath = rootPath.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new BusinessException("非法附件路径");
        }
        return resolvedPath;
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
}
