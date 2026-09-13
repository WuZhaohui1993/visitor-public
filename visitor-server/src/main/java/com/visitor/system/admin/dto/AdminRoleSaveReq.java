package com.visitor.system.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class AdminRoleSaveReq {
    @NotBlank(message = "请输入角色编码")
    private String code;
    @NotBlank(message = "请输入角色名称")
    private String name;
    private boolean enabled = true;
    @NotEmpty(message = "请至少分配一个权限")
    private Set<String> permissionCodes;
}
