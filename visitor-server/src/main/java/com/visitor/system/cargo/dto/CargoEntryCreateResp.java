package com.visitor.system.cargo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CargoEntryCreateResp {
    private final Long id;
    private final String bizId;
    private final LocalDateTime entryTime;
    private final LocalDateTime createTime;
}
