package com.visitor.system.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visitor.system.admin.domain.IntegrationConfigVersion;
import com.visitor.system.admin.dto.IntegrationConfigPayload;
import com.visitor.system.admin.dto.IntegrationConfigVersionResp;
import com.visitor.system.admin.dto.IntegrationConfigViewResp;
import com.visitor.system.admin.repository.IntegrationConfigVersionRepository;
import com.visitor.system.common.BusinessException;
import com.visitor.system.config.VisitorProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Order(20)
public class IntegrationConfigService implements ApplicationRunner {

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String ARCHIVED = "ARCHIVED";
    private static final String MASK = "********";

    private final IntegrationConfigVersionRepository repository;
    private final IntegrationConfigCryptoService cryptoService;
    private final IntegrationConfigProbeService probeService;
    private final VisitorProperties properties;
    private final ObjectMapper objectMapper;

    public IntegrationConfigService(IntegrationConfigVersionRepository repository,
                                    IntegrationConfigCryptoService cryptoService,
                                    IntegrationConfigProbeService probeService,
                                    VisitorProperties properties,
                                    ObjectMapper objectMapper) {
        this.repository = repository;
        this.cryptoService = cryptoService;
        this.probeService = probeService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        repository.findTopByStatusOrderByVersionNoDesc(PUBLISHED)
            .map(version -> cryptoService.decrypt(version.getEncryptedPayload()))
            .ifPresent(this::apply);
    }

    @Transactional(readOnly = true)
    public IntegrationConfigViewResp effective() {
        IntegrationConfigVersion active = repository.findTopByStatusOrderByVersionNoDesc(PUBLISHED).orElse(null);
        IntegrationConfigPayload payload = active == null ? fromProperties() : cryptoService.decrypt(active.getEncryptedPayload());
        return IntegrationConfigViewResp.builder()
            .activeVersionId(active == null ? null : active.getId())
            .activeVersionNo(active == null ? null : active.getVersionNo())
            .config(mask(payload))
            .build();
    }

    @Transactional(readOnly = true)
    public List<IntegrationConfigVersionResp> versions() {
        return repository.findAllByOrderByVersionNoDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public IntegrationConfigVersionResp createDraft(IntegrationConfigPayload request, Long actorUserId) {
        IntegrationConfigPayload current = currentRaw();
        mergeSecrets(request, current);
        validate(request, current);
        IntegrationConfigCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(request);
        IntegrationConfigVersion version = new IntegrationConfigVersion();
        version.setVersionNo(nextVersionNo());
        version.setStatus(DRAFT);
        version.setEncryptedPayload(encrypted.ciphertext());
        version.setPayloadHash(encrypted.hash());
        version.setTestStatus("NOT_TESTED");
        version.setCreatedBy(actorUserId);
        version.setPreviousVersionId(repository.findTopByStatusOrderByVersionNoDesc(PUBLISHED).map(IntegrationConfigVersion::getId).orElse(null));
        return toResponse(repository.save(version));
    }

    @Transactional
    public IntegrationConfigVersionResp test(Long versionId, Long actorUserId) {
        IntegrationConfigVersion version = requireStatusForUpdate(versionId, DRAFT);
        if (!version.getCreatedBy().equals(actorUserId)) {
            throw new BusinessException("只有草稿创建人可以执行连通测试");
        }
        try {
            version.setTestSummary(probeService.test(cryptoService.decrypt(version.getEncryptedPayload())));
            version.setTestStatus("PASSED");
        } catch (BusinessException exception) {
            version.setTestStatus("FAILED");
            version.setTestSummary(StringUtils.abbreviate(exception.getMessage(), 1000));
        }
        version.setTestedTime(LocalDateTime.now());
        return toResponse(repository.save(version));
    }

    @Transactional
    public IntegrationConfigVersionResp submit(Long versionId, Long actorUserId) {
        IntegrationConfigVersion version = requireStatusForUpdate(versionId, DRAFT);
        if (!version.getCreatedBy().equals(actorUserId)) {
            throw new BusinessException("只有草稿创建人可以提交复核");
        }
        if (!"PASSED".equals(version.getTestStatus()) || version.getTestedTime() == null
            || version.getTestedTime().isBefore(LocalDateTime.now().minusMinutes(10))) {
            throw new BusinessException("配置必须在最近10分钟内通过连通测试");
        }
        version.setStatus(PENDING_REVIEW);
        version.setSubmittedBy(actorUserId);
        version.setSubmittedTime(LocalDateTime.now());
        return toResponse(repository.save(version));
    }

    @Transactional
    public IntegrationConfigVersionResp publish(Long versionId, Long reviewerUserId) {
        IntegrationConfigVersion version = requireStatusForUpdate(versionId, PENDING_REVIEW);
        if (reviewerUserId.equals(version.getSubmittedBy()) || reviewerUserId.equals(version.getCreatedBy())) {
            throw new BusinessException("配置发布必须由另一名管理员复核");
        }
        IntegrationConfigVersion active = repository.findFirstByStatusOrderByVersionNoDesc(PUBLISHED).orElse(null);
        if (active != null) {
            active.setStatus(ARCHIVED);
            repository.save(active);
            version.setPreviousVersionId(active.getId());
        }
        version.setStatus(PUBLISHED);
        version.setReviewedBy(reviewerUserId);
        version.setReviewedTime(LocalDateTime.now());
        version.setPublishedTime(LocalDateTime.now());
        IntegrationConfigVersion saved = repository.save(version);
        IntegrationConfigPayload payload = cryptoService.decrypt(saved.getEncryptedPayload());
        afterCommit(() -> apply(payload));
        return toResponse(saved);
    }

    @Transactional
    public IntegrationConfigVersionResp createRollbackDraft(Long targetVersionId, Long actorUserId) {
        IntegrationConfigVersion target = repository.findByIdForUpdate(targetVersionId)
            .orElseThrow(() -> new BusinessException("目标配置版本不存在"));
        if (!PUBLISHED.equals(target.getStatus()) && !ARCHIVED.equals(target.getStatus())) {
            throw new BusinessException("只能回滚到已发布过的配置版本");
        }
        IntegrationConfigPayload payload = cryptoService.decrypt(target.getEncryptedPayload());
        IntegrationConfigCryptoService.EncryptedPayload encrypted = cryptoService.encrypt(payload);
        IntegrationConfigVersion rollback = new IntegrationConfigVersion();
        rollback.setVersionNo(nextVersionNo());
        rollback.setStatus(PENDING_REVIEW);
        rollback.setEncryptedPayload(encrypted.ciphertext());
        rollback.setPayloadHash(encrypted.hash());
        rollback.setTestStatus("PASSED");
        rollback.setTestSummary("回滚到已发布版本 v" + target.getVersionNo());
        rollback.setTestedTime(LocalDateTime.now());
        rollback.setCreatedBy(actorUserId);
        rollback.setSubmittedBy(actorUserId);
        rollback.setSubmittedTime(LocalDateTime.now());
        rollback.setPreviousVersionId(repository.findTopByStatusOrderByVersionNoDesc(PUBLISHED).map(IntegrationConfigVersion::getId).orElse(null));
        return toResponse(repository.save(rollback));
    }

    private IntegrationConfigVersion requireStatusForUpdate(Long id, String status) {
        IntegrationConfigVersion version = repository.findByIdForUpdate(id)
            .orElseThrow(() -> new BusinessException("配置版本不存在"));
        if (!status.equals(version.getStatus())) {
            throw new BusinessException("配置版本当前状态不允许执行该操作");
        }
        return version;
    }

    private long nextVersionNo() {
        return repository.findFirstByOrderByVersionNoDesc()
            .map(item -> item.getVersionNo() + 1)
            .orElse(1L);
    }

    private void validate(IntegrationConfigPayload payload, IntegrationConfigPayload current) {
        if (!StringUtils.equalsIgnoreCase(payload.getStorage().getProvider(), current.getStorage().getProvider())) {
            throw new BusinessException("对象存储提供方类型不允许在线切换");
        }
        if (payload.getDingtalk().isEnabled() && !payload.getDingtalk().isMockMode()
            && (StringUtils.isBlank(payload.getDingtalk().getAppKey()) || StringUtils.isBlank(payload.getDingtalk().getAppSecret()))) {
            throw new BusinessException("启用钉钉正式模式时必须填写 AppKey 和 AppSecret");
        }
        if (payload.getHikvision().isEnabled()
            && (StringUtils.isBlank(payload.getHikvision().getBaseUrl())
            || StringUtils.isBlank(payload.getHikvision().getAppKey())
            || StringUtils.isBlank(payload.getHikvision().getAppSecret()))) {
            throw new BusinessException("启用海康时必须填写网关地址、AppKey 和 AppSecret");
        }
        if (StringUtils.isBlank(payload.getStorage().getBucket())) {
            throw new BusinessException("对象存储桶或逻辑目录不能为空");
        }
    }

    private void mergeSecrets(IntegrationConfigPayload target, IntegrationConfigPayload current) {
        target.getDingtalk().setAppSecret(mergeSecret(target.getDingtalk().getAppSecret(), current.getDingtalk().getAppSecret()));
        target.getHikvision().setAppSecret(mergeSecret(target.getHikvision().getAppSecret(), current.getHikvision().getAppSecret()));
        target.getStorage().getMinio().setSecretKey(mergeSecret(target.getStorage().getMinio().getSecretKey(), current.getStorage().getMinio().getSecretKey()));
    }

    private String mergeSecret(String requested, String current) {
        return StringUtils.isBlank(requested) || MASK.equals(requested) ? current : requested;
    }

    private IntegrationConfigPayload currentRaw() {
        return repository.findTopByStatusOrderByVersionNoDesc(PUBLISHED)
            .map(version -> cryptoService.decrypt(version.getEncryptedPayload()))
            .orElseGet(this::fromProperties);
    }

    private IntegrationConfigPayload mask(IntegrationConfigPayload original) {
        IntegrationConfigPayload copy = objectMapper.convertValue(original, IntegrationConfigPayload.class);
        copy.getDingtalk().setAppSecret(maskValue(copy.getDingtalk().getAppSecret()));
        copy.getHikvision().setAppSecret(maskValue(copy.getHikvision().getAppSecret()));
        copy.getStorage().getMinio().setSecretKey(maskValue(copy.getStorage().getMinio().getSecretKey()));
        return copy;
    }

    private String maskValue(String value) {
        return StringUtils.isBlank(value) ? null : MASK;
    }

    private IntegrationConfigPayload fromProperties() {
        IntegrationConfigPayload payload = new IntegrationConfigPayload();
        VisitorProperties.Dingtalk sourceDingtalk = properties.getDingtalk();
        payload.getDingtalk().setEnabled(sourceDingtalk.isEnabled());
        payload.getDingtalk().setMockMode(sourceDingtalk.isMockMode());
        payload.getDingtalk().setAppKey(sourceDingtalk.getAppKey());
        payload.getDingtalk().setAppSecret(sourceDingtalk.getAppSecret());
        payload.getDingtalk().setCorpId(sourceDingtalk.getCorpId());
        payload.getDingtalk().setAgentId(sourceDingtalk.getAgentId());
        payload.getDingtalk().setRootDeptId(sourceDingtalk.getRootDeptId());

        VisitorProperties.Hikvision sourceHikvision = properties.getHikvision();
        payload.getHikvision().setEnabled(sourceHikvision.isEnabled());
        payload.getHikvision().setBaseUrl(sourceHikvision.getBaseUrl());
        payload.getHikvision().setAppKey(sourceHikvision.getAppKey());
        payload.getHikvision().setAppSecret(sourceHikvision.getAppSecret());
        payload.getHikvision().setTagId(sourceHikvision.getTagId());
        payload.getHikvision().setUserId(sourceHikvision.getUserId());
        payload.getHikvision().setOrgIndexCode(sourceHikvision.getOrgIndexCode());
        payload.getHikvision().setPersonAppId(sourceHikvision.getPersonAppId());
        payload.getHikvision().setFaceGroupIndexCode(sourceHikvision.getFaceGroupIndexCode());
        payload.getHikvision().setFaceScoreEnabled(sourceHikvision.isFaceScoreEnabled());
        payload.getHikvision().setConnectTimeoutSeconds(sourceHikvision.getConnectTimeoutSeconds());
        payload.getHikvision().setReadTimeoutSeconds(sourceHikvision.getReadTimeoutSeconds());
        payload.getHikvision().setTrustAll(sourceHikvision.isTrustAll());
        VisitorProperties.Access sourceAccess = sourceHikvision.getAccess();
        payload.getHikvision().getAccess().setEnabled(sourceAccess.isEnabled());
        payload.getHikvision().getAccess().setResourceType(sourceAccess.getResourceType());
        payload.getHikvision().getAccess().setResourceIndexCodes(List.copyOf(sourceAccess.getResourceIndexCodes()));
        payload.getHikvision().getAccess().setAutoDiscoverResources(sourceAccess.isAutoDiscoverResources());
        payload.getHikvision().getAccess().setResourceQueryPath(sourceAccess.getResourceQueryPath());
        payload.getHikvision().getAccess().setResourceQueryType(sourceAccess.getResourceQueryType());
        payload.getHikvision().getAccess().setResourceQueryPageSize(sourceAccess.getResourceQueryPageSize());
        payload.getHikvision().getAccess().setChannelNos(List.copyOf(sourceAccess.getChannelNos()));

        VisitorProperties.Storage sourceStorage = properties.getStorage();
        payload.getStorage().setProvider(sourceStorage.getProvider());
        payload.getStorage().setBucket(sourceStorage.getBucket());
        payload.getStorage().setLocalRoot(sourceStorage.getLocalRoot());
        payload.getStorage().setPreviewBaseUrl(sourceStorage.getPreviewBaseUrl());
        payload.getStorage().getMinio().setEndpoint(sourceStorage.getMinio().getEndpoint());
        payload.getStorage().getMinio().setAccessKey(sourceStorage.getMinio().getAccessKey());
        payload.getStorage().getMinio().setSecretKey(sourceStorage.getMinio().getSecretKey());
        return payload;
    }

    private void apply(IntegrationConfigPayload payload) {
        VisitorProperties.Dingtalk dingtalk = properties.getDingtalk();
        dingtalk.setEnabled(payload.getDingtalk().isEnabled());
        dingtalk.setMockMode(payload.getDingtalk().isMockMode());
        dingtalk.setAppKey(payload.getDingtalk().getAppKey());
        dingtalk.setAppSecret(payload.getDingtalk().getAppSecret());
        dingtalk.setCorpId(payload.getDingtalk().getCorpId());
        dingtalk.setAgentId(payload.getDingtalk().getAgentId());
        dingtalk.setRootDeptId(payload.getDingtalk().getRootDeptId());

        VisitorProperties.Hikvision hikvision = properties.getHikvision();
        hikvision.setEnabled(payload.getHikvision().isEnabled());
        hikvision.setBaseUrl(payload.getHikvision().getBaseUrl());
        hikvision.setAppKey(payload.getHikvision().getAppKey());
        hikvision.setAppSecret(payload.getHikvision().getAppSecret());
        hikvision.setTagId(payload.getHikvision().getTagId());
        hikvision.setUserId(payload.getHikvision().getUserId());
        hikvision.setOrgIndexCode(payload.getHikvision().getOrgIndexCode());
        hikvision.setPersonAppId(payload.getHikvision().getPersonAppId());
        hikvision.setFaceGroupIndexCode(payload.getHikvision().getFaceGroupIndexCode());
        hikvision.setFaceScoreEnabled(payload.getHikvision().isFaceScoreEnabled());
        hikvision.setConnectTimeoutSeconds(payload.getHikvision().getConnectTimeoutSeconds());
        hikvision.setReadTimeoutSeconds(payload.getHikvision().getReadTimeoutSeconds());
        hikvision.setTrustAll(payload.getHikvision().isTrustAll());
        VisitorProperties.Access access = hikvision.getAccess();
        access.setEnabled(payload.getHikvision().getAccess().isEnabled());
        access.setResourceType(payload.getHikvision().getAccess().getResourceType());
        access.setResourceIndexCodes(List.copyOf(payload.getHikvision().getAccess().getResourceIndexCodes()));
        access.setAutoDiscoverResources(payload.getHikvision().getAccess().isAutoDiscoverResources());
        access.setResourceQueryPath(payload.getHikvision().getAccess().getResourceQueryPath());
        access.setResourceQueryType(payload.getHikvision().getAccess().getResourceQueryType());
        access.setResourceQueryPageSize(payload.getHikvision().getAccess().getResourceQueryPageSize());
        access.setChannelNos(List.copyOf(payload.getHikvision().getAccess().getChannelNos()));

        VisitorProperties.Storage storage = properties.getStorage();
        storage.setBucket(payload.getStorage().getBucket());
        storage.setLocalRoot(payload.getStorage().getLocalRoot());
        storage.setPreviewBaseUrl(payload.getStorage().getPreviewBaseUrl());
        storage.getMinio().setEndpoint(payload.getStorage().getMinio().getEndpoint());
        storage.getMinio().setAccessKey(payload.getStorage().getMinio().getAccessKey());
        storage.getMinio().setSecretKey(payload.getStorage().getMinio().getSecretKey());
    }

    private void afterCommit(Runnable runnable) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    private IntegrationConfigVersionResp toResponse(IntegrationConfigVersion version) {
        return IntegrationConfigVersionResp.builder()
            .id(version.getId())
            .versionNo(version.getVersionNo())
            .status(version.getStatus())
            .testStatus(version.getTestStatus())
            .testSummary(version.getTestSummary())
            .testedTime(version.getTestedTime())
            .createdBy(version.getCreatedBy())
            .submittedBy(version.getSubmittedBy())
            .reviewedBy(version.getReviewedBy())
            .submittedTime(version.getSubmittedTime())
            .publishedTime(version.getPublishedTime())
            .createTime(version.getCreateTime())
            .build();
    }
}
