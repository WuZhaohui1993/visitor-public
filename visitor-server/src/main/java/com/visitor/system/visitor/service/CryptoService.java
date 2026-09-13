package com.visitor.system.visitor.service;

import com.visitor.system.config.VisitorProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class CryptoService {

    private static final String GCM_PREFIX = "gcm:";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final VisitorProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec secretKeySpec;

    public CryptoService(VisitorProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        byte[] bytes = properties.getSecurity().getAesKey().getBytes(StandardCharsets.UTF_8);
        this.secretKeySpec = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherBytes, 0, payload, iv.length, cipherBytes.length);
            return GCM_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("身份证号加密失败", exception);
        }
    }

    public String decrypt(String cipherText) {
        try {
            if (cipherText != null && cipherText.startsWith(GCM_PREFIX)) {
                return decryptGcm(cipherText.substring(GCM_PREFIX.length()));
            }
            return decryptLegacy(cipherText);
        } catch (Exception exception) {
            throw new IllegalStateException("身份证号解密失败", exception);
        }
    }

    private String decryptGcm(String cipherText) throws Exception {
        byte[] decoded = Base64.getDecoder().decode(cipherText);
        if (decoded.length <= GCM_IV_LENGTH) {
            throw new IllegalStateException("身份证号密文格式不正确");
        }
        byte[] iv = Arrays.copyOfRange(decoded, 0, GCM_IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(decoded, GCM_IV_LENGTH, decoded.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private String decryptLegacy(String cipherText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] decoded = Base64.getDecoder().decode(cipherText);
        return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
    }
}
