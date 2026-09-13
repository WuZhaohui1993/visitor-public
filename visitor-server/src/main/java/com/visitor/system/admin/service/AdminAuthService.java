package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminRefreshSession;
import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.domain.AdminPasswordHistory;
import com.visitor.system.admin.dto.AdminChangePasswordReq;
import com.visitor.system.admin.dto.AdminLoginReq;
import com.visitor.system.admin.dto.AdminLoginResp;
import com.visitor.system.admin.dto.AdminProfileResp;
import com.visitor.system.admin.repository.AdminRefreshSessionRepository;
import com.visitor.system.admin.repository.AdminUserRepository;
import com.visitor.system.admin.repository.AdminPasswordHistoryRepository;
import com.visitor.system.admin.security.AdminPrincipal;
import com.visitor.system.admin.security.AdminTokenService;
import com.visitor.system.common.BusinessException;
import com.visitor.system.common.UnauthorizedException;
import com.visitor.system.config.VisitorProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class AdminAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,72}$";

    private final VisitorProperties properties;
    private final AdminUserRepository userRepository;
    private final AdminRefreshSessionRepository sessionRepository;
    private final AdminPasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminTokenService tokenService;
    private final AdminCaptchaService captchaService;
    private final AdminAccessService accessService;
    private final AdminAuditService auditService;

    public AdminAuthService(VisitorProperties properties,
                            AdminUserRepository userRepository,
                            AdminRefreshSessionRepository sessionRepository,
                            AdminPasswordHistoryRepository passwordHistoryRepository,
                            PasswordEncoder passwordEncoder,
                            AdminTokenService tokenService,
                            AdminCaptchaService captchaService,
                            AdminAccessService accessService,
                            AdminAuditService auditService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.captchaService = captchaService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthBundle login(AdminLoginReq request, String ipAddress, String userAgent) {
        requireEnabled();
        String username = StringUtils.lowerCase(StringUtils.trim(request.getUsername()));
        AdminUser user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (user == null) {
            auditService.failure(username, "AUTH_LOGIN", "账号或密码错误", ipAddress, userAgent);
            throw new UnauthorizedException("账号或密码错误");
        }
        if (!user.isEnabled()) {
            auditService.failure(username, "AUTH_LOGIN", "账号已停用", ipAddress, userAgent);
            throw new UnauthorizedException("账号或密码错误");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            auditService.failure(username, "AUTH_LOGIN", "账号处于锁定状态", ipAddress, userAgent);
            throw new UnauthorizedException("登录失败次数过多，请稍后再试");
        }
        captchaService.verifyIfRequired(user, request.getCaptchaId(), request.getCaptchaCode());
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int failures = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
            user.setFailedLoginCount(failures);
            if (failures >= properties.getAdmin().getMaxLoginFailures()) {
                user.setLockedUntil(now.plusMinutes(properties.getAdmin().getLockMinutes()));
            }
            userRepository.save(user);
            auditService.failure(username, "AUTH_LOGIN", "账号或密码错误", ipAddress, userAgent);
            throw new UnauthorizedException("账号或密码错误");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginTime(now);
        userRepository.save(user);
        AdminPrincipal principal = accessService.principal(user);
        auditService.success(principal, "AUTH_LOGIN", "ADMIN_USER", String.valueOf(user.getId()), "管理员登录成功", ipAddress, userAgent);
        return issueBundle(user, ipAddress, userAgent);
    }

    @Transactional
    public AuthBundle refresh(String refreshToken, String ipAddress, String userAgent) {
        requireEnabled();
        if (StringUtils.isBlank(refreshToken)) {
            throw new UnauthorizedException("刷新凭证缺失，请重新登录");
        }
        AdminRefreshSession session = sessionRepository.findByTokenHashAndRevokedTimeIsNull(hash(refreshToken))
            .orElseThrow(() -> new UnauthorizedException("刷新凭证无效，请重新登录"));
        LocalDateTime now = LocalDateTime.now();
        if (!session.getExpiresTime().isAfter(now)) {
            session.setRevokedTime(now);
            sessionRepository.save(session);
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        AdminUser user = userRepository.findById(session.getUserId())
            .orElseThrow(() -> new UnauthorizedException("管理员账号不存在"));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("管理员账号已停用");
        }
        session.setRevokedTime(now);
        sessionRepository.save(session);
        return issueBundle(user, ipAddress, userAgent);
    }

    @Transactional
    public void logout(String refreshToken, AdminPrincipal principal, String ipAddress, String userAgent) {
        AdminPrincipal actor = principal;
        if (StringUtils.isNotBlank(refreshToken)) {
            AdminRefreshSession session = sessionRepository.findByTokenHashAndRevokedTimeIsNull(hash(refreshToken)).orElse(null);
            if (session != null) {
                if (actor == null) {
                    actor = userRepository.findById(session.getUserId())
                        .filter(AdminUser::isEnabled)
                        .map(accessService::principal)
                        .orElse(null);
                }
                session.setRevokedTime(LocalDateTime.now());
                sessionRepository.save(session);
            }
        }
        if (actor != null) {
            auditService.success(actor, "AUTH_LOGOUT", "ADMIN_USER", String.valueOf(actor.userId()), "管理员退出登录", ipAddress, userAgent);
        }
    }

    @Transactional(readOnly = true)
    public AdminProfileResp profile(Long userId) {
        return accessService.profile(requireUser(userId));
    }

    @Transactional
    public void changePassword(Long userId, AdminChangePasswordReq request, String ipAddress, String userAgent) {
        AdminUser user = requireUser(userId);
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        validatePasswordChange(user, request.getNewPassword());
        rememberCurrentPassword(user);
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedTime(LocalDateTime.now());
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userRepository.save(user);
        revokeActiveSessions(userId);
        AdminPrincipal principal = accessService.principal(user);
        auditService.success(principal, "AUTH_CHANGE_PASSWORD", "ADMIN_USER", String.valueOf(userId), "管理员修改密码", ipAddress, userAgent);
    }

    public void validatePassword(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new BusinessException("密码至少12位，并同时包含大小写字母、数字和符号");
        }
    }

    public void validatePasswordChange(AdminUser user, String newPassword) {
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())
            || passwordHistoryRepository.findByUserIdOrderByCreateTimeDesc(user.getId()).stream()
                .limit(5)
                .anyMatch(history -> passwordEncoder.matches(newPassword, history.getPasswordHash()))) {
            throw new BusinessException("新密码不能与当前或最近5次使用的密码相同");
        }
    }

    public void rememberCurrentPassword(AdminUser user) {
        AdminPasswordHistory history = new AdminPasswordHistory();
        history.setUserId(user.getId());
        history.setPasswordHash(user.getPasswordHash());
        passwordHistoryRepository.save(history);
        List<AdminPasswordHistory> entries = passwordHistoryRepository.findByUserIdOrderByCreateTimeDesc(user.getId());
        if (entries.size() > 5) {
            passwordHistoryRepository.deleteAll(entries.subList(5, entries.size()));
        }
    }

    private AuthBundle issueBundle(AdminUser user, String ipAddress, String userAgent) {
        AdminTokenService.TokenValue accessToken = tokenService.generate(user);
        String refreshToken = randomToken();
        AdminRefreshSession session = new AdminRefreshSession();
        session.setUserId(user.getId());
        session.setTokenHash(hash(refreshToken));
        session.setExpiresTime(LocalDateTime.now().plusHours(properties.getAdmin().getRefreshTokenHours()));
        session.setIpAddress(ipAddress);
        session.setUserAgent(StringUtils.abbreviate(userAgent, 300));
        sessionRepository.save(session);
        long expiresIn = Math.max(1, Duration.between(LocalDateTime.now(), accessToken.expiresAt()).toSeconds());
        AdminLoginResp response = AdminLoginResp.builder()
            .accessToken(accessToken.token())
            .expiresInSeconds(expiresIn)
            .user(accessService.profile(user))
            .build();
        return new AuthBundle(response, refreshToken, session.getExpiresTime());
    }

    private AdminUser requireUser(Long userId) {
        AdminUser user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("管理员账号不存在"));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("管理员账号已停用");
        }
        return user;
    }

    private void revokeActiveSessions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        sessionRepository.findByUserIdAndRevokedTimeIsNullAndExpiresTimeAfter(userId, now).forEach(session -> {
            session.setRevokedTime(now);
            sessionRepository.save(session);
        });
    }

    private void requireEnabled() {
        if (!properties.getAdmin().isEnabled()) {
            throw new UnauthorizedException("后台管理功能未启用");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成刷新凭证摘要", exception);
        }
    }

    public record AuthBundle(AdminLoginResp response, String refreshToken, LocalDateTime refreshExpiresAt) {
    }
}
