package com.visitor.system.visitor.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class HikvisionAccessResourceInfo {
    private final String resourceIndexCode;
    private final List<Integer> channelNos;
}
