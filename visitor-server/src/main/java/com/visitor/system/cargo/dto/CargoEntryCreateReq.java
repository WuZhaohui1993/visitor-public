package com.visitor.system.cargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CargoEntryCreateReq {

    @NotBlank(message = "供货单位不能为空")
    @Size(max = 100, message = "供货单位不能超过100个字")
    private String supplierUnit;

    @NotBlank(message = "收货单位不能为空")
    @Size(max = 100, message = "收货单位不能超过100个字")
    private String receivingUnit;

    @NotBlank(message = "货物名称不能为空")
    @Size(max = 100, message = "货物名称不能超过100个字")
    private String goodsName;

    @NotBlank(message = "数量不能为空")
    @Size(max = 50, message = "数量不能超过50个字")
    private String quantity;

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50, message = "收货人姓名不能超过50个字")
    private String receiverName;

    @NotBlank(message = "存放位置不能为空")
    @Size(max = 100, message = "存放位置不能超过100个字")
    private String storageLocation;

    @NotNull(message = "入场时间不能为空")
    private LocalDateTime entryTime;
}
