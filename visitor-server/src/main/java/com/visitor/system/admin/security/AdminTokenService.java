package com.visitor.system.admin.security;

import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.common.UnauthorizedException;
import com.visitor.system.config.VisitorProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class AdminTokenService {

    private static final String TOKEN_TYPE = "admin-access";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_SECURITY_VERSION = "securityVersion";

    private final VisitorProperties properties;
    private final SecretKey secretKey;

    public AdminTokenService(VisitorProperties properties) {
        this.properties = properties;
        String secret = properties.getAdmin().getJwtSecret();
        if (properties.getAdmin().isEnabled()) {
            int bytes = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < 32) {
                throw new IllegalStateException("VISITOR_ADMIN_JWT_SECRET 未配置为安全密钥，至少需要 32 字节");
            }
        }
        String effectiveSecret = secret == null || secret.isBlank()
            ? "disabled-admin-token-secret-32-bytes-minimum"
            : secret;
        this.secretKey = Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
    }

    public TokenValue generate(AdminUser user) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(properties.getAdmin().getAccessTokenMinutes());
        String token = Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE)
            .claim(CLAIM_USERNAME, user.getUsername())
            .claim(CLAIM_SECURITY_VERSION, user.getSecurityVersion())
            .issuedAt(new Date())
            .expiration(Date.from(expiresAt.atZone(ZoneId.systemDefault()).toInstant()))
            .signWith(secretKey)
            .compact();
        return new TokenValue(token, expiresAt);
    }

    public TokenClaims parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
            if (!TOKEN_TYPE.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                throw new UnauthorizedException("管理员访问凭证类型不正确");
            }
            Number version = claims.get(CLAIM_SECURITY_VERSION, Number.class);
            return new TokenClaims(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                version == null ? 0L : version.longValue()
            );
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new UnauthorizedException("管理员登录已过期，请重新登录");
        }
    }

    public record TokenValue(String token, LocalDateTime expiresAt) {
    }

    public record TokenClaims(Long userId, String username, Long securityVersion) {
    }
}
