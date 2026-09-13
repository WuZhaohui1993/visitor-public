package com.visitor.system.visitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.service.HikvisionFaceScoreService;
import com.visitor.system.visitor.service.impl.HikvisionHttpSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HikvisionFaceScoreServiceTests {

    @Mock
    private HikvisionHttpSupport hikvisionHttpSupport;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectWhenFaceScoreMarksImageAsUnqualified() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setEnabled(true);
        properties.getHikvision().setFaceScoreEnabled(true);
        properties.getHikvision().setFaceScorePath("/artemis/api/frs/v1/face/picture/check");
        when(hikvisionHttpSupport.postRaw(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0x1f913016","msg":"评分失败","data":{"checkResult":false,"statusCode":"0x1f902306","statusMessage":"图片清晰度过低","faceScore":32}}
                """));
        HikvisionFaceScoreService service = new HikvisionFaceScoreService(properties, hikvisionHttpSupport);

        assertThatThrownBy(() -> service.validateFaceImage(new byte[]{1, 2, 3}))
            .isInstanceOf(BusinessException.class)
            .hasMessage("人脸照片质量不符合海康要求：图片清晰度过低");
    }

    @Test
    void shouldTranslateTopLevelFaceScoreFailureCode() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setEnabled(true);
        properties.getHikvision().setFaceScoreEnabled(true);
        when(hikvisionHttpSupport.postRaw(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0x1f913016","msg":"face detect failed","data":{"checkResult":false,"statusCode":"0x1f913016","statusMessage":"face detect failed"}}
                """));
        HikvisionFaceScoreService service = new HikvisionFaceScoreService(properties, hikvisionHttpSupport);

        assertThatThrownBy(() -> service.validateFaceImage(new byte[]{1, 2, 3}))
            .isInstanceOf(BusinessException.class)
            .hasMessage("人脸照片质量不符合海康要求：未检测到合格人脸，请上传光线充足、无遮挡的清晰正脸照");
    }

    @Test
    void shouldTranslateKnownRootErrorCodeWhenStatusCodeIsMissing() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setEnabled(true);
        properties.getHikvision().setFaceScoreEnabled(true);
        when(hikvisionHttpSupport.postRaw(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0x1f902300","msg":"face detect failed","data":{"checkResult":false,"statusMessage":"face detect failed"}}
                """));
        HikvisionFaceScoreService service = new HikvisionFaceScoreService(properties, hikvisionHttpSupport);

        assertThatThrownBy(() -> service.validateFaceImage(new byte[]{1, 2, 3}))
            .isInstanceOf(BusinessException.class)
            .hasMessage("人脸照片质量不符合海康要求：图片格式不符合要求或未检测到人脸");
    }

    @Test
    void shouldUseChineseMessageForKnownIgnoredErrorCode() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setEnabled(true);
        properties.getHikvision().setFaceScoreEnabled(true);
        when(hikvisionHttpSupport.postRaw(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0x1f902302","msg":"unauthority","data":null}
                """));
        HikvisionFaceScoreService service = new HikvisionFaceScoreService(properties, hikvisionHttpSupport);

        assertThatCode(() -> service.validateFaceImage(new byte[]{1, 2, 3}))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowUploadWhenFaceScoreServiceIsUnavailable() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setEnabled(true);
        properties.getHikvision().setFaceScoreEnabled(true);
        when(hikvisionHttpSupport.postRaw(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0x1f902302","msg":"评分算法未授权","data":null}
                """));
        HikvisionFaceScoreService service = new HikvisionFaceScoreService(properties, hikvisionHttpSupport);

        assertThatCode(() -> service.validateFaceImage(new byte[]{1, 2, 3}))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldSkipFaceScoreWhenFeatureDisabled() {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setEnabled(true);
        properties.getHikvision().setFaceScoreEnabled(false);
        HikvisionFaceScoreService service = new HikvisionFaceScoreService(properties, hikvisionHttpSupport);

        assertThatCode(() -> service.validateFaceImage(new byte[]{1, 2, 3}))
            .doesNotThrowAnyException();
        verifyNoInteractions(hikvisionHttpSupport);
    }
}
