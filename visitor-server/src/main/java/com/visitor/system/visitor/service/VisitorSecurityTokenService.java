package com.visitor.system.visitor.service;

import com.visitor.system.common.ForbiddenException;
import com.visitor.system.common.UnauthorizedException;
import com.visitor.system.config.VisitorProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class VisitorSecurityTokenService {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_BIZ_ID = "bizId";
    private static final String CLAIM_OBJECT_KEY = "objectKey";
    private static final String CLAIM_USER_ID = "userId";
    private static final String TOKEN_TYPE_USER = "user-access";
    private static final String TOKEN_TYPE_RESULT = "result-access";
    private static final String TOKEN_TYPE_RECORD_FILE = "record-file";
    private static final String TOKEN_TYPE_TEMP_FILE = "temp-file";

    private final SecretKey secretKey;
    private final VisitorProperties properties;

    public VisitorSecurityTokenService(VisitorProperties properties) {
        this.properties = properties;
        String jwtSecret = properties.getSecurity().getJwtSecret();
        int secretBytes = jwtSecret == null ? 0 : jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (secretBytes < 32) {
            throw new IllegalStateException("VISITOR_JWT_SECRET 未配置为安全密钥，至少需要 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateUserAccessToken(String userId) {
        return createToken(
            userId,
            TOKEN_TYPE_USER,
            LocalDateTime.now().plusMinutes(properties.getSecurity().getAuthTokenMinutes())
        );
    }

    public String requireUserAccess(String accessToken) {
        Claims claims = parse(accessToken);
        requireType(claims, TOKEN_TYPE_USER);
        return claims.getSubject();
    }

    public String generateResultAccessToken(String bizId) {
        return createToken(
            bizId,
            TOKEN_TYPE_RESULT,
            LocalDateTime.now().plusHours(properties.getSecurity().getTokenHours())
        );
    }

    public void validateResultAccess(String bizId, String accessToken) {
        Claims claims = parse(accessToken);
        requireType(claims, TOKEN_TYPE_RESULT);
        if (!bizId.equals(claims.getSubject())) {
            throw new ForbiddenException("结果访问凭证与当前记录不匹配");
        }
    }

    public String buildRecordFilePreviewUrl(String bizId, String objectKey) {
        String accessToken = createToken(
            objectKey,
            TOKEN_TYPE_RECORD_FILE,
            LocalDateTime.now().plusMinutes(properties.getSecurity().getFileTokenMinutes()),
            CLAIM_BIZ_ID,
            bizId,
            CLAIM_OBJECT_KEY,
            objectKey
        );
        return buildPreviewUrl(objectKey, accessToken);
    }

    public String buildTempFilePreviewUrl(String objectKey) {
        String accessToken = createToken(
            objectKey,
            TOKEN_TYPE_TEMP_FILE,
            LocalDateTime.now().plusMinutes(properties.getSecurity().getFileTokenMinutes()),
            CLAIM_OBJECT_KEY,
            objectKey
        );
        return buildPreviewUrl(objectKey, accessToken);
    }

    public FileAccess validateFileAccess(String objectKey, String accessToken) {
        Claims claims = parse(accessToken);
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        String tokenObjectKey = claims.get(CLAIM_OBJECT_KEY, String.class);
        if (!objectKey.equals(claims.getSubject()) || !objectKey.equals(tokenObjectKey)) {
            throw new ForbiddenException("附件访问凭证与当前文件不匹配");
        }
        if (TOKEN_TYPE_TEMP_FILE.equals(tokenType)) {
            return new FileAccess(tokenType, null, objectKey);
        }
        if (!TOKEN_TYPE_RECORD_FILE.equals(tokenType)) {
            throw new UnauthorizedException("附件访问凭证无效或已过期");
        }
        return new FileAccess(tokenType, claims.get(CLAIM_BIZ_ID, String.class), objectKey);
    }

    private void requireType(Claims claims, String expectedType) {
        String actualType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedType.equals(actualType)) {
            throw new UnauthorizedException("访问凭证类型不正确");
        }
    }

    private Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("缺少访问凭证");
        }
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        } catch (Exception exception) {
            throw new UnauthorizedException("访问凭证无效或已过期");
        }
    }

    private String createToken(String subject, String tokenType, LocalDateTime expireAt, Object... additionalClaims) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
            .subject(subject)
            .claim(CLAIM_TOKEN_TYPE, tokenType)
            .issuedAt(new Date())
            .expiration(Date.from(expireAt.atZone(ZoneId.systemDefault()).toInstant()))
            .signWith(secretKey);
        for (int index = 0; index + 1 < additionalClaims.length; index += 2) {
            builder.claim(String.valueOf(additionalClaims[index]), additionalClaims[index + 1]);
        }
        return builder.compact();
    }

    private String buildPreviewUrl(String objectKey, String accessToken) {
        return UriComponentsBuilder.fromUriString(properties.getStorage().getPreviewBaseUrl())
            .path("/" + objectKey)
            .queryParam("accessToken", accessToken)
            .build(true)
            .toUriString();
    }

    public record FileAccess(String tokenType, String bizId, String objectKey) {
    }
}
