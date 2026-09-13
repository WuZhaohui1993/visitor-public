package com.visitor.system.visitor.service;

import com.visitor.system.common.BusinessException;
import com.visitor.system.common.ForbiddenException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.domain.VisitorRecord;
import com.visitor.system.visitor.domain.VisitorRecordSequence;
import com.visitor.system.visitor.dto.DingTalkUserDto;
import com.visitor.system.visitor.dto.DingTalkOaMessage;
import com.visitor.system.visitor.dto.VisitorApproveDetailResp;
import com.visitor.system.visitor.dto.VisitorApproveReq;
import com.visitor.system.visitor.dto.VisitorQueryReq;
import com.visitor.system.visitor.dto.VisitorRegisterReq;
import com.visitor.system.visitor.dto.VisitorRegisterResp;
import com.visitor.system.visitor.dto.VisitorResultResp;
import com.visitor.system.visitor.repository.VisitorRecordRepository;
import com.visitor.system.visitor.repository.VisitorRecordSequenceRepository;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class VisitorRecordService {

    private final VisitorRecordRepository visitorRecordRepository;
    private final UploadFileService uploadFileService;
    private final ObjectStorageService objectStorageService;
    private final CryptoService cryptoService;
    private final ApproveTokenService approveTokenService;
    private final DingTalkClient dingTalkClient;
    private final DingTalkNotifyService dingTalkNotifyService;
    private final HikvisionSyncService hikvisionSyncService;
    private final VisitorProperties properties;
    private final VisitorSecurityTokenService visitorSecurityTokenService;
    private final VisitorRecordSequenceRepository visitorRecordSequenceRepository;

    public VisitorRecordService(VisitorRecordRepository visitorRecordRepository,
                                UploadFileService uploadFileService,
                                ObjectStorageService objectStorageService,
                                CryptoService cryptoService,
                                ApproveTokenService approveTokenService,
                                DingTalkClient dingTalkClient,
                                DingTalkNotifyService dingTalkNotifyService,
                                HikvisionSyncService hikvisionSyncService,
                                VisitorProperties properties,
                                VisitorSecurityTokenService visitorSecurityTokenService,
                                VisitorRecordSequenceRepository visitorRecordSequenceRepository) {
        this.visitorRecordRepository = visitorRecordRepository;
        this.uploadFileService = uploadFileService;
        this.objectStorageService = objectStorageService;
        this.cryptoService = cryptoService;
        this.approveTokenService = approveTokenService;
        this.dingTalkClient = dingTalkClient;
        this.dingTalkNotifyService = dingTalkNotifyService;
        this.hikvisionSyncService = hikvisionSyncService;
        this.properties = properties;
        this.visitorSecurityTokenService = visitorSecurityTokenService;
        this.visitorRecordSequenceRepository = visitorRecordSequenceRepository;
    }

    public List<DingTalkUserDto> searchUsers(String keyword) {
        if (StringUtils.length(keyword) < 2) {
            throw new BusinessException("搜索关键字至少输入2个字");
        }
        return dingTalkClient.searchUsers(keyword);
    }

    @Transactional
    public VisitorRegisterResp register(VisitorRegisterReq request) {
        validateVisitWindow(request.getPlannedEntryTime(), request.getPlannedExitTime());
        String bizId = UUID.randomUUID().toString();
        String frontObjectKey = uploadFileService.linkToBiz(request.getIdCardFrontKey(), bizId, "id-card-front");
        String backObjectKey = uploadFileService.linkToBiz(request.getIdCardBackKey(), bizId, "id-card-back");
        String faceObjectKey = uploadFileService.linkToBiz(request.getFacePhotoKey(), bizId, "face-photo");

        VisitorRecord record = new VisitorRecord();
        record.setBizId(bizId);
        record.setRecordNo(generateRecordNo());
        record.setVisitorName(request.getVisitorName().trim());
        record.setIdCardNo(cryptoService.encrypt(request.getIdCardNo().toUpperCase()));
        record.setIdCardSuffix(extractIdCardSuffix(request.getIdCardNo()));
        record.setPhone(request.getPhone().trim());
        record.setIdCardFront(frontObjectKey);
        record.setIdCardBack(backObjectKey);
        record.setFacePhoto(faceObjectKey);
        record.setVisitedUserId(request.getVisitedUserId());
        record.setVisitedUserName(request.getVisitedUserName());
        record.setVisitedDeptName(request.getVisitedDeptName());
        record.setVisitReason(StringUtils.trimToEmpty(request.getVisitReason()));
        record.setPlannedEntryTime(request.getPlannedEntryTime());
        record.setPlannedExitTime(request.getPlannedExitTime());
        record.setStatus(0);
        record.setExpireTime(request.getPlannedExitTime());
        record.setTokenExpireTime(LocalDateTime.now().plusHours(properties.getSecurity().getTokenHours()));
        String token = approveTokenService.generateToken(bizId, request.getVisitedUserId());
        record.setApproveToken(token);
        visitorRecordRepository.save(record);

        sendApproveNotification(record);
        String resultAccessToken = visitorSecurityTokenService.generateResultAccessToken(bizId);

        return VisitorRegisterResp.builder()
            .bizId(bizId)
            .resultAccessToken(resultAccessToken)
            .resultUrl(buildResultUrl(bizId, resultAccessToken))
            .build();
    }

    public VisitorResultResp getResult(String bizId, String accessToken) {
        visitorSecurityTokenService.validateResultAccess(bizId, accessToken);
        VisitorRecord record = mustGetByBizId(bizId);
        return toResult(record);
    }

    public VisitorApproveDetailResp getApproveDetail(String bizId, String currentUserId) {
        VisitorRecord record = mustGetByBizId(bizId);
        ensureApproveOwner(record, currentUserId);
        return VisitorApproveDetailResp.builder()
            .bizId(record.getBizId())
            .recordNo(record.getRecordNo())
            .visitorName(record.getVisitorName())
            .idCardMasked(maskIdCard(cryptoService.decrypt(record.getIdCardNo())))
            .phoneMasked(maskPhone(record.getPhone()))
            .idCardFrontUrl(buildRecordFilePreviewUrl(record.getBizId(), record.getIdCardFront()))
            .idCardBackUrl(buildRecordFilePreviewUrl(record.getBizId(), record.getIdCardBack()))
            .facePhotoUrl(buildRecordFilePreviewUrl(record.getBizId(), record.getFacePhoto()))
            .visitedUserName(record.getVisitedUserName())
            .visitedDeptName(record.getVisitedDeptName())
            .visitReason(record.getVisitReason())
            .plannedEntryTime(record.getPlannedEntryTime())
            .plannedExitTime(record.getPlannedExitTime())
            .status(record.getStatus())
            .approveRemark(record.getApproveRemark())
            .createTime(record.getCreateTime())
            .approveTime(record.getApproveTime())
            .expired(isExpired(record))
            .build();
    }

    public List<VisitorResultResp> queryRecords(VisitorQueryReq request) {
        String idCardSuffix = request.getIdCardSuffix().trim().toUpperCase();
        List<VisitorResultResp> results = visitorRecordRepository
            .findTop10ByPhoneAndIdCardSuffixOrderByCreateTimeDesc(request.getPhone().trim(), idCardSuffix)
            .stream()
            .sorted(Comparator.comparing(VisitorRecord::getCreateTime).reversed())
            .map(this::toResult)
            .toList();
        if (results.isEmpty()) {
            throw new BusinessException("未查询到申请记录，请检查手机号和身份证后6位");
        }
        return results;
    }

    @Transactional
    public VisitorResultResp approve(String currentUserId, VisitorApproveReq request) {
        VisitorRecord record = mustGetByBizId(request.getBizId());
        if (record.getStatus() != 0) {
            throw new BusinessException("该记录已审批，请勿重复操作");
        }
        if (isExpired(record)) {
            throw new BusinessException("该审批记录已过期");
        }
        Claims claims = approveTokenService.parse(request.getToken());
        String tokenBizId = claims.getSubject();
        String visitedUserId = claims.get("visitedUserId", String.class);
        if (!record.getBizId().equals(tokenBizId) || !record.getApproveToken().equals(request.getToken())) {
            throw new BusinessException("审批 token 与记录不匹配");
        }
        if (!record.getVisitedUserId().equals(currentUserId) || !record.getVisitedUserId().equals(visitedUserId)) {
            throw new ForbiddenException("当前用户无权审批该记录");
        }
        if (request.getStatus() == 2 && StringUtils.isBlank(request.getRemark())) {
            throw new BusinessException("拒绝时必须填写原因");
        }
        record.setStatus(request.getStatus());
        record.setApproveRemark(StringUtils.trimToNull(request.getRemark()));
        record.setApproveTime(LocalDateTime.now());
        record.setApproveToken(record.getApproveToken() + "#used");
        if (request.getStatus() == 1) {
            record.setHikPersonCode(normalizeHikPersonCode(record.getBizId()));
            record.setHikPersonId(null);
            record.setHikFaceIndexCode(null);
            record.setHikRetryCount(0);
            record.setHikLastRetryTime(null);
            record.setHikDisabledTime(null);
            record.setHikSyncError(null);
            record.setHikSyncStatus(hikvisionSyncService.initialApproveStatus());
            record.setHikSyncTime(hikvisionSyncService.initialSyncTime());
            record.setHikAccessRetryCount(0);
            record.setHikAccessLastRetryTime(null);
            record.setHikAccessDisabledTime(null);
            record.setHikAccessError(null);
            record.setHikAccessStatus(hikvisionSyncService.isAccessSyncEnabled()
                ? com.visitor.system.visitor.domain.HikSyncStatus.PENDING
                : com.visitor.system.visitor.domain.HikSyncStatus.SUCCESS);
            record.setHikAccessSyncTime(hikvisionSyncService.isAccessSyncEnabled() ? null : hikvisionSyncService.initialSyncTime());
        } else {
            record.setHikSyncStatus(null);
            record.setHikSyncTime(null);
            record.setHikSyncError(null);
            record.setHikPersonCode(null);
            record.setHikPersonId(null);
            record.setHikFaceIndexCode(null);
            record.setHikRetryCount(0);
            record.setHikLastRetryTime(null);
            record.setHikDisabledTime(null);
            record.setHikAccessStatus(null);
            record.setHikAccessSyncTime(null);
            record.setHikAccessError(null);
            record.setHikAccessRetryCount(0);
            record.setHikAccessLastRetryTime(null);
            record.setHikAccessDisabledTime(null);
        }
        visitorRecordRepository.save(record);
        if (request.getStatus() == 1 && hikvisionSyncService.isEnabled()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    hikvisionSyncService.syncApprovedRecordAsync(record.getId());
                }
            });
        }
        return toResult(record);
    }

    public DingTalkUserDto getUserByAuthCode(String authCode) {
        return dingTalkClient.getUserByAuthCode(authCode);
    }

    public InputStream openAuthorizedFile(String objectKey, String accessToken) {
        String normalizedObjectKey = StringUtils.removeStart(StringUtils.trimToEmpty(objectKey), "/");
        VisitorSecurityTokenService.FileAccess fileAccess = visitorSecurityTokenService.validateFileAccess(normalizedObjectKey, accessToken);
        if (fileAccess.bizId() != null) {
            VisitorRecord record = mustGetByBizId(fileAccess.bizId());
            if (!recordOwnsObjectKey(record, normalizedObjectKey)) {
                throw new ForbiddenException("当前记录无权访问该附件");
            }
        }
        return objectStorageService.openStream(normalizedObjectKey);
    }

    private VisitorRecord mustGetByBizId(String bizId) {
        return visitorRecordRepository.findByBizId(bizId)
            .orElseThrow(() -> new BusinessException("访客记录不存在"));
    }

    private VisitorResultResp toResult(VisitorRecord record) {
        EntryDecision entryDecision = resolveEntryDecision(record);
        String resultAccessToken = visitorSecurityTokenService.generateResultAccessToken(record.getBizId());
        return VisitorResultResp.builder()
            .bizId(record.getBizId())
            .resultAccessToken(resultAccessToken)
            .recordNo(record.getRecordNo())
            .visitorName(record.getVisitorName())
            .idCardMasked(maskIdCard(cryptoService.decrypt(record.getIdCardNo())))
            .phoneMasked(maskPhone(record.getPhone()))
            .visitedUserName(record.getVisitedUserName())
            .visitedDeptName(record.getVisitedDeptName())
            .visitReason(record.getVisitReason())
            .plannedEntryTime(record.getPlannedEntryTime())
            .plannedExitTime(record.getPlannedExitTime())
            .status(record.getStatus())
            .statusText(statusText(record.getStatus(), isExpired(record)))
            .hikSyncStatus(record.getHikSyncStatus() == null ? null : record.getHikSyncStatus().name())
            .hikSyncStatusText(hikSyncStatusText(record))
            .hikSyncError(record.getHikSyncError())
            .hikAccessStatus(record.getHikAccessStatus() == null ? null : record.getHikAccessStatus().name())
            .hikAccessStatusText(hikAccessStatusText(record))
            .hikAccessError(record.getHikAccessError())
            .entryAllowed(entryDecision.allowed())
            .entryMessage(entryDecision.message())
            .approveRemark(record.getApproveRemark())
            .approveTime(record.getApproveTime())
            .createTime(record.getCreateTime())
            .expired(isExpired(record))
            .build();
    }

    private String statusText(int status, boolean expired) {
        if (expired && status == 0) {
            return "已过期";
        }
        return switch (status) {
            case 1 -> "审批通过";
            case 2 -> "审批拒绝";
            default -> "等待审批中";
        };
    }

    private boolean isExpired(VisitorRecord record) {
        return LocalDateTime.now().isAfter(record.getExpireTime());
    }

    private EntryDecision resolveEntryDecision(VisitorRecord record) {
        LocalDateTime now = LocalDateTime.now();
        if (record.getStatus() != 1) {
            if (record.getStatus() == 2) {
                return new EntryDecision(false, "审批已拒绝，不可入场");
            }
            if (isExpired(record)) {
                return new EntryDecision(false, "申请已过期，不可入场");
            }
            return new EntryDecision(false, "等待审批完成后方可入场");
        }
        if (now.isBefore(record.getPlannedEntryTime())) {
            return new EntryDecision(false, "审批已通过，但未到预计入场时间");
        }
        if (now.isAfter(record.getPlannedExitTime())) {
            return new EntryDecision(false, "审批已通过，但已超过预计出场时间");
        }
        if (hikvisionSyncService.isEnabled()) {
            if (record.getHikSyncStatus() == null || record.getHikSyncStatus() == com.visitor.system.visitor.domain.HikSyncStatus.PENDING) {
                return new EntryDecision(false, "审批已通过，海康同步进行中，请稍后刷新结果");
            }
            if (record.getHikSyncStatus() == com.visitor.system.visitor.domain.HikSyncStatus.FAILED) {
                return new EntryDecision(false, "审批已通过，但海康同步失败，请联系前台处理");
            }
            if (record.getHikSyncStatus() == com.visitor.system.visitor.domain.HikSyncStatus.DISABLED) {
                return new EntryDecision(false, "海康权限已回收，不可入场");
            }
            if (hikvisionSyncService.isAccessSyncEnabled()) {
                if (record.getHikAccessStatus() == null || record.getHikAccessStatus() == com.visitor.system.visitor.domain.HikSyncStatus.PENDING) {
                    return new EntryDecision(false, "审批已通过，门禁权限下发中，请稍后刷新结果");
                }
                if (record.getHikAccessStatus() == com.visitor.system.visitor.domain.HikSyncStatus.FAILED) {
                    return new EntryDecision(false, "审批已通过，但门禁权限下发失败，请联系前台处理");
                }
                if (record.getHikAccessStatus() == com.visitor.system.visitor.domain.HikSyncStatus.DISABLED) {
                    return new EntryDecision(false, "门禁权限已回收，不可入场");
                }
            }
        }
        return new EntryDecision(true, "审批通过，可入场");
    }

    private String hikSyncStatusText(VisitorRecord record) {
        if (!hikvisionSyncService.isEnabled()) {
            return "未启用海康同步";
        }
        if (record.getStatus() != 1) {
            return "待审批通过后同步";
        }
        if (record.getHikSyncStatus() == null) {
            return "待同步";
        }
        return switch (record.getHikSyncStatus()) {
            case PENDING -> "海康同步中";
            case SUCCESS -> "海康同步成功";
            case FAILED -> "海康同步失败";
            case DISABLED -> "海康权限已回收";
        };
    }

    private String hikAccessStatusText(VisitorRecord record) {
        if (!hikvisionSyncService.isAccessSyncEnabled()) {
            return null;
        }
        if (record.getStatus() != 1) {
            return "待审批通过后下发权限";
        }
        if (record.getHikSyncStatus() != com.visitor.system.visitor.domain.HikSyncStatus.SUCCESS) {
            return "待基础身份同步成功后下发权限";
        }
        if (record.getHikAccessStatus() == null) {
            return "待下发权限";
        }
        return switch (record.getHikAccessStatus()) {
            case PENDING -> "门禁权限下发中";
            case SUCCESS -> "门禁权限下发成功";
            case FAILED -> "门禁权限下发失败";
            case DISABLED -> "门禁权限已回收";
        };
    }

    private void validateVisitWindow(LocalDateTime plannedEntryTime, LocalDateTime plannedExitTime) {
        if (plannedEntryTime == null || plannedExitTime == null) {
            throw new BusinessException("请填写预计入场时间和预计出场时间");
        }
        if (!plannedExitTime.isAfter(plannedEntryTime)) {
            throw new BusinessException("预计出场时间必须晚于预计入场时间");
        }
        if (plannedEntryTime.isBefore(LocalDateTime.now().minusHours(2))) {
            throw new BusinessException("预计入场时间不能早于当前时间2小时以上");
        }
    }

    private String generateRecordNo() {
        LocalDate now = LocalDate.now();
        String sequenceDate = now.format(DateTimeFormatter.BASIC_ISO_DATE);
        long sequence = nextSequenceValue(sequenceDate);
        return "VIS-" + now.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + String.format("%04d", sequence);
    }

    private long nextSequenceValue(String sequenceDate) {
        for (int attempt = 0; attempt < 3; attempt++) {
            VisitorRecordSequence sequence = visitorRecordSequenceRepository.findBySequenceDateForUpdate(sequenceDate)
                .orElseGet(() -> createSequence(sequenceDate));
            sequence.setCurrentValue(sequence.getCurrentValue() + 1);
            if (sequence.getCurrentValue() == 1) {
                visitorRecordSequenceRepository.saveAndFlush(sequence);
            }
            return sequence.getCurrentValue();
        }
        throw new IllegalStateException("生成登记流水号失败，请稍后重试");
    }

    private VisitorRecordSequence createSequence(String sequenceDate) {
        VisitorRecordSequence sequence = new VisitorRecordSequence();
        sequence.setSequenceDate(sequenceDate);
        sequence.setCurrentValue(0L);
        try {
            return visitorRecordSequenceRepository.saveAndFlush(sequence);
        } catch (DataIntegrityViolationException exception) {
            return visitorRecordSequenceRepository.findBySequenceDateForUpdate(sequenceDate)
                .orElseThrow(() -> exception);
        }
    }

    private String maskIdCard(String idCardNo) {
        if (idCardNo.length() < 8) {
            return "****";
        }
        return idCardNo.substring(0, 4) + "**********" + idCardNo.substring(idCardNo.length() - 4);
    }

    private String maskPhone(String phone) {
        if (StringUtils.length(phone) < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String extractIdCardSuffix(String idCardNo) {
        String normalized = StringUtils.trimToEmpty(idCardNo).toUpperCase();
        return normalized.substring(normalized.length() - 6);
    }

    private String normalizeHikPersonCode(String value) {
        return StringUtils.trimToEmpty(value).replace("-", "");
    }

    private void ensureApproveOwner(VisitorRecord record, String currentUserId) {
        if (!record.getVisitedUserId().equals(currentUserId)) {
            throw new ForbiddenException("当前用户无权查看该审批详情");
        }
    }

    private boolean recordOwnsObjectKey(VisitorRecord record, String objectKey) {
        return objectKey.equals(record.getIdCardFront())
            || objectKey.equals(record.getIdCardBack())
            || objectKey.equals(record.getFacePhoto());
    }

    private String buildRecordFilePreviewUrl(String bizId, String objectKey) {
        if (StringUtils.isBlank(objectKey)) {
            return null;
        }
        return visitorSecurityTokenService.buildRecordFilePreviewUrl(bizId, objectKey);
    }

    private String buildResultUrl(String bizId, String resultAccessToken) {
        return properties.getAppBaseUrl() + "/visitor/result/" + bizId + "?accessToken=" + resultAccessToken;
    }

    protected void sendApproveNotification(VisitorRecord record) {
        String actionUrl = properties.getAppBaseUrl()
            + "/visitor/approve?bizId=" + record.getBizId()
            + "&token=" + record.getApproveToken()
            + (StringUtils.isBlank(properties.getDingtalk().getCorpId()) ? "" : "&corpId=" + properties.getDingtalk().getCorpId());
        DingTalkOaMessage message = DingTalkOaMessage.builder()
            .title("来访审批待处理")
            .headText("待审批")
            .content("请尽快核对访客信息与来访时间，确认后完成审批。")
            .author("访客审批系统")
            .actionUrl(actionUrl)
            .forms(List.of(
                formItem("访客姓名", record.getVisitorName()),
                formItem("联系电话", maskPhone(record.getPhone())),
                formItem("来访时间", formatNotifyTime(record.getPlannedEntryTime()) + " - " + formatNotifyTime(record.getPlannedExitTime())),
                formItem("被访部门", StringUtils.defaultIfBlank(record.getVisitedDeptName(), "未填写")),
                formItem("访问事由", StringUtils.defaultIfBlank(record.getVisitReason(), "未填写")),
                formItem("登记编号", record.getRecordNo())
            ))
            .build();
        dingTalkNotifyService.sendApproveNotification(record.getVisitedUserId(), message);
    }

    private record EntryDecision(boolean allowed, String message) {
    }

    private DingTalkOaMessage.FormItem formItem(String key, String value) {
        return DingTalkOaMessage.FormItem.builder()
            .key(key)
            .value(value)
            .build();
    }

    private String formatNotifyTime(LocalDateTime value) {
        return value == null ? "--" : value.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
    }
}
