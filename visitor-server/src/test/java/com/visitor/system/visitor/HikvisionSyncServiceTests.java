package com.visitor.system.visitor;

import com.visitor.system.visitor.domain.HikSyncStatus;
import com.visitor.system.visitor.domain.VisitorRecord;
import com.visitor.system.visitor.dto.HikvisionFaceResult;
import com.visitor.system.visitor.dto.HikvisionGrantAccessCommand;
import com.visitor.system.visitor.repository.VisitorRecordRepository;
import com.visitor.system.visitor.service.CryptoService;
import com.visitor.system.visitor.service.HikvisionClient;
import com.visitor.system.visitor.service.HikvisionSyncService;
import com.visitor.system.visitor.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "visitor.hikvision.enabled=true",
    "visitor.hikvision.org-index-code=org-test",
    "visitor.hikvision.person-ready-max-attempts=3",
    "visitor.hikvision.person-ready-wait-millis=1",
    "visitor.hikvision.face-group-index-code=test-face-group",
    "visitor.hikvision.face-ready-max-attempts=3",
    "visitor.hikvision.face-ready-wait-millis=1",
    "visitor.hikvision.access.enabled=true",
    "visitor.hikvision.access.grant-path=/artemis/api/acps/v1/auth_config/add",
    "visitor.hikvision.access.revoke-path=/artemis/api/acps/v1/auth_config/delete",
    "visitor.hikvision.access.resource-index-codes[0]=door-1"
})
class HikvisionSyncServiceTests {

    @Autowired
    private VisitorRecordRepository visitorRecordRepository;

    @Autowired
    private HikvisionSyncService hikvisionSyncService;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Autowired
    private CryptoService cryptoService;

    @MockBean
    private HikvisionClient hikvisionClient;

    @Test
    void shouldMarkSyncSuccessWhenHikvisionAddSucceeds() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.PENDING, LocalDateTime.now().plusHours(1));
        when(hikvisionClient.addPerson(any())).thenReturn("person-id-1");
        when(hikvisionClient.addFace(any())).thenReturn(HikvisionFaceResult.builder()
            .faceIndexCode("face-index-1")
            .facePicUrl("/pic/test-face-1")
            .build());
        doNothing().when(hikvisionClient).grantAccess(any());

        hikvisionSyncService.syncApprovedRecord(record.getId());

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikAccessStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikAccessSyncTime()).isNotNull();
        assertThat(refreshed.getHikSyncTime()).isNotNull();
        assertThat(refreshed.getHikPersonId()).isEqualTo("person-id-1");
        assertThat(refreshed.getHikFaceIndexCode()).isEqualTo("face-index-1");
        assertThat(refreshed.getHikFacePicUrl()).isEqualTo("/pic/test-face-1");
        assertThat(refreshed.getHikRetryCount()).isEqualTo(1);
        assertThat(refreshed.getHikSyncError()).isNull();
    }

    @Test
    void shouldMarkSyncFailureWhenHikvisionAddFails() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.PENDING, LocalDateTime.now().plusHours(1));
        when(hikvisionClient.addPerson(any())).thenThrow(new RuntimeException("海康网关调用失败"));

        hikvisionSyncService.syncApprovedRecord(record.getId());

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.FAILED);
        assertThat(refreshed.getHikRetryCount()).isEqualTo(1);
        assertThat(refreshed.getHikSyncError()).contains("海康网关调用失败");
    }

    @Test
    void shouldNormalizeExistingHikPersonCodeBeforeSync() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.PENDING, LocalDateTime.now().plusHours(1));
        record.setHikPersonCode("123e4567-e89b-12d3-a456-426614174000");
        visitorRecordRepository.save(record);
        when(hikvisionClient.addPerson(any())).thenReturn("person-id-2");
        when(hikvisionClient.addFace(any())).thenReturn(HikvisionFaceResult.builder()
            .faceIndexCode("face-index-2")
            .facePicUrl("/pic/test-face-2")
            .build());
        doNothing().when(hikvisionClient).grantAccess(any());

        hikvisionSyncService.syncApprovedRecord(record.getId());

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikPersonCode()).isEqualTo("123e4567e89b12d3a456426614174000");
    }

    @Test
    void shouldDisableExpiredApprovedVisitor() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.SUCCESS, LocalDateTime.now().minusMinutes(5));
        record.setHikFaceIndexCode("face-index-1");
        record.setHikPersonId("person-id-1");
        record.setHikAccessStatus(HikSyncStatus.SUCCESS);
        visitorRecordRepository.save(record);
        doNothing().when(hikvisionClient).deleteFace(anyString(), anyString());
        doNothing().when(hikvisionClient).disablePerson(anyString());
        doNothing().when(hikvisionClient).revokeAccess(any());

        hikvisionSyncService.disableExpiredVisitors();

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.DISABLED);
        assertThat(refreshed.getHikAccessStatus()).isEqualTo(HikSyncStatus.DISABLED);
        assertThat(refreshed.getHikDisabledTime()).isNotNull();
        assertThat(refreshed.getHikFaceIndexCode()).isNull();
        assertThat(refreshed.getHikSyncError()).isNull();
    }

    @Test
    void shouldStillMarkVisitorDisabledWhenDisablePersonFails() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.SUCCESS, LocalDateTime.now().minusMinutes(5));
        record.setHikFaceIndexCode("face-index-2");
        record.setHikPersonId("person-id-2");
        record.setHikAccessStatus(HikSyncStatus.SUCCESS);
        visitorRecordRepository.save(record);
        doNothing().when(hikvisionClient).deleteFace(anyString(), anyString());
        doNothing().when(hikvisionClient).revokeAccess(any());
        doThrow(new RuntimeException("person disable api failed")).when(hikvisionClient).disablePerson(anyString());

        hikvisionSyncService.disableExpiredVisitors();

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.DISABLED);
        assertThat(refreshed.getHikAccessStatus()).isEqualTo(HikSyncStatus.DISABLED);
        assertThat(refreshed.getHikDisabledTime()).isNotNull();
        assertThat(refreshed.getHikFaceIndexCode()).isNull();
        assertThat(refreshed.getHikSyncError()).isNull();
    }

    @Test
    void shouldReuseExistingPersonWhenAddPersonReportsAlreadyExists() {
        VisitorRecord existing = createApprovedRecord(HikSyncStatus.SUCCESS, LocalDateTime.now().plusHours(1));
        existing.setHikPersonCode("existing-person-code");
        existing.setHikPersonId("existing-person-id");
        visitorRecordRepository.save(existing);

        VisitorRecord record = createApprovedRecord(HikSyncStatus.PENDING, LocalDateTime.now().plusHours(2));
        record.setIdCardNo(existing.getIdCardNo());
        visitorRecordRepository.save(record);

        when(hikvisionClient.addPerson(any())).thenThrow(new RuntimeException("实名标识110101199001011234在平台已存在"));
        doNothing().when(hikvisionClient).enablePerson("existingpersoncode");
        when(hikvisionClient.addFace(any())).thenReturn(HikvisionFaceResult.builder()
            .faceIndexCode("face-index-reused")
            .facePicUrl("/pic/test-face-reused")
            .build());
        doNothing().when(hikvisionClient).grantAccess(any());

        hikvisionSyncService.syncApprovedRecord(record.getId());

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikAccessStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikPersonCode()).isEqualTo("existingpersoncode");
        assertThat(refreshed.getHikPersonId()).isEqualTo("existing-person-id");
        assertThat(refreshed.getHikFaceIndexCode()).isEqualTo("face-index-reused");
        verify(hikvisionClient).enablePerson("existingpersoncode");
    }

    @Test
    void shouldReuseCurrentRecordPersonWhenRetryAfterPartialSuccess() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.FAILED, LocalDateTime.now().plusHours(1));
        record.setHikPersonCode("current-person-code");
        record.setHikPersonId("current-person-id");
        record.setHikSyncError("海康新增访客人脸失败");
        visitorRecordRepository.save(record);

        when(hikvisionClient.addFace(any())).thenReturn(HikvisionFaceResult.builder()
            .faceIndexCode("face-index-current")
            .facePicUrl("/pic/test-face-current")
            .build());
        doNothing().when(hikvisionClient).enablePerson("currentpersoncode");
        doNothing().when(hikvisionClient).grantAccess(any());

        hikvisionSyncService.syncApprovedRecord(record.getId());

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikAccessStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikPersonId()).isEqualTo("current-person-id");
        assertThat(refreshed.getHikFaceIndexCode()).isEqualTo("face-index-current");
        verify(hikvisionClient, never()).addPerson(any());
        verify(hikvisionClient).enablePerson("currentpersoncode");
    }

    @Test
    void shouldBuildAccessConfigCommandWhenGrantingAccess() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.PENDING, LocalDateTime.now().plusHours(1));
        when(hikvisionClient.addPerson(any())).thenReturn("person-id-face");
        when(hikvisionClient.addFace(any())).thenReturn(HikvisionFaceResult.builder()
            .faceIndexCode("face-index-face")
            .facePicUrl("/pic/test-face-grant")
            .build());
        doNothing().when(hikvisionClient).grantAccess(any());

        hikvisionSyncService.syncApprovedRecord(record.getId());

        org.mockito.ArgumentCaptor<HikvisionGrantAccessCommand> captor =
            org.mockito.ArgumentCaptor.forClass(HikvisionGrantAccessCommand.class);
        verify(hikvisionClient).grantAccess(captor.capture());
        HikvisionGrantAccessCommand command = captor.getValue();
        assertThat(command.getPersonId()).isEqualTo("person-id-face");
        assertThat(command.getResourceType()).isEqualTo("acsDevice");
        assertThat(command.getBeginTime()).isEqualTo(record.getPlannedEntryTime());
        assertThat(command.getEndTime()).isEqualTo(record.getPlannedExitTime());
        assertThat(command.getResourceInfos()).hasSize(1);
    }

    @Test
    void shouldRetryAddFaceWhenPersonNeedsMoreTimeToBeReady() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.PENDING, LocalDateTime.now().plusHours(1));
        when(hikvisionClient.addPerson(any())).thenReturn("person-id-delayed");
        when(hikvisionClient.addFace(any()))
            .thenThrow(new RuntimeException("海康新增访客人脸失败：0x15452501a 人员在平台上不存在"))
            .thenReturn(HikvisionFaceResult.builder()
                .faceIndexCode("face-index-delayed")
                .facePicUrl("/pic/test-face-delayed")
                .build());
        doNothing().when(hikvisionClient).grantAccess(any());

        hikvisionSyncService.syncApprovedRecord(record.getId());

        VisitorRecord refreshed = waitForRecord(record.getId(), HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikAccessStatus()).isEqualTo(HikSyncStatus.SUCCESS);
        assertThat(refreshed.getHikPersonId()).isEqualTo("person-id-delayed");
        assertThat(refreshed.getHikFaceIndexCode()).isEqualTo("face-index-delayed");
        verify(hikvisionClient, org.mockito.Mockito.times(2)).addFace(any());
    }

    @Test
    void shouldFailWhenAddPersonReportsAlreadyExistsButNoMappingFound() {
        VisitorRecord record = createApprovedRecord(HikSyncStatus.PENDING, LocalDateTime.now().plusHours(1));
        record.setIdCardNo(cryptoService.encrypt("320101199001011234"));
        visitorRecordRepository.save(record);
        when(hikvisionClient.addPerson(any())).thenThrow(new RuntimeException("实名标识110101199001011234在平台已存在"));

        hikvisionSyncService.syncApprovedRecord(record.getId());

        VisitorRecord refreshed = visitorRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(refreshed.getHikSyncStatus()).isEqualTo(HikSyncStatus.FAILED);
        assertThat(refreshed.getHikSyncError()).contains("未找到可复用的本地人员映射");
        verify(hikvisionClient, never()).grantAccess(any());
    }

    private VisitorRecord createApprovedRecord(HikSyncStatus hikSyncStatus, LocalDateTime plannedExitTime) {
        String bizId = UUID.randomUUID().toString();
        String faceObjectKey = "record/" + bizId + "/face-photo.png";
        objectStorageService.uploadTemp(createValidFaceBytes(), faceObjectKey, "image/jpeg");

        VisitorRecord record = new VisitorRecord();
        record.setBizId(bizId);
        record.setRecordNo("VIS-TEST-" + System.nanoTime());
        record.setVisitorName("测试访客");
        record.setIdCardNo(cryptoService.encrypt("110101199001011234"));
        record.setIdCardSuffix("011234");
        record.setPhone("13812345678");
        record.setFacePhoto(faceObjectKey);
        record.setVisitedUserId("u1001");
        record.setVisitedUserName("张三");
        record.setVisitedDeptName("行政部");
        record.setVisitReason("测试");
        record.setPlannedEntryTime(LocalDateTime.now().minusMinutes(10));
        record.setPlannedExitTime(plannedExitTime);
        record.setStatus(1);
        record.setHikSyncStatus(hikSyncStatus);
        record.setHikPersonCode(bizId);
        record.setHikRetryCount(0);
        record.setApproveToken("used");
        record.setTokenExpireTime(LocalDateTime.now().plusHours(1));
        record.setExpireTime(plannedExitTime);
        record.setApproveTime(LocalDateTime.now().minusMinutes(1));
        return visitorRecordRepository.save(record);
    }

    private byte[] createValidFaceBytes() {
        try {
            BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
            Random random = new Random(1);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = random.nextInt(0x1000000);
                    image.setRGB(x, y, rgb);
                }
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private VisitorRecord waitForRecord(Long recordId, HikSyncStatus expectedStatus) {
        for (int attempt = 0; attempt < 50; attempt++) {
            VisitorRecord record = visitorRecordRepository.findById(recordId).orElseThrow();
            if (record.getHikSyncStatus() == expectedStatus) {
                return record;
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
        return visitorRecordRepository.findById(recordId).orElseThrow();
    }
}
