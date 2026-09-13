package com.visitor.system.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminPasswordResetReq {
    @NotBlank(message = "请输入新密码")
    private String newPassword;
}
