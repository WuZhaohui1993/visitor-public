package com.visitor.system.admin.service;

import com.visitor.system.admin.dto.AdminDashboardResp;
import com.visitor.system.admin.dto.AdminPageResp;
import com.visitor.system.admin.dto.AdminVisitorRecordResp;
import com.visitor.system.common.BusinessException;
import com.visitor.system.visitor.domain.HikSyncStatus;
import com.visitor.system.visitor.domain.VisitorRecord;
import com.visitor.system.visitor.repository.VisitorRecordRepository;
import com.visitor.system.visitor.service.CryptoService;
import com.visitor.system.visitor.service.HikvisionSyncService;
import com.visitor.system.visitor.service.ObjectStorageService;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminVisitorService {

    private final VisitorRecordRepository recordRepository;
    private final CryptoService cryptoService;
    private final HikvisionSyncService hikvisionSyncService;
    private final ObjectStorageService objectStorageService;

    public AdminVisitorService(VisitorRecordRepository recordRepository,
                               CryptoService cryptoService,
                               HikvisionSyncService hikvisionSyncService,
                               ObjectStorageService objectStorageService) {
        this.recordRepository = recordRepository;
        this.cryptoService = cryptoService;
        this.hikvisionSyncService = hikvisionSyncService;
        this.objectStorageService = objectStorageService;
    }

    @Transactional(readOnly = true)
    public AdminPageResp<AdminVisitorRecordResp> list(String keyword,
                                                      Integer status,
                                                      String hikState,
                                                      LocalDateTime from,
                                                      LocalDateTime to,
                                                      int page,
                                                      int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Page<VisitorRecord> result = recordRepository.findAll(
            specification(keyword, status, hikState, from, to),
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createTime"))
        );
        return AdminPageResp.<AdminVisitorRecordResp>builder()
            .items(result.getContent().stream().map(this::toResponse).toList())
            .page(result.getNumber())
            .size(result.getSize())
            .totalElements(result.getTotalElements())
            .totalPages(result.getTotalPages())
            .build();
    }

    @Transactional(readOnly = true)
    public List<AdminVisitorRecordResp> export(String keyword,
                                               Integer status,
                                               String hikState,
                                               LocalDateTime from,
                                               LocalDateTime to) {
        Page<VisitorRecord> result = recordRepository.findAll(
            specification(keyword, status, hikState, from, to),
            PageRequest.of(0, 10_001, Sort.by(Sort.Direction.DESC, "createTime"))
        );
        if (result.getTotalElements() > 10_000) {
            throw new BusinessException("单次最多导出10000条，请缩小筛选范围");
        }
        return result.getContent().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminVisitorRecordResp detail(String bizId) {
        return toResponse(requireRecord(bizId));
    }

    @Transactional(readOnly = true)
    public AdminDashboardResp dashboard() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime today = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1);
        List<VisitorRecord> todayRecords = recordRepository.findAll(specification(null, null, null, today, tomorrow));
        List<VisitorRecord> pendingRecords = recordRepository.findAll((root, query, cb) -> cb.and(
            cb.equal(root.get("status"), 0),
            cb.greaterThan(root.get("expireTime"), now)
        ));
        List<VisitorRecord> activeRecords = recordRepository.findAll((root, query, cb) -> cb.and(
            cb.equal(root.get("status"), 1),
            cb.lessThanOrEqualTo(root.get("plannedEntryTime"), now),
            cb.greaterThan(root.get("plannedExitTime"), now)
        ));
        List<VisitorRecord> failures = recordRepository.findAll((root, query, cb) -> cb.or(
            cb.equal(root.get("hikSyncStatus"), HikSyncStatus.FAILED),
            cb.equal(root.get("hikAccessStatus"), HikSyncStatus.FAILED)
        ));

        LocalDate firstDate = LocalDate.now().minusDays(6);
        List<VisitorRecord> trendRecords = recordRepository.findAll(
            specification(null, null, null, firstDate.atStartOfDay(), tomorrow)
        );
        Map<LocalDate, List<VisitorRecord>> grouped = new LinkedHashMap<>();
        for (int day = 0; day < 7; day++) {
            grouped.put(firstDate.plusDays(day), new ArrayList<>());
        }
        trendRecords.forEach(record -> grouped.computeIfAbsent(record.getCreateTime().toLocalDate(), key -> new ArrayList<>()).add(record));
        List<AdminDashboardResp.DailyTrend> trend = grouped.entrySet().stream().map(entry ->
            AdminDashboardResp.DailyTrend.builder()
                .date(entry.getKey())
                .total(entry.getValue().size())
                .approved(entry.getValue().stream().filter(record -> record.getStatus() == 1).count())
                .rejected(entry.getValue().stream().filter(record -> record.getStatus() == 2).count())
                .build()
        ).toList();

        return AdminDashboardResp.builder()
            .todayTotal(todayRecords.size())
            .pending(pendingRecords.size())
            .approvedToday(todayRecords.stream().filter(record -> record.getStatus() == 1).count())
            .rejectedToday(todayRecords.stream().filter(record -> record.getStatus() == 2).count())
            .activeVisitors(activeRecords.size())
            .integrationFailures(failures.size())
            .trend(trend)
            .build();
    }

    @Transactional
    public AdminVisitorRecordResp retryHikvision(String bizId) {
        VisitorRecord record = requireRecord(bizId);
        requireApprovedAndActive(record);
        if (record.getHikSyncStatus() == HikSyncStatus.DISABLED) {
            throw new BusinessException("海康身份已回收，不允许重新下发");
        }
        record.setHikSyncStatus(HikSyncStatus.PENDING);
        record.setHikSyncError(null);
        recordRepository.save(record);
        afterCommit(() -> hikvisionSyncService.syncApprovedRecordAsync(record.getId()));
        return toResponse(record);
    }

    @Transactional
    public AdminVisitorRecordResp retryAccess(String bizId) {
        VisitorRecord record = requireRecord(bizId);
        requireApprovedAndActive(record);
        if (record.getHikSyncStatus() != HikSyncStatus.SUCCESS) {
            throw new BusinessException("海康人员和人脸尚未同步成功，不能单独重试门禁权限");
        }
        if (record.getHikAccessStatus() == HikSyncStatus.DISABLED) {
            throw new BusinessException("门禁权限已回收，不允许重新下发");
        }
        record.setHikAccessStatus(HikSyncStatus.PENDING);
        record.setHikAccessError(null);
        recordRepository.save(record);
        afterCommit(() -> hikvisionSyncService.syncAccess(record.getId()));
        return toResponse(record);
    }

    @Transactional
    public AdminVisitorRecordResp revokeAccess(String bizId) {
        VisitorRecord record = requireRecord(bizId);
        if (record.getHikSyncStatus() == HikSyncStatus.DISABLED) {
            throw new BusinessException("该访客权限已经回收");
        }
        if (record.getStatus() != 1 || record.getHikSyncStatus() != HikSyncStatus.SUCCESS) {
            throw new BusinessException("只有已成功下发海康的访客可以回收权限");
        }
        afterCommit(() -> hikvisionSyncService.disableExpiredRecord(record.getId()));
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public Attachment openAttachment(String bizId, String type) {
        VisitorRecord record = requireRecord(bizId);
        String objectKey = switch (type.toLowerCase(Locale.ROOT)) {
            case "id-card-front" -> record.getIdCardFront();
            case "id-card-back" -> record.getIdCardBack();
            case "face-photo" -> record.getFacePhoto();
            default -> throw new BusinessException("不支持的附件类型");
        };
        if (StringUtils.isBlank(objectKey)) {
            throw new BusinessException("附件不存在");
        }
        InputStream stream = objectStorageService.openStream(objectKey);
        String contentType = objectKey.toLowerCase(Locale.ROOT).endsWith(".png") ? "image/png" : "image/jpeg";
        return new Attachment(new InputStreamResource(stream), contentType);
    }

    private Specification<VisitorRecord> specification(String keyword,
                                                        Integer status,
                                                        String hikState,
                                                        LocalDateTime from,
                                                        LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.isNotBlank(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("recordNo")), like),
                    cb.like(cb.lower(root.get("visitorName")), like),
                    cb.like(cb.lower(root.get("phone")), like),
                    cb.like(cb.lower(root.get("visitedUserName")), like),
                    cb.like(cb.lower(root.get("visitedDeptName")), like)
                ));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.isNotBlank(hikState)) {
                HikSyncStatus state;
                try {
                    state = HikSyncStatus.valueOf(hikState.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new BusinessException("海康状态筛选值不正确");
                }
                predicates.add(cb.or(
                    cb.equal(root.get("hikSyncStatus"), state),
                    cb.equal(root.get("hikAccessStatus"), state)
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createTime"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private VisitorRecord requireRecord(String bizId) {
        return recordRepository.findByBizId(bizId)
            .orElseThrow(() -> new BusinessException("访客记录不存在"));
    }

    private void requireApprovedAndActive(VisitorRecord record) {
        if (record.getStatus() != 1) {
            throw new BusinessException("只有审批通过的记录可以执行同步操作");
        }
        if (!record.getPlannedExitTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("访客记录已过期，不允许重新下发权限");
        }
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private AdminVisitorRecordResp toResponse(VisitorRecord record) {
        boolean expired = LocalDateTime.now().isAfter(record.getExpireTime());
        return AdminVisitorRecordResp.builder()
            .bizId(record.getBizId())
            .recordNo(record.getRecordNo())
            .visitorName(record.getVisitorName())
            .idCardNo(cryptoService.decrypt(record.getIdCardNo()))
            .phone(record.getPhone())
            .visitedUserId(record.getVisitedUserId())
            .visitedUserName(record.getVisitedUserName())
            .visitedDeptName(record.getVisitedDeptName())
            .visitReason(record.getVisitReason())
            .plannedEntryTime(record.getPlannedEntryTime())
            .plannedExitTime(record.getPlannedExitTime())
            .status(record.getStatus())
            .statusText(statusText(record.getStatus(), expired))
            .hikSyncStatus(enumName(record.getHikSyncStatus()))
            .hikSyncError(record.getHikSyncError())
            .hikSyncTime(record.getHikSyncTime())
            .hikRetryCount(record.getHikRetryCount())
            .hikAccessStatus(enumName(record.getHikAccessStatus()))
            .hikAccessError(record.getHikAccessError())
            .hikAccessSyncTime(record.getHikAccessSyncTime())
            .hikAccessRetryCount(record.getHikAccessRetryCount())
            .approveRemark(record.getApproveRemark())
            .approveTime(record.getApproveTime())
            .createTime(record.getCreateTime())
            .expired(expired)
            .hasIdCardFront(StringUtils.isNotBlank(record.getIdCardFront()))
            .hasIdCardBack(StringUtils.isNotBlank(record.getIdCardBack()))
            .hasFacePhoto(StringUtils.isNotBlank(record.getFacePhoto()))
            .build();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String statusText(Integer status, boolean expired) {
        if (status == 0 && expired) {
            return "已过期";
        }
        return switch (status) {
            case 1 -> "审批通过";
            case 2 -> "审批拒绝";
            default -> "等待审批";
        };
    }

    public record Attachment(InputStreamResource resource, String contentType) {
    }
}
