package com.visitor.system.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminLoginReq {
    @NotBlank(message = "请输入管理员账号")
    private String username;

    @NotBlank(message = "请输入管理员密码")
    private String password;

    private String captchaId;

    private String captchaCode;
}
