package com.visitor.system.cargo;

import com.visitor.system.cargo.domain.CargoEntryRecord;
import com.visitor.system.cargo.repository.CargoEntryRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CargoEntryControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    CargoEntryRecordRepository repository;

    @BeforeEach
    void clearRecords() {
        repository.deleteAll();
    }

    @Test
    void shouldCreateCargoEntryWithoutAuthentication() throws Exception {
        LocalDateTime entryTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        mockMvc.perform(post("/api/cargo/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "supplierUnit": "  陕西供应有限公司  ",
                      "receivingUnit": "示例仓储中心",
                      "goodsName": "设备配件",
                      "quantity": "20 箱",
                      "receiverName": "张三",
                      "storageLocation": "一号库 A 区",
                      "entryTime": "%s"
                    }
                    """.formatted(entryTime)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.bizId").isNotEmpty())
            .andExpect(jsonPath("$.data.entryTime").value(entryTime.toString()));

        CargoEntryRecord saved = repository.findAll().get(0);
        assertThat(saved.getSupplierUnit()).isEqualTo("陕西供应有限公司");
        assertThat(saved.getReceivingUnit()).isEqualTo("示例仓储中心");
        assertThat(saved.getGoodsName()).isEqualTo("设备配件");
        assertThat(saved.getQuantity()).isEqualTo("20 箱");
        assertThat(saved.getReceiverName()).isEqualTo("张三");
        assertThat(saved.getStorageLocation()).isEqualTo("一号库 A 区");
        assertThat(saved.getEntryTime()).isEqualTo(entryTime);
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    void shouldRejectIncompleteCargoEntry() throws Exception {
        mockMvc.perform(post("/api/cargo/entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "supplierUnit": "",
                      "receivingUnit": "示例仓储中心"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("供货单位不能为空")))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("入场时间不能为空")));

        assertThat(repository.count()).isZero();
    }
}
