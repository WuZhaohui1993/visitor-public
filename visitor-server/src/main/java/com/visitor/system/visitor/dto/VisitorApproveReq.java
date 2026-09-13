package com.visitor.system.visitor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VisitorApproveReq {

    @NotBlank(message = "bizId不能为空")
    private String bizId;

    @NotBlank(message = "token不能为空")
    private String token;

    @Min(value = 1, message = "审批状态不合法")
    @Max(value = 2, message = "审批状态不合法")
    private Integer status;

    @Size(max = 200, message = "审批备注不能超过200字")
    private String remark;
}
