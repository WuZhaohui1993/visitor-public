package com.visitor.system.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.admin.domain.AdminUser;
import com.visitor.system.admin.repository.AdminAuditLogRepository;
import com.visitor.system.admin.repository.AdminUserRepository;
import com.visitor.system.visitor.domain.VisitorRecord;
import com.visitor.system.visitor.repository.VisitorRecordRepository;
import com.visitor.system.visitor.service.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTests {

    private static final String PASSWORD = "test-only-password";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AdminUserRepository userRepository;

    @Autowired
    AdminAuditLogRepository auditLogRepository;

    @Autowired
    VisitorRecordRepository visitorRecordRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    CryptoService cryptoService;

    @BeforeEach
    void resetAdmin() {
        visitorRecordRepository.deleteAll();
        auditLogRepository.deleteAll();
        AdminUser admin = userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setMustChangePassword(false);
        admin.setEnabled(true);
        admin.setFailedLoginCount(0);
        admin.setLockedUntil(null);
        admin.setSecurityVersion(admin.getSecurityVersion() + 1);
        userRepository.save(admin);
    }

    @Test
    void shouldLoginLoadProfileAndRoutes() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.permissions").isArray());

        mockMvc.perform(get("/api/admin/auth/routes").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].path").value("/dashboard"));
    }

    @Test
    void shouldLogoutWithRefreshCookieWithoutAccessToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
        MockCookie refreshCookie = MockCookie.parse(loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE));

        mockMvc.perform(post("/api/admin/auth/logout").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void shouldRejectInvalidPasswordAndRecordAudit() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));

        assertThat(auditLogRepository.findAll()).anyMatch(log -> "AUTH_LOGIN".equals(log.getAction()) && "FAILED".equals(log.getResult()));
    }

    @Test
    void shouldRequireCaptchaAfterThirdFailedLogin() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/admin/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/admin/auth/captcha").param("username", "admin"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.required").value(true))
            .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
            .andExpect(jsonPath("$.data.imageData").value(org.hamcrest.Matchers.startsWith("data:image/svg+xml;base64,")));

        mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("请输入图形验证码"));
    }

    @Test
    void shouldKeepAdminAndVisitorTokensSeparated() throws Exception {
        String token = login();

        mockMvc.perform(get("/api/visitor/approve-detail/not-found")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListFullVisitorDetailAndWriteAudit() throws Exception {
        VisitorRecord record = createVisitorRecord();
        String token = login();

        mockMvc.perform(get("/api/admin/records/{bizId}", record.getBizId())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phone").value("13800138000"))
            .andExpect(jsonPath("$.data.idCardNo").value("610102199001011234"));

        assertThat(auditLogRepository.findAll()).anyMatch(log ->
            "RECORD_VIEW_DETAIL".equals(log.getAction()) && record.getBizId().equals(log.getTargetId()));
    }

    @Test
    void shouldReturnDashboardSummary() throws Exception {
        createVisitorRecord();
        String token = login();

        mockMvc.perform(get("/api/admin/dashboard/summary")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.todayTotal").isNumber())
            .andExpect(jsonPath("$.data.trend.length()").value(7));
    }

    @Test
    void shouldExportPlaintextVisitorDataAndWriteAudit() throws Exception {
        VisitorRecord record = createVisitorRecord();
        String token = login();

        mockMvc.perform(get("/api/admin/records/export")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].bizId").value(record.getBizId()))
            .andExpect(jsonPath("$.data[0].phone").value("13800138000"));

        assertThat(auditLogRepository.findAll()).anyMatch(log -> "RECORD_EXPORT".equals(log.getAction()));
    }

    private String login() throws Exception {
        String body = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.path("data").path("accessToken").asText();
    }

    private VisitorRecord createVisitorRecord() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        VisitorRecord record = new VisitorRecord();
        record.setBizId(UUID.randomUUID().toString());
        record.setRecordNo("VR20260714" + suffix);
        record.setVisitorName("测试访客");
        record.setIdCardNo(cryptoService.encrypt("610102199001011234"));
        record.setIdCardSuffix("011234");
        record.setPhone("13800138000");
        record.setVisitedUserId("u1001");
        record.setVisitedUserName("接待人");
        record.setVisitedDeptName("综合管理部");
        record.setVisitReason("后台管理测试");
        record.setPlannedEntryTime(LocalDateTime.now().plusHours(1));
        record.setPlannedExitTime(LocalDateTime.now().plusHours(3));
        record.setStatus(0);
        record.setApproveToken("test-token");
        record.setTokenExpireTime(LocalDateTime.now().plusHours(24));
        record.setExpireTime(LocalDateTime.now().plusHours(3));
        record.setCreateTime(LocalDateTime.now());
        return visitorRecordRepository.save(record);
    }
}
