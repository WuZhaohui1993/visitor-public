package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.dto.AdminCaptchaResp;
import com.visitor.system.admin.repository.AdminUserRepository;
import com.visitor.system.common.UnauthorizedException;
import com.visitor.system.config.VisitorProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminCaptchaService {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final AdminUserRepository userRepository;
    private final VisitorProperties properties;

    public AdminCaptchaService(AdminUserRepository userRepository, VisitorProperties properties) {
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public AdminCaptchaResp issue(String rawUsername) {
        cleanup();
        String username = StringUtils.lowerCase(StringUtils.trim(rawUsername));
        AdminUser user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (!required(user)) {
            return AdminCaptchaResp.builder().required(false).build();
        }
        String code = randomCode();
        String challengeId = UUID.randomUUID().toString();
        challenges.put(challengeId, new Challenge(username, code, LocalDateTime.now().plusMinutes(5)));
        return AdminCaptchaResp.builder()
            .required(true)
            .challengeId(challengeId)
            .imageData(toDataUri(code))
            .build();
    }

    public void verifyIfRequired(AdminUser user, String challengeId, String submittedCode) {
        if (!required(user)) {
            return;
        }
        if (StringUtils.isBlank(challengeId) || StringUtils.isBlank(submittedCode)) {
            throw new UnauthorizedException("请输入图形验证码");
        }
        Challenge challenge = challenges.remove(challengeId);
        if (challenge == null
            || challenge.expiresAt().isBefore(LocalDateTime.now())
            || !challenge.username().equals(user.getUsername().toLowerCase(Locale.ROOT))
            || !challenge.code().equalsIgnoreCase(submittedCode.trim())) {
            throw new UnauthorizedException("验证码错误或已失效，请重新输入");
        }
    }

    private boolean required(AdminUser user) {
        return user != null
            && user.getFailedLoginCount() != null
            && user.getFailedLoginCount() >= properties.getAdmin().getCaptchaAfterFailures();
    }

    private String randomCode() {
        StringBuilder value = new StringBuilder(5);
        for (int index = 0; index < 5; index++) {
            value.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return value.toString();
    }

    private String toDataUri(String code) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='150' height='46' viewBox='0 0 150 46'>"
            + "<rect width='150' height='46' fill='#edf4f1'/>"
            + "<path d='M4 34L146 12M8 9L142 39M31 2L119 44' stroke='#86a69b' stroke-width='1' opacity='.55'/>"
            + "<text x='75' y='31' text-anchor='middle' font-family='monospace' font-size='24' font-weight='700' "
            + "letter-spacing='5' fill='#174f43'>" + code + "</text></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder()
            .encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }

    private void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Challenge(String username, String code, LocalDateTime expiresAt) {
    }
}
