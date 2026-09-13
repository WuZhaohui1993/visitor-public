package com.visitor.system.admin;

import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.dto.AdminChangePasswordReq;
import com.visitor.system.admin.repository.AdminIdempotencyRecordRepository;
import com.visitor.system.admin.repository.AdminPasswordHistoryRepository;
import com.visitor.system.admin.repository.AdminUserRepository;
import com.visitor.system.admin.service.AdminAuthService;
import com.visitor.system.admin.service.AdminIdempotencyService;
import com.visitor.system.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AdminSecurityServiceTests {

    private static final String ORIGINAL_PASSWORD = "test-only-password";
    private static final String NEW_PASSWORD = "ChangedAdmin@2026";

    @Autowired
    AdminAuthService authService;

    @Autowired
    AdminIdempotencyService idempotencyService;

    @Autowired
    AdminUserRepository userRepository;

    @Autowired
    AdminPasswordHistoryRepository passwordHistoryRepository;

    @Autowired
    AdminIdempotencyRecordRepository idempotencyRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private AdminUser admin;

    @BeforeEach
    void reset() {
        passwordHistoryRepository.deleteAll();
        idempotencyRepository.deleteAll();
        admin = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode(ORIGINAL_PASSWORD));
        admin.setMustChangePassword(false);
        admin.setEnabled(true);
        admin.setSecurityVersion(admin.getSecurityVersion() + 1);
        admin = userRepository.save(admin);
    }

    @Test
    void shouldRejectOneOfLastFivePasswords() {
        AdminChangePasswordReq firstChange = request(ORIGINAL_PASSWORD, NEW_PASSWORD);
        authService.changePassword(admin.getId(), firstChange, "127.0.0.1", "test");

        AdminChangePasswordReq reuse = request(NEW_PASSWORD, ORIGINAL_PASSWORD);
        assertThatThrownBy(() -> authService.changePassword(admin.getId(), reuse, "127.0.0.1", "test"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("最近5次");
        assertThat(passwordHistoryRepository.findByUserIdOrderByCreateTimeDesc(admin.getId())).hasSize(1);
    }

    @Test
    void shouldRejectReusedIdempotencyKeyForSameActor() {
        String key = "b130da70-7688-4ddc-ab35-42ca273da421";
        idempotencyService.claim(admin.getId(), key, "/records/one/operations/revoke");

        assertThatThrownBy(() -> idempotencyService.claim(admin.getId(), key, "/records/one/operations/revoke"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("重复");
        assertThat(idempotencyRepository.count()).isEqualTo(1);
    }

    private AdminChangePasswordReq request(String currentPassword, String newPassword) {
        AdminChangePasswordReq request = new AdminChangePasswordReq();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return request;
    }
}
