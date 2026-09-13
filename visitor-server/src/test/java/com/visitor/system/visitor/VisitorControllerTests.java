package com.visitor.system.visitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.visitor.system.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.visitor.repository.VisitorRecordRepository;
import com.visitor.system.visitor.service.HikvisionFaceScoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VisitorControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VisitorRecordRepository visitorRecordRepository;

    @MockBean
    private HikvisionFaceScoreService hikvisionFaceScoreService;

    @Test
    void shouldRegisterAndApproveFlow() throws Exception {
        String frontKey = uploadFile("front.png");
        String backKey = uploadFile("back.png");
        String faceKey = uploadFaceFile("face.png");

        String registerResp = mockMvc.perform(post("/api/visitor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "visitorName":"测试访客",
                      "idCardNo":"110101199001011234",
                      "phone":"13812345678",
                      "idCardFrontKey":"%s",
                      "idCardBackKey":"%s",
                      "facePhotoKey":"%s",
                      "visitedUserId":"u1001",
                      "visitedUserName":"张三",
                      "visitedDeptName":"行政部",
                      "plannedEntryTime":"2030-04-15T10:00:00",
                      "plannedExitTime":"2030-04-15T12:00:00",
                      "visitReason":"商务拜访"
                    }
                    """.formatted(frontKey, backKey, faceKey)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bizId").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode registerJson = objectMapper.readTree(registerResp);
        String bizId = registerJson.at("/data/bizId").asText();
        String resultAccessToken = registerJson.at("/data/resultAccessToken").asText();

        String resultResp = mockMvc.perform(get("/api/visitor/result/{bizId}", bizId).param("accessToken", resultAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode resultJson = objectMapper.readTree(resultResp);
        assertThat(resultJson.at("/data/bizId").asText()).isEqualTo(bizId);
        assertThat(resultJson.at("/data/resultAccessToken").asText()).isNotBlank();

        String authResp = mockMvc.perform(post("/api/visitor/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "authCode":"mock-u1001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("u1001"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String userAccessToken = objectMapper.readTree(authResp).at("/data/accessToken").asText();

        String detailResp = mockMvc.perform(get("/api/visitor/approve-detail/{bizId}", bizId)
                .header("Authorization", "Bearer " + userAccessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.facePhotoUrl").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode detailJson = objectMapper.readTree(detailResp);
        String approveBizId = detailJson.at("/data/bizId").asText();
        assertThat(approveBizId).isEqualTo(bizId);
        String facePhotoUrl = detailJson.at("/data/facePhotoUrl").asText();
        URI facePhotoUri = URI.create(facePhotoUrl);
        String facePhotoPath = facePhotoUri.getPath();
        String facePhotoAccessToken = facePhotoUri.getQuery().replace("accessToken=", "");

        mockMvc.perform(get(facePhotoPath).param("accessToken", facePhotoAccessToken))
            .andExpect(status().isOk());

        String approveToken = visitorRecordRepository.findByBizId(bizId).orElseThrow().getApproveToken();
        mockMvc.perform(post("/api/visitor/approve")
                .header("Authorization", "Bearer " + userAccessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bizId":"%s",
                      "token":"%s",
                      "status":1,
                      "remark":""
                    }
                    """.formatted(bizId, approveToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(post("/api/visitor/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "phone":"13812345678",
                      "idCardSuffix":"11234"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/visitor/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "phone":"13812345678",
                      "idCardSuffix":"011234"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].bizId").value(bizId))
            .andExpect(jsonPath("$.data[0].resultAccessToken").isNotEmpty());
    }

    @Test
    void shouldRejectInvalidSearchKeyword() throws Exception {
        mockMvc.perform(get("/api/visitor/search").param("q", "张"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectApproveDetailWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/visitor/approve-detail/{bizId}", "missing-biz-id"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectResultWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/visitor/result/{bizId}", "missing-biz-id"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidFaceUploadEarly() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "face.png", MediaType.IMAGE_PNG_VALUE, createSmallPngBytes());

        mockMvc.perform(multipart("/api/visitor/upload").file(file).param("scene", "face"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("人脸照片不符合海康要求，请上传光线充足、无遮挡的清晰正脸照"));
    }

    @Test
    void shouldRejectFaceUploadWhenHikvisionFaceScoreFails() throws Exception {
        doThrow(new BusinessException("人脸照片质量不符合海康要求：图片清晰度过低"))
            .when(hikvisionFaceScoreService)
            .validateFaceImage(any());
        MockMultipartFile file = new MockMultipartFile("file", "face.png", MediaType.IMAGE_PNG_VALUE, createPngBytes(320, 320, true));

        mockMvc.perform(multipart("/api/visitor/upload").file(file).param("scene", "face"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("人脸照片质量不符合海康要求：图片清晰度过低"));
    }

    @Test
    void shouldRejectReusingUploadedFilesAcrossDifferentRecords() throws Exception {
        String frontKey = uploadFile("front.png");
        String backKey = uploadFile("back.png");
        String faceKey = uploadFaceFile("face.png");

        mockMvc.perform(post("/api/visitor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "visitorName":"测试访客1",
                      "idCardNo":"110101199001011234",
                      "phone":"13812345678",
                      "idCardFrontKey":"%s",
                      "idCardBackKey":"%s",
                      "facePhotoKey":"%s",
                      "visitedUserId":"u1001",
                      "visitedUserName":"张三",
                      "visitedDeptName":"行政部",
                      "plannedEntryTime":"2030-04-15T10:00:00",
                      "plannedExitTime":"2030-04-15T12:00:00",
                      "visitReason":"商务拜访"
                    }
                    """.formatted(frontKey, backKey, faceKey)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/visitor/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "visitorName":"测试访客2",
                      "idCardNo":"110101199001011235",
                      "phone":"13812345679",
                      "idCardFrontKey":"%s",
                      "idCardBackKey":"%s",
                      "facePhotoKey":"%s",
                      "visitedUserId":"u1002",
                      "visitedUserName":"李四",
                      "visitedDeptName":"技术部",
                      "plannedEntryTime":"2030-04-16T10:00:00",
                      "plannedExitTime":"2030-04-16T12:00:00",
                      "visitReason":"再次拜访"
                    }
                    """.formatted(frontKey, backKey, faceKey)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("上传文件已被使用，请重新上传"));
    }

    private String uploadFile(String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, createPngBytes(280, 180, false));
        String response = mockMvc.perform(multipart("/api/visitor/upload").file(file))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).at("/data/fileKey").asText();
    }

    private String uploadFaceFile(String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, MediaType.IMAGE_PNG_VALUE, createPngBytes(320, 320, true));
        String response = mockMvc.perform(multipart("/api/visitor/upload").file(file).param("scene", "face"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).at("/data/fileKey").asText();
    }

    private byte[] createPngBytes(int width, int height, boolean noisy) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(width * 31L + height);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = noisy ? random.nextInt(0x1000000) : ((x + y) % 2 == 0 ? 0xF3F7FF : 0xD9E7FF);
                image.setRGB(x, y, rgb);
            }
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private byte[] createSmallPngBytes() throws Exception {
        return createPngBytes(48, 48, false);
    }
}
