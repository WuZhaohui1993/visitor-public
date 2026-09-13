package com.visitor.system.admin.service;

import com.visitor.system.admin.domain.AdminAuditLog;
import com.visitor.system.admin.dto.AdminAuditLogResp;
import com.visitor.system.admin.dto.AdminPageResp;
import com.visitor.system.admin.repository.AdminAuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminAuditQueryService {

    private final AdminAuditLogRepository repository;

    public AdminAuditQueryService(AdminAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AdminPageResp<AdminAuditLogResp> list(String keyword, String action, String result,
                                                 LocalDateTime from, LocalDateTime to, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Specification<AdminAuditLog> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.isNotBlank(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("actorUsername")), like),
                    cb.like(cb.lower(root.get("targetId")), like),
                    cb.like(cb.lower(root.get("summary")), like)
                ));
            }
            if (StringUtils.isNotBlank(action)) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (StringUtils.isNotBlank(result)) {
                predicates.add(cb.equal(root.get("result"), result.trim().toUpperCase(Locale.ROOT)));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createTime"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<AdminAuditLog> resultPage = repository.findAll(
            specification,
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createTime"))
        );
        return AdminPageResp.<AdminAuditLogResp>builder()
            .items(resultPage.getContent().stream().map(this::toResponse).toList())
            .page(resultPage.getNumber())
            .size(resultPage.getSize())
            .totalElements(resultPage.getTotalElements())
            .totalPages(resultPage.getTotalPages())
            .build();
    }

    private AdminAuditLogResp toResponse(AdminAuditLog log) {
        return AdminAuditLogResp.builder()
            .id(log.getId())
            .actorUsername(log.getActorUsername())
            .action(log.getAction())
            .targetType(log.getTargetType())
            .targetId(log.getTargetId())
            .result(log.getResult())
            .summary(log.getSummary())
            .ipAddress(log.getIpAddress())
            .createTime(log.getCreateTime())
            .build();
    }
}
