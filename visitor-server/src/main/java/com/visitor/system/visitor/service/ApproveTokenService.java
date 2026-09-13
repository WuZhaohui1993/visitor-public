package com.visitor.system.visitor.service;

import com.visitor.system.common.BusinessException;
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
public class ApproveTokenService {

    private final SecretKey secretKey;
    private final VisitorProperties properties;

    public ApproveTokenService(VisitorProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String bizId, String visitedUserId) {
        Date now = new Date();
        Date expireAt = Date.from(LocalDateTime.now()
            .plusHours(properties.getSecurity().getTokenHours())
            .atZone(ZoneId.systemDefault())
            .toInstant());
        return Jwts.builder()
            .subject(bizId)
            .claim("visitedUserId", visitedUserId)
            .issuedAt(now)
            .expiration(expireAt)
            .signWith(secretKey)
            .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        } catch (Exception exception) {
            throw new BusinessException("审批 token 无效或已过期");
        }
    }
}
