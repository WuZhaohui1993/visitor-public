package com.visitor.system.visitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.visitor.repository.HikvisionAccessTargetRepository;
import com.visitor.system.visitor.service.HikvisionAccessResourceService;
import com.visitor.system.visitor.service.impl.HikvisionHttpSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
    "visitor.hikvision.enabled=true",
    "visitor.hikvision.access.enabled=true",
    "visitor.hikvision.access.resource-index-codes=",
    "visitor.hikvision.access.auto-discover-resources=true",
    "visitor.hikvision.access.resource-query-path=/artemis/api/irds/v2/deviceResource/resources",
    "visitor.hikvision.access.resource-query-type=door",
    "visitor.hikvision.access.resource-query-page-size=2",
    "visitor.hikvision.access.refresh-resources-on-startup=false"
})
class HikvisionAccessResourceServiceTests {

    @Autowired
    private HikvisionAccessResourceService resourceService;

    @Autowired
    private HikvisionAccessTargetRepository targetRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HikvisionHttpSupport hikvisionHttpSupport;

    @Test
    void shouldRefreshAndCacheAllAccessDevicesByPaging() throws Exception {
        when(hikvisionHttpSupport.postRaw(anyString(), any(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {
                  "code":"0",
                  "data":{
                    "total":3,
                    "list":[
                      {"indexCode":"door-1","parentIndexCode":"device-1","name":"一号门","resourceType":"door","channelNo":"1"},
                      {"indexCode":"door-2","parentIndexCode":"device-1","name":"二号门","resourceType":"door","channelNo":"2"}
                    ]
                  }
                }
                """))
            .thenReturn(objectMapper.readTree("""
                {
                  "code":"0",
                  "data":{
                    "total":3,
                    "list":[
                      {"indexCode":"door-3","parentIndexCode":"device-2","name":"三号门","resourceType":"door","channelNo":"1"}
                    ]
                  }
                }
                """));

        assertThat(resourceService.refreshAccessDevices())
            .extracting(info -> info.getResourceIndexCode() + ":" + info.getChannelNos())
            .containsExactly("device-1:[1, 2]", "device-2:[1]");
        assertThat(targetRepository.findAllByOrderByResourceIndexCodeAscChannelNoAsc())
            .extracting(target -> target.getResourceIndexCode() + ":" + target.getChannelNo())
            .containsExactly("device-1:1", "device-1:2", "device-2:1");
        assertThat(resourceService.resolveAccessResourceInfos())
            .extracting(info -> info.getResourceIndexCode() + ":" + info.getChannelNos())
            .containsExactly("device-1:[1, 2]", "device-2:[1]");
    }
}
