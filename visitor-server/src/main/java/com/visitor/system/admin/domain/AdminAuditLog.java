package com.visitor.system.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "admin_audit_log",
    indexes = {
        @Index(name = "idx_admin_audit_actor_time", columnList = "actorUserId, createTime"),
        @Index(name = "idx_admin_audit_action_time", columnList = "action, createTime")
    }
)
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorUserId;

    @Column(length = 40)
    private String actorUsername;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(length = 40)
    private String targetType;

    @Column(length = 100)
    private String targetId;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(length = 500)
    private String summary;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 300)
    private String userAgent;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    void prePersist() {
        createTime = LocalDateTime.now();
    }
}
