package com.visitor.system.visitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class VisitorRegisterReq {

    @NotBlank(message = "访客姓名不能为空")
    @Size(max = 20, message = "访客姓名不能超过20个字")
    private String visitorName;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^[0-9]{17}[0-9Xx]$", message = "身份证号格式不正确")
    private String idCardNo;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请上传身份证正面")
    private String idCardFrontKey;

    @NotBlank(message = "请上传身份证背面")
    private String idCardBackKey;

    @NotBlank(message = "请上传人脸自拍照")
    private String facePhotoKey;

    @NotBlank(message = "被访人ID不能为空")
    private String visitedUserId;

    @NotBlank(message = "被访人姓名不能为空")
    private String visitedUserName;

    @NotBlank(message = "被访人部门不能为空")
    private String visitedDeptName;

    @NotNull(message = "预计入场时间不能为空")
    private LocalDateTime plannedEntryTime;

    @NotNull(message = "预计出场时间不能为空")
    private LocalDateTime plannedExitTime;

    @Size(max = 200, message = "访问事由不能超过200字")
    private String visitReason;
}
