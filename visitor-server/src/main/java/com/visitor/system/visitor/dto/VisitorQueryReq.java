package com.visitor.system.visitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VisitorQueryReq {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请输入身份证后6位")
    @Pattern(regexp = "^[0-9Xx]{6}$", message = "身份证后6位格式不正确")
    private String idCardSuffix;
}
