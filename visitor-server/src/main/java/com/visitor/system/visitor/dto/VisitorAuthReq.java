package com.visitor.system.visitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VisitorAuthReq {

    @NotBlank(message = "authCode不能为空")
    private String authCode;
}
