package com.visitor.system.visitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "visitor_upload_file")
public class UploadFileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String fileKey;

    @Column(nullable = false, length = 200)
    private String objectKey;

    @Column(nullable = false, length = 120)
    private String originalFilename;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private boolean linked;

    @Column(length = 36)
    private String linkedBizId;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    public void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
