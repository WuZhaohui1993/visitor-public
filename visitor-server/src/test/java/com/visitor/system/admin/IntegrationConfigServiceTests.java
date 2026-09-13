package com.visitor.system.admin;

import com.visitor.system.admin.domain.IntegrationConfigVersion;
import com.visitor.system.admin.dto.IntegrationConfigPayload;
import com.visitor.system.admin.dto.IntegrationConfigVersionResp;
import com.visitor.system.admin.dto.IntegrationConfigViewResp;
import com.visitor.system.admin.repository.IntegrationConfigVersionRepository;
import com.visitor.system.admin.service.IntegrationConfigProbeService;
import com.visitor.system.admin.service.IntegrationConfigService;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class IntegrationConfigServiceTests {

    @Autowired
    IntegrationConfigService service;

    @Autowired
    IntegrationConfigVersionRepository repository;

    @Autowired
    VisitorProperties properties;

    @MockBean
    IntegrationConfigProbeService probeService;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        properties.getDingtalk().setEnabled(false);
        properties.getDingtalk().setMockMode(true);
        properties.getDingtalk().setAppKey("original-dingtalk-key");
        properties.getDingtalk().setAppSecret("original-dingtalk-secret");
        properties.getHikvision().setEnabled(false);
        properties.getHikvision().setAppKey("original-hikvision-key");
        properties.getHikvision().setAppSecret("original-hikvision-secret");
        properties.getStorage().setProvider("local");
        properties.getStorage().setBucket("visitor");
        properties.getStorage().setLocalRoot("./target/test-config-storage");
        properties.getStorage().getMinio().setSecretKey("original-minio-secret");
        when(probeService.test(any())).thenReturn("全部连通测试通过");
    }

    @Test
    void shouldEncryptMaskReviewPublishRestoreAndRollbackConfiguration() throws Exception {
        IntegrationConfigVersionResp version1 = service.createDraft(payload("bucket-v1", "secret-v1"), 101L);
        IntegrationConfigVersion stored = repository.findById(version1.getId()).orElseThrow();
        assertThat(stored.getEncryptedPayload()).doesNotContain("secret-v1");

        service.test(version1.getId(), 101L);
        service.submit(version1.getId(), 101L);
        assertThatThrownBy(() -> service.publish(version1.getId(), 101L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("另一名管理员");
        service.publish(version1.getId(), 202L);

        IntegrationConfigViewResp effective = service.effective();
        assertThat(effective.getConfig().getDingtalk().getAppSecret()).isEqualTo("********");
        assertThat(effective.getConfig().getStorage().getBucket()).isEqualTo("bucket-v1");
        assertThat(properties.getDingtalk().getAppSecret()).isEqualTo("secret-v1");

        IntegrationConfigVersionResp version2 = service.createDraft(payload("bucket-v2", "secret-v2"), 101L);
        service.test(version2.getId(), 101L);
        service.submit(version2.getId(), 101L);
        service.publish(version2.getId(), 202L);
        assertThat(properties.getStorage().getBucket()).isEqualTo("bucket-v2");

        IntegrationConfigVersionResp rollback = service.createRollbackDraft(version1.getId(), 303L);
        assertThat(rollback.getStatus()).isEqualTo(IntegrationConfigService.PENDING_REVIEW);
        service.publish(rollback.getId(), 404L);
        assertThat(properties.getStorage().getBucket()).isEqualTo("bucket-v1");
        assertThat(repository.findAll().stream()
            .filter(item -> IntegrationConfigService.PUBLISHED.equals(item.getStatus())))
            .hasSize(1);

        properties.getStorage().setBucket("runtime-drift");
        service.run(null);
        assertThat(properties.getStorage().getBucket()).isEqualTo("bucket-v1");
    }

    @Test
    void shouldRejectStaleConnectivityTest() {
        IntegrationConfigVersionResp draft = service.createDraft(payload("stale-bucket", "secret"), 101L);
        service.test(draft.getId(), 101L);
        IntegrationConfigVersion stored = repository.findById(draft.getId()).orElseThrow();
        stored.setTestedTime(LocalDateTime.now().minusMinutes(11));
        repository.saveAndFlush(stored);

        assertThatThrownBy(() -> service.submit(draft.getId(), 101L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("最近10分钟");
    }

    @Test
    void shouldPersistFailedProbeAndBlockSubmission() {
        when(probeService.test(any())).thenThrow(new BusinessException("网关不可达"));
        IntegrationConfigVersionResp draft = service.createDraft(payload("failed-bucket", "secret"), 101L);

        IntegrationConfigVersionResp tested = service.test(draft.getId(), 101L);
        assertThat(tested.getTestStatus()).isEqualTo("FAILED");
        assertThat(tested.getTestSummary()).contains("网关不可达");
        assertThatThrownBy(() -> service.submit(draft.getId(), 101L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("连通测试");
    }

    private IntegrationConfigPayload payload(String bucket, String dingtalkSecret) {
        IntegrationConfigPayload payload = new IntegrationConfigPayload();
        payload.getDingtalk().setEnabled(false);
        payload.getDingtalk().setMockMode(true);
        payload.getDingtalk().setAppKey("dingtalk-key");
        payload.getDingtalk().setAppSecret(dingtalkSecret);
        payload.getDingtalk().setRootDeptId(1L);
        payload.getHikvision().setEnabled(false);
        payload.getHikvision().setAppKey("hikvision-key");
        payload.getHikvision().setAppSecret("hikvision-secret");
        payload.getHikvision().getAccess().setEnabled(false);
        payload.getStorage().setProvider("local");
        payload.getStorage().setBucket(bucket);
        payload.getStorage().setLocalRoot("./target/test-config-storage");
        payload.getStorage().setPreviewBaseUrl("http://127.0.0.1/files");
        payload.getStorage().getMinio().setEndpoint("http://127.0.0.1:9000");
        payload.getStorage().getMinio().setAccessKey("minio-key");
        payload.getStorage().getMinio().setSecretKey("minio-secret");
        return payload;
    }
}
