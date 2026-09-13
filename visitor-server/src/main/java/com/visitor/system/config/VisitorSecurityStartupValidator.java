package com.visitor.system.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

@Component
public class VisitorSecurityStartupValidator {

    private static final String DEFAULT_JWT_SECRET = "change-this-jwt-secret-at-least-32-bytes";
    private static final String DEFAULT_AES_KEY = "";
    private static final Set<String> SAFE_PROFILES = Set.of("local", "test");

    private final VisitorProperties properties;
    private final Environment environment;

    public VisitorSecurityStartupValidator(VisitorProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        validateJwtSecret();
        validateAesKey();
        validateMockMode();
    }

    private void validateJwtSecret() {
        String jwtSecret = properties.getSecurity().getJwtSecret();
        int secretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < 32 || DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("VISITOR_JWT_SECRET 未配置为安全密钥，至少需要 32 字节且不能使用默认值");
        }
    }

    private void validateAesKey() {
        String aesKey = properties.getSecurity().getAesKey();
        int secretBytes = aesKey.getBytes(StandardCharsets.UTF_8).length;
        boolean validLength = secretBytes == 16 || secretBytes == 24 || secretBytes == 32;
        if (!validLength || DEFAULT_AES_KEY.equals(aesKey)) {
            throw new IllegalStateException("VISITOR_AES_KEY 未配置为安全密钥，长度必须为 16/24/32 字节且不能使用默认值");
        }
    }

    private void validateMockMode() {
        if (!properties.getDingtalk().isMockMode()) {
            return;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        boolean safeProfile = activeProfiles.length == 0 || Arrays.stream(activeProfiles).anyMatch(SAFE_PROFILES::contains);
        if (!safeProfile) {
            throw new IllegalStateException("钉钉 mock 模式仅允许在 local/test 环境启用");
        }
    }
}
