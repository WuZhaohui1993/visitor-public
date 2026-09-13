package com.visitor.system.visitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.service.impl.HikvisionHttpSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.Map;

/**
 * 海康 FRS 人脸评分校验。
 *
 * <p>这条链路发生在访客上传人脸照片阶段，目标是提前拦截明显不合格的自拍照；
 * 它不是审批通过后的人员/人脸/权限下发链路。
 */
@Service
public class HikvisionFaceScoreService {

    private static final Logger log = LoggerFactory.getLogger(HikvisionFaceScoreService.class);
    private static final String FACE_SCORE_FAILED_CODE = "0x1f913016";

    private final VisitorProperties properties;
    private final HikvisionHttpSupport httpSupport;

    public HikvisionFaceScoreService(VisitorProperties properties, HikvisionHttpSupport httpSupport) {
        this.properties = properties;
        this.httpSupport = httpSupport;
    }

    public void validateFaceImage(byte[] faceImageBytes) {
        // 海康集成或评分能力关闭时，上传链路只做本地图片校验，不额外阻断访客登记。
        if (!properties.getHikvision().isEnabled() || !properties.getHikvision().isFaceScoreEnabled()) {
            return;
        }
        JsonNode root;
        try {
            // 海康要求 facePicBinaryData 为纯 Base64 字符串，传入的是服务端已转码后的 JPG 字节。
            root = httpSupport.postRaw(
                properties.getHikvision().getFaceScorePath(),
                Map.of("facePicBinaryData", Base64.getEncoder().encodeToString(faceImageBytes)),
                "海康人脸评分失败"
            );
        } catch (Exception exception) {
            // 评分接口只是辅助手段，不因为授权、超时等问题拦截上传。
            log.warn("hikvision face score unavailable but continue, reason={}", exception.getMessage());
            return;
        }

        // 成功响应里如果 checkResult=false，说明图片质量明确不合格，应当拦截上传并提示用户重拍。
        if (isSuccess(root)) {
            if (hasExplicitCheckFailure(root)) {
                throw new BusinessException(buildRejectedMessage(root));
            }
            return;
        }

        // 只有海康明确返回质量不合格错误码或 checkResult=false 才拦截；授权/网关等异常在前面已放行。
        if (FACE_SCORE_FAILED_CODE.equalsIgnoreCase(root.path("code").asText()) || hasExplicitCheckFailure(root)) {
            throw new BusinessException(buildRejectedMessage(root));
        }

        log.warn("hikvision face score failed but ignored, code={}, msg={}",
            root.path("code").asText(),
            extractMessage(root));
    }

    private boolean isSuccess(JsonNode root) {
        if (root == null || root.isNull()) {
            return false;
        }
        String code = root.path("code").asText();
        return !StringUtils.hasText(code) || "0".equals(code) || "200".equals(code);
    }

    private boolean hasExplicitCheckFailure(JsonNode root) {
        JsonNode checkResult = root.path("data").path("checkResult");
        return !checkResult.isMissingNode() && !checkResult.isNull() && !checkResult.asBoolean(true);
    }

    private String buildRejectedMessage(JsonNode root) {
        // 用户提示优先映射成本地中文业务文案，避免把海康原始错误或内部码直接暴露给访客。
        String statusCode = root.path("data").path("statusCode").asText();
        String detail = firstNonBlank(
            knownCodeMessage(statusCode),
            knownCodeMessage(root.path("code").asText()),
            root.path("data").path("statusMessage").asText(),
            extractMessage(root)
        );
        if (!StringUtils.hasText(detail)) {
            detail = "请上传光线充足、无遮挡的清晰正脸照";
        }
        return "人脸照片质量不符合海康要求：" + detail;
    }

    private String extractMessage(JsonNode root) {
        return firstNonBlank(
            knownCodeMessage(root.path("code").asText()),
            root.path("msg").asText(),
            root.path("message").asText()
        );
    }

    private String knownCodeMessage(String code) {
        return switch (code) {
            case "0x1f913016" -> "未检测到合格人脸，请上传光线充足、无遮挡的清晰正脸照";
            case "0x1f902302" -> "评分算法未授权";
            case "0x1f902066" -> "人脸评分参数错误，请检查上传的图片";
            case "0x1f902197" -> "图片数据解析失败，请重新上传图片";
            case "0x1f910010" -> "评分算法服务不可用，请稍后再试";
            case "0x1f900000" -> "评分服务发生未知错误，请稍后再试";
            case "0x1f91300b" -> "未上传人脸图片，请重新选择图片";
            case "0x1f934000" -> "评分请求缺少必填参数，请检查上传内容";
            case "0x1f934001" -> "评分请求参数不合法，请检查上传内容";
            case "0x1f93003e" -> "评分服务内部异常，请稍后再试";
            case "0x1f902300" -> "图片格式不符合要求或未检测到人脸";
            case "0x1f902301" -> "人脸检测超时";
            case "0x1f902303" -> "图片两眼间距过小";
            case "0x1f902304" -> "图片彩色置信度过低";
            case "0x1f902305" -> "图片人脸角度过大";
            case "0x1f902306" -> "图片清晰度过低";
            case "0x1f902307" -> "图片过曝或过暗";
            case "0x1f902308" -> "图片遮挡严重";
            case "0x1f902309" -> "图片分数过低";
            default -> null;
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
