package com.visitor.system.visitor.service;

import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import com.visitor.system.visitor.domain.HikSyncStatus;
import com.visitor.system.visitor.domain.VisitorRecord;
import com.visitor.system.visitor.dto.HikvisionAddFaceCommand;
import com.visitor.system.visitor.dto.HikvisionAddPersonCommand;
import com.visitor.system.visitor.dto.HikvisionFaceResult;
import com.visitor.system.visitor.dto.HikvisionGrantAccessCommand;
import com.visitor.system.visitor.dto.HikvisionRevokeAccessCommand;
import com.visitor.system.visitor.repository.VisitorRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;

/**
 * 访客记录与海康平台的业务同步编排。
 *
 * <p>审批通过后负责“人员 -> 人脸 -> 门禁权限”的入场下发；
 * 访客到期后负责“权限 -> 人脸 -> 人员”的退场回收。
 */
@Service
public class HikvisionSyncService {

    private static final Logger log = LoggerFactory.getLogger(HikvisionSyncService.class);
    private static final int DEFAULT_PERSON_READY_MAX_ATTEMPTS = 20;
    private static final long DEFAULT_PERSON_READY_WAIT_MILLIS = 1000L;
    private static final int DEFAULT_FACE_READY_MAX_ATTEMPTS = 20;
    private static final long DEFAULT_FACE_READY_WAIT_MILLIS = 1000L;

    private final VisitorRecordRepository visitorRecordRepository;
    private final VisitorProperties properties;
    private final ObjectProvider<HikvisionClient> hikvisionClientProvider;
    private final HikvisionAccessResourceService hikvisionAccessResourceService;
    private final ObjectStorageService objectStorageService;
    private final CryptoService cryptoService;
    private final FileValidationService fileValidationService;
    private final TaskScheduler taskScheduler;

    public HikvisionSyncService(VisitorRecordRepository visitorRecordRepository,
                                VisitorProperties properties,
                                ObjectProvider<HikvisionClient> hikvisionClientProvider,
                                HikvisionAccessResourceService hikvisionAccessResourceService,
                                ObjectStorageService objectStorageService,
                                CryptoService cryptoService,
                                FileValidationService fileValidationService,
                                TaskScheduler taskScheduler) {
        this.visitorRecordRepository = visitorRecordRepository;
        this.properties = properties;
        this.hikvisionClientProvider = hikvisionClientProvider;
        this.hikvisionAccessResourceService = hikvisionAccessResourceService;
        this.objectStorageService = objectStorageService;
        this.cryptoService = cryptoService;
        this.fileValidationService = fileValidationService;
        this.taskScheduler = taskScheduler;
    }

    public boolean isEnabled() {
        return properties.getHikvision().isEnabled();
    }

    public HikSyncStatus initialApproveStatus() {
        // 海康关闭时本系统仍可独立完成审批流程，因此初始同步状态直接视为成功。
        return isEnabled() ? HikSyncStatus.PENDING : HikSyncStatus.SUCCESS;
    }

    public LocalDateTime initialSyncTime() {
        return isEnabled() ? null : LocalDateTime.now();
    }

    public boolean isAccessSyncEnabled() {
        return isEnabled() && properties.getHikvision().getAccess().isEnabled();
    }

    @Async
    public void syncApprovedRecordAsync(Long recordId) {
        // 审批接口不等待海康链路完成，后台异步下发，结果通过记录状态展示给用户/管理员。
        syncApprovedRecord(recordId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncApprovedRecord(Long recordId) {
        VisitorRecord record = visitorRecordRepository.findById(recordId).orElse(null);
        if (record == null || record.getStatus() != 1) {
            return;
        }
        // 已成功下发人员/人脸的记录不重复执行主链路；如果只剩权限未成功，则单独补权限。
        if (!isEnabled() || record.getHikSyncStatus() == HikSyncStatus.SUCCESS || record.getHikSyncStatus() == HikSyncStatus.DISABLED) {
            if (isAccessSyncEnabled()
                && record.getHikSyncStatus() == HikSyncStatus.SUCCESS
                && record.getHikAccessStatus() != HikSyncStatus.SUCCESS
                && record.getHikAccessStatus() != HikSyncStatus.DISABLED) {
                syncAccess(record);
                visitorRecordRepository.save(record);
            }
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!record.getPlannedExitTime().isAfter(now)) {
            // 计划离场时间已过的访客不再下发海康，避免刚审批完成就生成无效通行权限。
            record.setHikSyncStatus(HikSyncStatus.FAILED);
            record.setHikSyncError("记录已过期，停止下发海康");
            record.setHikLastRetryTime(now);
            record.setHikRetryCount(nextRetryCount(record));
            if (isAccessSyncEnabled() && record.getHikAccessStatus() == null) {
                record.setHikAccessStatus(HikSyncStatus.FAILED);
                record.setHikAccessError("记录已过期，停止下发门禁权限");
                record.setHikAccessLastRetryTime(now);
                record.setHikAccessRetryCount(nextAccessRetryCount(record));
            }
            visitorRecordRepository.save(record);
            return;
        }

        // 1. 本次同步先确定海康人员编码；默认用 bizId 去掉横线，保证每次重试拿到同一个 personIndexCode。
        record.setHikPersonCode(defaultPersonCode(record));
        // 2. 记录本次尝试时间，方便后台页面和后续补偿任务判断最近一次同步发生在什么时候。
        record.setHikLastRetryTime(now);
        // 3. 重试次数先加一，后面遇到“人员未落库”时会用它判断是否超过最大等待次数。
        record.setHikRetryCount(nextRetryCount(record));
        try {
            // 审批通过后的固定顺序：加人 -> 等人员落平台 -> 加人脸 -> 下发权限配置。
            // 4. 先读取并重新预处理人脸图片，保证传给海康的是服务端标准化后的 JPG 字节。
            byte[] faceBytes = readFaceImage(record);
            // 5. 新增或复用海康人员；这个方法内部会处理 addPerson 后 personId 不可立即读取的问题。
            String personId = resolveOrCreatePerson(record);
            // 6. 把当前拿到的海康真实人员 ID 保存下来，后续重试不必重新新增人员。
            record.setHikPersonId(personId);
            // 7. 使用真实 personId 绑定人脸；如果海康返回“人员不存在”，内部会转成延迟重试异常。
            HikvisionFaceResult faceResult = addFaceWhenPersonReady(record, personId, faceBytes);
            // 8. 保存海康返回的人脸索引，后续到期退场时会用它删除人脸。
            record.setHikFaceIndexCode(faceResult.getFaceIndexCode());
            // 9. 保存海康返回的人脸图片地址，便于排查海康侧最终绑定的图片。
            record.setHikFacePicUrl(faceResult.getFacePicUrl());
            // 10. 人员和人脸都成功后，主同步状态才标记成功。
            record.setHikSyncStatus(HikSyncStatus.SUCCESS);
            // 11. 记录主同步成功时间。
            record.setHikSyncTime(now);
            // 12. 清空之前可能留下的失败原因。
            record.setHikSyncError(null);
            if (!isAccessSyncEnabled()) {
                // 未启用门禁权限模块时，人员和人脸成功即视为海康链路完成。
                // 13. 如果现场没有启用 ACPS 权限下发，本地把权限状态也视为成功，避免页面一直显示待同步。
                record.setHikAccessStatus(HikSyncStatus.SUCCESS);
                // 14. 权限状态跟随主链路记录成功时间。
                record.setHikAccessSyncTime(now);
                // 15. 清空权限错误。
                record.setHikAccessError(null);
            } else {
                // 16. 如果启用了门禁权限，人员和人脸成功后继续下发通行权限。
                syncAccess(record);
            }
            // 17. 所有状态字段更新完成后统一落库。
            visitorRecordRepository.save(record);
        } catch (DeferredRetryException exception) {
            // 18. DeferredRetryException 只表示海康平台暂时没准备好，不是最终失败。
            record.setHikSyncStatus(HikSyncStatus.PENDING);
            // 19. 把“稍后重试”的业务提示保存下来，页面和日志都能看到当前卡在哪一步。
            record.setHikSyncError(exception.getMessage());
            // 20. 先保存 PENDING 状态，防止应用重启后丢失当前同步进度。
            visitorRecordRepository.save(record);
            // 21. 按异常里带出的 delayMillis 安排下一次自动重试。
            scheduleRetry(record.getId(), exception.delayMillis());
            // 22. 记录延迟重试日志，方便排查是不是海康 addPerson 后 personFace 读取太快。
            log.info("hikvision sync deferred for retry, bizId={}, delay={}ms, reason={}",
                record.getBizId(),
                exception.delayMillis(),
                exception.getMessage());
        } catch (Exception exception) {
            // 23. 进入这里说明不是“人员暂未就绪”这类可立即延迟重试的问题，先截断错误防止字段超长。
            String message = truncateMessage(exception.getMessage());
            if (isAlreadyExistsMessage(message)) {
                // 海康按实名唯一时，重复来访会提示已存在；优先复用旧人员，避免人为清库才能继续登记。
                // 24. 如果是人员已存在，尝试复用已存在人员并继续下发人脸和权限。
                if (reuseExistingPerson(record, now)) {
                    // 25. 复用成功后保存状态并结束，不把这类情况暴露成失败。
                    visitorRecordRepository.save(record);
                    return;
                }
                // 26. 已存在但无法复用时，给出明确的人工处理提示。
                message = "海康已存在同实名人员，但未找到可复用的本地人员映射，请先处理旧访客或补齐人员映射";
            }
            // 27. 其他异常按真正失败处理，等待定时补偿或人工排查。
            record.setHikSyncStatus(HikSyncStatus.FAILED);
            // 28. 保存失败原因。
            record.setHikSyncError(message);
            // 29. 失败状态落库。
            visitorRecordRepository.save(record);
            // 30. 输出 warn 日志，保留 bizId 和失败原因用于定位。
            log.warn("sync visitor to hikvision failed, bizId={}, reason={}", record.getBizId(), message);
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void retryFailedOrPendingSync() {
        if (!isEnabled()) {
            return;
        }
        // 定时补偿覆盖异步任务丢失、海康短暂不可用和人员异步落库慢的场景。
        List<VisitorRecord> records = visitorRecordRepository.findRecordsForHikSyncRetry(
            List.of(HikSyncStatus.PENDING, HikSyncStatus.FAILED),
            LocalDateTime.now(),
            PageRequest.of(0, syncBatchSize())
        );
        records.forEach(record -> syncApprovedRecord(record.getId()));
    }

    @Scheduled(cron = "30 */5 * * * *")
    public void retryFailedOrPendingAccessSync() {
        if (!isAccessSyncEnabled()) {
            return;
        }
        // 权限下发和人员/人脸主链路分开补偿，避免权限目标问题反复重放人员新增。
        List<VisitorRecord> records = visitorRecordRepository.findRecordsForHikAccessRetry(
            List.of(HikSyncStatus.PENDING, HikSyncStatus.FAILED),
            LocalDateTime.now(),
            PageRequest.of(0, syncBatchSize())
        );
        records.forEach(record -> syncAccess(record.getId()));
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void disableExpiredVisitors() {
        if (!isEnabled()) {
            return;
        }
        // 到期回收由服务端定时执行，不依赖用户再次打开结果页。
        List<VisitorRecord> records = visitorRecordRepository.findRecordsForHikDisable(
            HikSyncStatus.SUCCESS,
            LocalDateTime.now(),
            PageRequest.of(0, syncBatchSize())
        );
        records.forEach(record -> disableExpiredRecord(record.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void disableExpiredRecord(Long recordId) {
        VisitorRecord record = visitorRecordRepository.findById(recordId).orElse(null);
        if (record == null || record.getStatus() != 1 || record.getHikSyncStatus() != HikSyncStatus.SUCCESS || record.getHikDisabledTime() != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            // 退场顺序固定为：删权限 -> 删人脸 -> 删人员，保证门禁先失效。
            if (isAccessSyncEnabled()) {
                try {
                    List<com.visitor.system.visitor.dto.HikvisionAccessResourceInfo> resourceInfos = hikvisionAccessResourceService.resolveAccessResourceInfos();
                    hikvisionClient().revokeAccess(HikvisionRevokeAccessCommand.builder()
                        .personId(resolveAccessPersonId(record))
                        .resourceType(properties.getHikvision().getAccess().getResourceType())
                        .resourceInfos(resourceInfos)
                        .build());
                    record.setHikAccessStatus(HikSyncStatus.DISABLED);
                    record.setHikAccessDisabledTime(now);
                    record.setHikAccessError(null);
                } catch (Exception exception) {
                    record.setHikAccessStatus(HikSyncStatus.FAILED);
                    record.setHikAccessLastRetryTime(now);
                    record.setHikAccessRetryCount(nextAccessRetryCount(record));
                    record.setHikAccessError("权限回收失败：" + truncateMessage(exception.getMessage()));
                    log.warn("revoke hikvision access failed, bizId={}, reason={}", record.getBizId(), exception.getMessage());
                }
            }
            if (record.getHikFaceIndexCode() != null && !record.getHikFaceIndexCode().isBlank()) {
                hikvisionClient().deleteFace(properties.getHikvision().getFaceGroupIndexCode(), record.getHikFaceIndexCode());
            }
            try {
                hikvisionClient().disablePerson(defaultPersonCode(record));
            } catch (Exception exception) {
                log.warn("disable hikvision person failed but continue, bizId={}, reason={}", record.getBizId(), exception.getMessage());
            }
            record.setHikSyncStatus(HikSyncStatus.DISABLED);
            record.setHikDisabledTime(now);
            record.setHikFaceIndexCode(null);
            record.setHikFacePicUrl(null);
            record.setHikSyncError(null);
            visitorRecordRepository.save(record);
        } catch (Exception exception) {
            record.setHikLastRetryTime(now);
            record.setHikRetryCount(nextRetryCount(record));
            record.setHikSyncError("禁用失败：" + truncateMessage(exception.getMessage()));
            visitorRecordRepository.save(record);
            log.warn("disable hikvision visitor failed, bizId={}, reason={}", record.getBizId(), exception.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncAccess(Long recordId) {
        VisitorRecord record = visitorRecordRepository.findById(recordId).orElse(null);
        if (record == null) {
            return;
        }
        syncAccess(record);
        visitorRecordRepository.save(record);
    }

    private byte[] readFaceImage(VisitorRecord record) {
        if (record.getFacePhoto() == null || record.getFacePhoto().isBlank()) {
            throw new BusinessException("缺少人脸自拍照片，无法下发海康");
        }
        // 审批后下发人脸时再次走本地预处理，确保传给 PMAS 的图片与上传阶段规则一致。
        return fileValidationService.prepareFaceImage(objectStorageService.readObject(record.getFacePhoto()));
    }

    private HikvisionClient hikvisionClient() {
        HikvisionClient client = hikvisionClientProvider.getIfAvailable();
        if (client == null) {
            throw new BusinessException("海康客户端未启用");
        }
        return client;
    }

    private String defaultPersonCode(VisitorRecord record) {
        if (record.getHikPersonCode() != null && !record.getHikPersonCode().isBlank()) {
            return record.getHikPersonCode().replace("-", "");
        }
        // bizId 去掉横线后作为默认 personIndexCode，既唯一又便于按本地记录反查海康人员。
        return record.getBizId().replace("-", "");
    }

    private int nextRetryCount(VisitorRecord record) {
        return (record.getHikRetryCount() == null ? 0 : record.getHikRetryCount()) + 1;
    }

    private int nextAccessRetryCount(VisitorRecord record) {
        return (record.getHikAccessRetryCount() == null ? 0 : record.getHikAccessRetryCount()) + 1;
    }

    private String truncateMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private boolean isAlreadyExistsMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("已存在") || message.toLowerCase().contains("already exists");
    }

    private String resolveAccessPersonId(VisitorRecord record) {
        if (record.getHikPersonId() != null && !record.getHikPersonId().isBlank()) {
            return record.getHikPersonId();
        }
        return defaultPersonCode(record);
    }

    private boolean reuseExistingPerson(VisitorRecord record, LocalDateTime now) {
        // 复用顺序：本记录已有 personId -> 海康按 personCode 查询 -> 本地同身份证最近成功记录。
        if (record.getHikPersonId() != null && !record.getHikPersonId().isBlank()) {
            return continueWithExistingPerson(record, now, record.getHikPersonCode(), record.getHikPersonId());
        }
        String queriedPersonId = queryExistingPersonId(record);
        if (queriedPersonId != null) {
            return continueWithExistingPerson(record, now, record.getHikPersonCode(), queriedPersonId);
        }
        return visitorRecordRepository
            .findTopByIdCardNoAndIdNotAndHikPersonIdIsNotNullOrderByCreateTimeDesc(record.getIdCardNo(), record.getId())
            .map(existing -> continueWithExistingPerson(
                record,
                now,
                defaultPersonCode(existing),
                existing.getHikPersonId()
            ))
            .orElse(false);
    }

    private boolean continueWithExistingPerson(VisitorRecord record, LocalDateTime now, String personCode, String personId) {
        // 复用旧人员后仍然重新下发本次访客的人脸和权限，保证入场时间、照片和通行范围都是最新的。
        record.setHikPersonCode(personCode == null ? defaultPersonCode(record) : personCode.replace("-", ""));
        record.setHikPersonId(personId);
        tryEnablePerson(record);
        byte[] faceBytes = readFaceImage(record);
        HikvisionFaceResult faceResult = addFaceWhenPersonReady(record, record.getHikPersonId(), faceBytes);
        record.setHikFaceIndexCode(faceResult.getFaceIndexCode());
        record.setHikFacePicUrl(faceResult.getFacePicUrl());
        record.setHikSyncStatus(HikSyncStatus.SUCCESS);
        record.setHikSyncTime(now);
        record.setHikSyncError(null);
        if (!isAccessSyncEnabled()) {
            record.setHikAccessStatus(HikSyncStatus.SUCCESS);
            record.setHikAccessSyncTime(now);
            record.setHikAccessError(null);
        } else {
            syncAccess(record);
        }
        return true;
    }

    private String resolveOrCreatePerson(VisitorRecord record) {
        // 如果本地已有海康人员映射，先查一次真实 personId，避免拿临时编码直接去绑人脸。
        if (record.getHikPersonId() != null && !record.getHikPersonId().isBlank()) {
            // 1. 重试或复用链路里，本地可能已经存了 personId；先按 personCode 去海康确认真实 ID。
            String personId = resolveTruePersonId(record);
            // 2. 已存在人员可能曾被退场禁用，先尝试启用，启用失败不阻断主链路。
            tryEnablePerson(record);
            // 3. 返回可用于 personFace 的真实 personId。
            return personId;
        }
        // 4. 本地没有 personId，说明这是第一次下发或之前没成功走到人员创建。
        String personId = hikvisionClient().addPerson(HikvisionAddPersonCommand.builder()
            // 5. personCode 会作为海康 personIndexCode，是本系统和海康之间的幂等关联键。
            .personCode(record.getHikPersonCode())
            // 6. 访客姓名写入海康人员姓名。
            .personName(record.getVisitorName())
            // 7. 访客手机号写入海康人员手机号。
            .phone(record.getPhone())
            // 8. 身份证号在本地是加密存储，下发前解密给海康实名字段。
            .idCardNo(cryptoService.decrypt(record.getIdCardNo()))
            // 9. orgIndexCode 决定人员创建到海康哪个组织下。
            .orgIndexCode(properties.getHikvision().getOrgIndexCode())
            .build());
        // 10. 先把 addPerson 返回值保存下来；即使它只是本地编码，也能帮助后续排查和补偿。
        record.setHikPersonId(personId);
        if (personId != null && !personId.equals(defaultPersonCode(record))) {
            // 11. 如果海康直接返回了真实 personId，说明人员已经可被后续接口引用，直接返回。
            return personId;
        }
        // 新增成功但未拿到真实 personId 时，立即补查；仍查不到则等待海康异步落库后重试。
        // 12. 有些现场 addPerson 成功后只返回 personIndexCode，需要再调 detailV1 查真实 personId。
        String queriedPersonId = queryExistingPersonId(record);
        if (queriedPersonId != null && !queriedPersonId.isBlank()) {
            // 13. 如果 detailV1 已经查到真实 personId，返回它给 personFace 使用。
            return queriedPersonId;
        }
        // 14. addPerson 成功但 detailV1 仍查不到，说明海康平台还在异步落库，触发延迟重试。
        throw deferredRetryException(
            // 15. 当前已经尝试的次数，用来判断是否达到最大等待次数。
            record.getHikRetryCount(),
            // 16. 人员落库最大等待次数来自配置。
            properties.getHikvision().getPersonReadyMaxAttempts(),
            // 17. 每次等待多久来自配置。
            properties.getHikvision().getPersonReadyWaitMillis(),
            // 18. 这条消息会保存到 hikSyncError，便于前端/后台展示。
            "海康人员尚未落库，稍后自动重试"
        );
    }

    private void tryEnablePerson(VisitorRecord record) {
        try {
            // 1. 复用旧人员前尝试把人员状态启用，避免旧访客退场后处于禁用状态。
            hikvisionClient().enablePerson(defaultPersonCode(record));
        } catch (Exception exception) {
            // 启用旧人员是增强动作，失败不应阻断后续人脸/权限补偿，由海康主链路结果兜底。
            // 2. 启用失败只记日志；后续 addFace/grantAccess 如果真不可用，会按主链路失败处理。
            log.warn("enable hikvision person failed but continue, bizId={}, reason={}", record.getBizId(), exception.getMessage());
        }
    }

    private HikvisionFaceResult addFaceWhenPersonReady(VisitorRecord record, String personId, byte[] faceBytes) {
        try {
            // 1. 调用海康 personFace 接口前，必须使用上一步拿到的真实 personId。
            return hikvisionClient().addFace(HikvisionAddFaceCommand.builder()
                // 2. personId 是海康人员主键，不是本系统 bizId，也不是临时 fileKey。
                .personId(personId)
                // 3. faceBytes 是服务端预处理后的 JPG 字节，避免原图格式/方向影响海康识别。
                .faceImageBytes(faceBytes)
                .build());
        } catch (Exception exception) {
            // 4. 捕获 personFace 返回的异常，先判断是不是“人员还没同步完成”。
            if (isPersonNotReadyMessage(exception.getMessage())) {
                // PMAS 新增人员不是强一致，personFace 可能短时间内还查不到人，按可配置延迟重试处理。
                // 5. 命中“人员不存在”时，不立刻失败，而是把它转换成可恢复的延迟重试。
                throw deferredRetryException(
                    // 6. 当前重试次数来自记录字段，主流程每次进来都会加一。
                    record.getHikRetryCount(),
                    // 7. 人脸绑定等待最大次数来自 face-ready-max-attempts。
                    properties.getHikvision().getFaceReadyMaxAttempts(),
                    // 8. 人脸绑定等待间隔来自 face-ready-wait-millis。
                    properties.getHikvision().getFaceReadyWaitMillis(),
                    // 9. 这条消息会保存到 hikSyncError，说明当前卡在人脸下发等待人员同步。
                    "海康人员同步中，稍后自动重试人脸下发"
                );
            }
            // 10. 不是“人员不存在”的 RuntimeException，说明是明确业务失败或网关失败，按原异常抛出。
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            // 11. 非 RuntimeException 统一包装，交给主流程按失败处理。
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private void scheduleRetry(Long recordId, long delayMillis) {
        // 延迟重试只针对平台异步就绪问题；其他明确失败交给五分钟定时补偿或人工处理。
        // 1. 使用 TaskScheduler 在当前 JVM 内延迟执行，不阻塞当前审批线程。
        // 2. 重试时重新进入 syncApprovedRecordAsync，整个流程会再次查人员、再绑人脸。
        taskScheduler.schedule(() -> syncApprovedRecordAsync(recordId), Instant.now().plusMillis(delayMillis));
    }

    private int configuredAttempts(Integer configuredValue, int defaultValue) {
        // 配置为空或小于等于 0 时使用默认值，避免错误配置导致不能重试。
        return configuredValue != null && configuredValue > 0 ? configuredValue : defaultValue;
    }

    private long configuredWaitMillis(Integer configuredValue, long defaultValue) {
        // 等待时间同样做兜底，避免配置为空时延迟重试失效。
        return configuredValue != null && configuredValue > 0 ? configuredValue : defaultValue;
    }

    private RuntimeException deferredRetryException(Integer currentRetryCount,
                                                    Integer configuredAttempts,
                                                    Integer configuredWaitMillis,
                                                    String message) {
        // readiness 重试次数用配置保护，避免海康长时间不可用时无限占用后台任务。
        // 1. 先解析最大重试次数，配置不可用时使用默认 20 次。
        int maxAttempts = configuredAttempts(configuredAttempts, DEFAULT_PERSON_READY_MAX_ATTEMPTS);
        // 2. 再解析每次等待毫秒数，配置不可用时使用默认 1000ms。
        long waitMillis = configuredWaitMillis(configuredWaitMillis, DEFAULT_PERSON_READY_WAIT_MILLIS);
        // 3. 当前重试次数为空时按 0 处理。
        int retryCount = currentRetryCount == null ? 0 : currentRetryCount;
        if (retryCount >= maxAttempts) {
            // 4. 超过最大次数后不再伪装成可恢复问题，转成普通异常，主流程会标记 FAILED。
            return new IllegalStateException("海康同步等待超时，请稍后重试");
        }
        // 5. 未超过最大次数时返回 DeferredRetryException，主流程会保存 PENDING 并 scheduleRetry。
        return new DeferredRetryException(message, waitMillis);
    }

    private int syncBatchSize() {
        Integer configuredBatchSize = properties.getHikvision().getSyncBatchSize();
        return configuredBatchSize == null ? 50 : Math.max(configuredBatchSize, 1);
    }

    private String resolveTruePersonId(VisitorRecord record) {
        // 1. 按本系统稳定 personCode 调海康 detailV1 查询真实 personId。
        String queriedPersonId = queryExistingPersonId(record);
        if (queriedPersonId != null) {
            // 2. 查到后更新本地 hikPersonId，后续人脸和权限都使用这个真实 ID。
            record.setHikPersonId(queriedPersonId);
            // 3. 返回真实 ID。
            return queriedPersonId;
        }
        // 4. 查不到时保留本地已有值，由后续 addFaceWhenPersonReady 判断是否需要延迟重试。
        return record.getHikPersonId();
    }

    private String queryExistingPersonId(VisitorRecord record) {
        try {
            // 1. findPersonId 内部会调海康 detailV1，并兼容不同返回结构。
            return hikvisionClient().findPersonId(defaultPersonCode(record));
        } catch (Exception exception) {
            if (isPersonNotReadyMessage(exception.getMessage())) {
                // 查询不到人员属于可恢复状态，留给 resolveOrCreatePerson/addFaceWhenPersonReady 触发延迟重试。
                // 2. detailV1 返回人员不存在时，说明 addPerson 后平台可能还没落库，这里返回 null。
                log.debug("hikvision person not ready yet, bizId={}, reason={}", record.getBizId(), exception.getMessage());
                return null;
            }
            // 3. 其他查询失败先不阻断，后续 addFace 或主流程会给出最终失败/重试结果。
            log.warn("query hikvision person failed, bizId={}, reason={}", record.getBizId(), exception.getMessage());
            return null;
        }
    }

    private boolean isPersonNotReadyMessage(String message) {
        // 1. 没有错误消息时无法判定为人员未就绪。
        if (message == null || message.isBlank()) {
            return false;
        }
        // 2. 英文错误统一转小写，兼容海康或网关返回的英文描述。
        String lowerCaseMessage = message.toLowerCase();
        // 3. 中文“人员在平台上不存在”是当前现场最常见的 PMAS 异步落库提示。
        return message.contains("人员在平台上不存在")
            // 4. 兼容简化版中文错误。
            || message.contains("人员不存在")
            // 5. 兼容英文 person not exist / person not found / person does not exist。
            || (lowerCaseMessage.contains("person")
            && (lowerCaseMessage.contains("not exist")
            || lowerCaseMessage.contains("not found")
            || lowerCaseMessage.contains("does not exist")));
    }

    private void syncAccess(VisitorRecord record) {
        LocalDateTime now = LocalDateTime.now();
        if (!isAccessSyncEnabled()) {
            record.setHikAccessStatus(HikSyncStatus.SUCCESS);
            record.setHikAccessSyncTime(now);
            record.setHikAccessError(null);
            return;
        }
        if (record.getStatus() != 1 || record.getHikSyncStatus() != HikSyncStatus.SUCCESS) {
            return;
        }
        if (record.getHikAccessStatus() == HikSyncStatus.SUCCESS || record.getHikAccessStatus() == HikSyncStatus.DISABLED) {
            return;
        }
        record.setHikAccessLastRetryTime(now);
        record.setHikAccessRetryCount(nextAccessRetryCount(record));
        try {
            // 权限下发只在人员/人脸主链路成功后执行，使用计划访问时间作为海康通行有效期。
            List<com.visitor.system.visitor.dto.HikvisionAccessResourceInfo> resourceInfos = hikvisionAccessResourceService.resolveAccessResourceInfos();
            hikvisionClient().grantAccess(HikvisionGrantAccessCommand.builder()
                .personId(resolveAccessPersonId(record))
                .beginTime(record.getPlannedEntryTime())
                .endTime(record.getPlannedExitTime())
                .resourceType(properties.getHikvision().getAccess().getResourceType())
                .resourceInfos(resourceInfos)
                .build());
            record.setHikAccessStatus(HikSyncStatus.SUCCESS);
            record.setHikAccessSyncTime(now);
            record.setHikAccessError(null);
        } catch (Exception exception) {
            record.setHikAccessStatus(HikSyncStatus.FAILED);
            record.setHikAccessError(truncateMessage(exception.getMessage()));
            log.warn("grant hikvision access failed, bizId={}, reason={}", record.getBizId(), exception.getMessage());
        }
    }

    private static class DeferredRetryException extends RuntimeException {

        private final long delayMillis;

        private DeferredRetryException(String message, long delayMillis) {
            super(message);
            this.delayMillis = delayMillis;
        }

        private long delayMillis() {
            return delayMillis;
        }
    }

}
