package com.visitor.system.visitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DingTalkUserDto {
    private final String userId;
    private final String name;
    private final String deptName;
}
