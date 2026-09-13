package com.visitor.system.visitor.controller;

import com.visitor.system.common.ApiResponse;
import com.visitor.system.visitor.dto.AuthUserResp;
import com.visitor.system.visitor.dto.DingTalkUserDto;
import com.visitor.system.visitor.dto.UploadScene;
import com.visitor.system.visitor.dto.UploadResp;
import com.visitor.system.visitor.dto.VisitorApproveDetailResp;
import com.visitor.system.visitor.dto.VisitorApproveReq;
import com.visitor.system.visitor.dto.VisitorAuthReq;
import com.visitor.system.visitor.dto.VisitorQueryReq;
import com.visitor.system.visitor.dto.VisitorRegisterReq;
import com.visitor.system.visitor.dto.VisitorRegisterResp;
import com.visitor.system.visitor.dto.VisitorResultResp;
import com.visitor.system.visitor.dto.HikvisionAccessTargetResp;
import com.visitor.system.visitor.service.HikvisionAccessResourceService;
import com.visitor.system.visitor.service.UploadFileService;
import com.visitor.system.visitor.service.VisitorSecurityTokenService;
import com.visitor.system.visitor.service.VisitorRecordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/visitor")
public class VisitorController {

    private final UploadFileService uploadFileService;
    private final VisitorRecordService visitorRecordService;
    private final HikvisionAccessResourceService hikvisionAccessResourceService;
    private final VisitorSecurityTokenService visitorSecurityTokenService;
    private final boolean dingTalkMockMode;

    public VisitorController(UploadFileService uploadFileService,
                             VisitorRecordService visitorRecordService,
                             HikvisionAccessResourceService hikvisionAccessResourceService,
                             VisitorSecurityTokenService visitorSecurityTokenService,
                             @Value("${visitor.dingtalk.mock-mode:true}") boolean dingTalkMockMode) {
        this.uploadFileService = uploadFileService;
        this.visitorRecordService = visitorRecordService;
        this.hikvisionAccessResourceService = hikvisionAccessResourceService;
        this.visitorSecurityTokenService = visitorSecurityTokenService;
        this.dingTalkMockMode = dingTalkMockMode;
    }

    @PostMapping("/upload")
    public ApiResponse<UploadResp> upload(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "scene", required = false) String scene) {
        return ApiResponse.success(uploadFileService.upload(file, UploadScene.from(scene)));
    }

    @GetMapping("/search")
    public ApiResponse<List<DingTalkUserDto>> search(@RequestParam("q") String keyword) {
        return ApiResponse.success(visitorRecordService.searchUsers(keyword));
    }

    @PostMapping("/register")
    public ApiResponse<VisitorRegisterResp> register(@Valid @org.springframework.web.bind.annotation.RequestBody VisitorRegisterReq request) {
        return ApiResponse.success(visitorRecordService.register(request));
    }

    @GetMapping("/result/{bizId}")
    public ApiResponse<VisitorResultResp> result(@PathVariable String bizId,
                                                 @RequestParam("accessToken") String accessToken) {
        return ApiResponse.success(visitorRecordService.getResult(bizId, accessToken));
    }

    @PostMapping("/query")
    public ApiResponse<List<VisitorResultResp>> query(@Valid @org.springframework.web.bind.annotation.RequestBody VisitorQueryReq request) {
        return ApiResponse.success(visitorRecordService.queryRecords(request));
    }

    @GetMapping("/hikvision/access-targets")
    public ApiResponse<List<HikvisionAccessTargetResp>> accessTargets() {
        // 给运维/管理员查看当前缓存的海康门禁目标，便于确认权限会下发到哪些设备和通道。
        return ApiResponse.success(hikvisionAccessResourceService.listAccessTargets());
    }

    @PostMapping("/hikvision/access-targets/refresh")
    public ApiResponse<Void> refreshAccessTargets() {
        // 手动刷新海康门禁目标缓存，用于现场新增门点后不等定时任务立即生效。
        hikvisionAccessResourceService.refreshAccessDevices();
        return ApiResponse.successMessage("海康权限目标缓存刷新成功");
    }

    @PostMapping("/auth")
    public ApiResponse<AuthUserResp> auth(@Valid @org.springframework.web.bind.annotation.RequestBody VisitorAuthReq request) {
        DingTalkUserDto user = visitorRecordService.getUserByAuthCode(request.getAuthCode());
        return ApiResponse.success(AuthUserResp.builder()
            .userId(user.getUserId())
            .accessToken(visitorSecurityTokenService.generateUserAccessToken(user.getUserId()))
            .mock(dingTalkMockMode)
            .build());
    }

    @GetMapping("/approve-detail/{bizId}")
    public ApiResponse<VisitorApproveDetailResp> approveDetail(@PathVariable String bizId,
                                                               Authentication authentication) {
        return ApiResponse.success(visitorRecordService.getApproveDetail(bizId, currentUserId(authentication)));
    }

    @PostMapping("/approve")
    public ApiResponse<VisitorResultResp> approve(@Valid @org.springframework.web.bind.annotation.RequestBody VisitorApproveReq request,
                                                  Authentication authentication) {
        return ApiResponse.success(visitorRecordService.approve(currentUserId(authentication), request));
    }

    @GetMapping(value = "/files/{*objectKey}")
    public ResponseEntity<InputStreamResource> file(@PathVariable String objectKey,
                                                    @RequestParam("accessToken") String accessToken) {
        MediaType mediaType = objectKey.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
            .contentType(mediaType)
            .body(new InputStreamResource(visitorRecordService.openAuthorizedFile(objectKey, accessToken)));
    }

    private String currentUserId(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
