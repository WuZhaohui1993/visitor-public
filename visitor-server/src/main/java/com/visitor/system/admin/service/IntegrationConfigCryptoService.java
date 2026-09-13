package com.visitor.system.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.admin.dto.IntegrationConfigPayload;
import com.visitor.system.config.VisitorProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class IntegrationConfigCryptoService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ObjectMapper objectMapper;
    private final SecretKeySpec key;

    public IntegrationConfigCryptoService(ObjectMapper objectMapper, VisitorProperties properties) {
        this.objectMapper = objectMapper;
        String configuredKey = properties.getAdmin().getConfigEncryptionKey();
        if (properties.getAdmin().isEnabled()) {
            int length = configuredKey == null ? 0 : configuredKey.getBytes(StandardCharsets.UTF_8).length;
            if (length < 32) {
                throw new IllegalStateException("VISITOR_ADMIN_CONFIG_KEY 未配置为安全密钥，至少需要 32 字节");
            }
        }
        String effectiveKey = configuredKey == null || configuredKey.isBlank()
            ? "disabled-admin-config-key-32-bytes-minimum"
            : configuredKey;
        this.key = new SecretKeySpec(sha256(effectiveKey.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public EncryptedPayload encrypt(IntegrationConfigPayload payload) {
        try {
            byte[] plaintext = objectMapper.writeValueAsBytes(payload);
            byte[] nonce = new byte[12];
            SECURE_RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);
            return new EncryptedPayload(
                Base64.getEncoder().encodeToString(combined),
                HexFormat.of().formatHex(sha256(plaintext))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("加密集成配置失败", exception);
        }
    }

    public IntegrationConfigPayload decrypt(String encryptedPayload) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedPayload);
            byte[] nonce = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return objectMapper.readValue(cipher.doFinal(ciphertext), IntegrationConfigPayload.class);
        } catch (Exception exception) {
            throw new IllegalStateException("解密集成配置失败", exception);
        }
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record EncryptedPayload(String ciphertext, String hash) {
    }
}
