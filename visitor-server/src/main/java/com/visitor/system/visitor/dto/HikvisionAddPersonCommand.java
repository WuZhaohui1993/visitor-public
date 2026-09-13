package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HikvisionAddPersonCommand {
    private final String personCode;
    private final String personName;
    private final String phone;
    private final String idCardNo;
    private final String orgIndexCode;
}
