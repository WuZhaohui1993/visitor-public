package com.visitor.system.visitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.dto.HikvisionAccessResourceInfo;
import com.visitor.system.visitor.dto.HikvisionAddFaceCommand;
import com.visitor.system.visitor.dto.HikvisionGrantAccessCommand;
import com.visitor.system.visitor.dto.HikvisionRevokeAccessCommand;
import com.visitor.system.visitor.service.impl.HikvisionHttpSupport;
import com.visitor.system.visitor.service.impl.HttpHikvisionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpHikvisionClientTests {

    @Mock
    private HikvisionHttpSupport hikvisionHttpSupport;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRetryPersonDetailQueryWithAppIdWhenDefaultQueryCannotFindPerson() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setUserId("admin");
        properties.getHikvision().setPersonAppId("defaultPersonal");
        properties.getHikvision().setQueryPersonPath("/artemis/api/pmas/v1/person/detailV1");
        HttpHikvisionClient client = new HttpHikvisionClient(hikvisionHttpSupport, properties);
        when(hikvisionHttpSupport.postRaw(anyString(), anyMap(), anyMap(), anyBoolean(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0x15452501a","msg":"人员在平台上不存在","data":null}
                """))
            .thenReturn(objectMapper.readTree("""
                {"code":"0","msg":"success","data":{"personId":"person-id-9","personIndexCode":"person-code-9"}}
                """));

        String personId = client.findPersonId("person-code-9");

        assertThat(personId).isEqualTo("person-id-9");
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hikvisionHttpSupport, org.mockito.Mockito.times(2)).postRaw(
            org.mockito.Mockito.eq("/artemis/api/pmas/v1/person/detailV1"),
            bodyCaptor.capture(),
            anyMap(),
            org.mockito.Mockito.eq(false),
            org.mockito.Mockito.eq("海康查询人员失败")
        );
        assertThat(bodyCaptor.getAllValues().get(0)).containsEntry("personIndexCode", "person-code-9");
        assertThat(bodyCaptor.getAllValues().get(1)).containsEntry("personIndexCode", "person-code-9");
        assertThat(bodyCaptor.getAllValues().get(1)).containsEntry("appId", "defaultPersonal");
    }

    @Test
    void shouldAddFaceViaPmasPersonFaceApi() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setUserId("admin");
        properties.getHikvision().setAddFacePath("/artemis/api/pmas/v1/personFace");
        HttpHikvisionClient client = new HttpHikvisionClient(hikvisionHttpSupport, properties);
        when(hikvisionHttpSupport.post(anyString(), anyMap(), anyMap(), anyBoolean(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0","msg":"success","data":null}
                """));

        client.addFace(HikvisionAddFaceCommand.builder()
            .personId("person-0")
            .faceImageBytes(new byte[]{1, 2, 3})
            .build());

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hikvisionHttpSupport).post(
            org.mockito.Mockito.eq("/artemis/api/pmas/v1/personFace"),
            bodyCaptor.capture(),
            headerCaptor.capture(),
            org.mockito.Mockito.eq(false),
            org.mockito.Mockito.eq("海康新增访客人脸失败")
        );
        assertThat(bodyCaptor.getValue()).containsEntry("chgFlag", true);
        assertThat(bodyCaptor.getValue()).containsEntry("personId", "person-0");
        assertThat(bodyCaptor.getValue()).containsEntry("faceData", "AQID");
        assertThat(headerCaptor.getValue()).containsEntry("userId", "admin");
    }

    @Test
    void shouldDeletePersonViaPmasBatchDeleteApi() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setUserId("admin");
        properties.getHikvision().setDisablePersonPath("/artemis/api/pmas/v1/person/batch/delete");
        HttpHikvisionClient client = new HttpHikvisionClient(hikvisionHttpSupport, properties);
        when(hikvisionHttpSupport.post(anyString(), anyMap(), anyMap(), anyBoolean(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0","msg":"success","data":{"successes":[{"indexCode":"person-1","name":"测试"}],"failures":[]}}
                """));

        client.disablePerson("person-1");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hikvisionHttpSupport).post(
            org.mockito.Mockito.eq("/artemis/api/pmas/v1/person/batch/delete"),
            bodyCaptor.capture(),
            headerCaptor.capture(),
            org.mockito.Mockito.eq(false),
            org.mockito.Mockito.eq("海康禁用人员失败")
        );
        assertThat(bodyCaptor.getValue()).containsEntry("personIndexCodes", List.of("person-1"));
        assertThat(headerCaptor.getValue()).containsEntry("userId", "admin");
    }

    @Test
    void shouldEnablePersonViaConfiguredUpdateApi() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().setEnablePersonPath("/artemis/api/resource/v1/person/single/update");
        HttpHikvisionClient client = new HttpHikvisionClient(hikvisionHttpSupport, properties);
        when(hikvisionHttpSupport.post(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0","msg":"success","data":null}
                """));

        client.enablePerson("person-2");

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hikvisionHttpSupport).post(
            org.mockito.Mockito.eq("/artemis/api/resource/v1/person/single/update"),
            bodyCaptor.capture(),
            org.mockito.Mockito.eq("海康启用人员失败")
        );
        assertThat(bodyCaptor.getValue()).containsEntry("personIndexCode", "person-2");
        assertThat(bodyCaptor.getValue()).containsEntry("personStatus", "1");
    }

    @Test
    void shouldGrantAccessViaAuthConfigAddApi() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().getAccess().setEnabled(true);
        properties.getHikvision().getAccess().setGrantPath("/artemis/api/acps/v1/auth_config/add");
        HttpHikvisionClient client = new HttpHikvisionClient(hikvisionHttpSupport, properties);
        when(hikvisionHttpSupport.post(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0","msg":"success","data":{"taskId":"task-1"}}
                """));

        client.grantAccess(HikvisionGrantAccessCommand.builder()
            .personId("person-3")
            .beginTime(LocalDateTime.of(2026, 4, 20, 10, 0))
            .endTime(LocalDateTime.of(2026, 4, 20, 18, 0))
            .resourceType("acsDevice")
            .resourceInfos(List.of(HikvisionAccessResourceInfo.builder()
                .resourceIndexCode("door-1")
                .channelNos(List.of(1))
                .build()))
            .build());

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hikvisionHttpSupport).post(
            org.mockito.Mockito.eq("/artemis/api/acps/v1/auth_config/add"),
            bodyCaptor.capture(),
            org.mockito.Mockito.eq("海康门禁权限下发失败")
        );
        List<?> personDatas = (List<?>) bodyCaptor.getValue().get("personDatas");
        List<?> resourceInfos = (List<?>) bodyCaptor.getValue().get("resourceInfos");
        Map<?, ?> firstPersonData = (Map<?, ?>) personDatas.get(0);
        Map<?, ?> firstResourceInfo = (Map<?, ?>) resourceInfos.get(0);
        assertThat(firstPersonData.get("personDataType")).isEqualTo("person");
        assertThat(firstPersonData.get("indexCodes")).isEqualTo(List.of("person-3"));
        assertThat(firstResourceInfo.get("resourceIndexCode")).isEqualTo("door-1");
        assertThat(firstResourceInfo.get("channelNos")).isEqualTo(List.of(1));
        assertThat(bodyCaptor.getValue()).containsKeys("startTime", "endTime");
    }

    @Test
    void shouldRevokeAccessViaAuthConfigDeleteApi() throws Exception {
        VisitorProperties properties = new VisitorProperties();
        properties.getHikvision().getAccess().setEnabled(true);
        properties.getHikvision().getAccess().setRevokePath("/artemis/api/acps/v1/auth_config/delete");
        HttpHikvisionClient client = new HttpHikvisionClient(hikvisionHttpSupport, properties);
        when(hikvisionHttpSupport.post(anyString(), anyMap(), anyString()))
            .thenReturn(objectMapper.readTree("""
                {"code":"0","msg":"success","data":{"taskId":"task-2"}}
                """));

        client.revokeAccess(HikvisionRevokeAccessCommand.builder()
            .personId("person-4")
            .resourceType("acsDevice")
            .resourceInfos(List.of(HikvisionAccessResourceInfo.builder()
                .resourceIndexCode("door-2")
                .channelNos(List.of(1, 2))
                .build()))
            .build());

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hikvisionHttpSupport).post(
            org.mockito.Mockito.eq("/artemis/api/acps/v1/auth_config/delete"),
            bodyCaptor.capture(),
            org.mockito.Mockito.eq("海康门禁权限回收失败")
        );
        List<?> personDatas = (List<?>) bodyCaptor.getValue().get("personDatas");
        List<?> resourceInfos = (List<?>) bodyCaptor.getValue().get("resourceInfos");
        Map<?, ?> firstPersonData = (Map<?, ?>) personDatas.get(0);
        Map<?, ?> firstResourceInfo = (Map<?, ?>) resourceInfos.get(0);
        assertThat(firstPersonData.get("personDataType")).isEqualTo("person");
        assertThat(firstPersonData.get("indexCodes")).isEqualTo(List.of("person-4"));
        assertThat(firstResourceInfo.get("resourceIndexCode")).isEqualTo("door-2");
        assertThat(firstResourceInfo.get("channelNos")).isEqualTo(List.of("1", "2"));
    }
}
