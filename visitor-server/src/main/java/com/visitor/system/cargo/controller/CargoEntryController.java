package com.visitor.system.cargo.controller;

import com.visitor.system.cargo.dto.CargoEntryCreateReq;
import com.visitor.system.cargo.dto.CargoEntryCreateResp;
import com.visitor.system.cargo.service.CargoEntryService;
import com.visitor.system.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cargo/entries")
public class CargoEntryController {

    private final CargoEntryService cargoEntryService;

    public CargoEntryController(CargoEntryService cargoEntryService) {
        this.cargoEntryService = cargoEntryService;
    }

    @PostMapping
    public ApiResponse<CargoEntryCreateResp> create(@Valid @RequestBody CargoEntryCreateReq request) {
        return ApiResponse.success(cargoEntryService.create(request));
    }
}
