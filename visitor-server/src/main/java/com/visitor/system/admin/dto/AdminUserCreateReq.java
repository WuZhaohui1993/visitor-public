package com.visitor.system.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class AdminUserCreateReq {
    @NotBlank(message = "请输入管理员账号")
    private String username;
    @NotBlank(message = "请输入管理员姓名")
    private String displayName;
    @NotBlank(message = "请输入初始密码")
    private String password;
    @NotEmpty(message = "请至少分配一个角色")
    private Set<String> roleCodes;
}
