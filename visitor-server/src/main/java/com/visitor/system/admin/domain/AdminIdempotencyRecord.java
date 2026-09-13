package com.visitor.system.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "admin_idempotency_record",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_admin_idempotency_actor_key",
        columnNames = {"actorUserId", "idempotencyKeyHash"}
    ),
    indexes = @Index(name = "idx_admin_idempotency_created", columnList = "createTime")
)
public class AdminIdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 64)
    private String idempotencyKeyHash;

    @Column(nullable = false, length = 160)
    private String requestPath;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    void prePersist() {
        createTime = LocalDateTime.now();
    }
}
