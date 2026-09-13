package com.visitor.system.visitor.service;

import java.io.InputStream;

public interface ObjectStorageService {

    String uploadTemp(byte[] bytes, String objectKey, String contentType);

    String moveToBizPath(String sourceObjectKey, String targetObjectKey);

    String generatePreviewUrl(String objectKey);

    byte[] readObject(String objectKey);

    InputStream openStream(String objectKey);

    void deleteObject(String objectKey);
}
