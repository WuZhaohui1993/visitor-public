package com.visitor.system.visitor.service;

import com.visitor.system.common.BusinessException;
import com.visitor.system.visitor.domain.UploadFileRecord;
import com.visitor.system.visitor.dto.UploadScene;
import com.visitor.system.visitor.dto.UploadResp;
import com.visitor.system.visitor.repository.UploadFileRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UploadFileService {

    private final FileValidationService fileValidationService;
    private final HikvisionFaceScoreService hikvisionFaceScoreService;
    private final ObjectStorageService objectStorageService;
    private final UploadFileRecordRepository uploadFileRecordRepository;
    private final VisitorSecurityTokenService visitorSecurityTokenService;

    public UploadFileService(FileValidationService fileValidationService,
                             HikvisionFaceScoreService hikvisionFaceScoreService,
                             ObjectStorageService objectStorageService,
                             UploadFileRecordRepository uploadFileRecordRepository,
                             VisitorSecurityTokenService visitorSecurityTokenService) {
        this.fileValidationService = fileValidationService;
        this.hikvisionFaceScoreService = hikvisionFaceScoreService;
        this.objectStorageService = objectStorageService;
        this.uploadFileRecordRepository = uploadFileRecordRepository;
        this.visitorSecurityTokenService = visitorSecurityTokenService;
    }

    @Transactional
    public UploadResp upload(MultipartFile file) {
        return upload(file, UploadScene.GENERAL);
    }

    @Transactional
    public UploadResp upload(MultipartFile file, UploadScene scene) {
        FileValidationService.ValidatedFile validatedFile = fileValidationService.validate(file, scene);
        if (scene == UploadScene.FACE) {
            // 人脸照片上传阶段先做海康评分，审批后再下发人员/人脸/权限，避免把低质量照片带入后续链路。
            hikvisionFaceScoreService.validateFaceImage(validatedFile.bytes());
        }
        byte[] bytes = validatedFile.bytes();
        String extension = validatedFile.extension();
        String fileKey = UUID.randomUUID().toString().replace("-", "");
        String objectKey = "temp/" + LocalDateTime.now().toLocalDate() + "/" + fileKey + extension;
        objectStorageService.uploadTemp(bytes, objectKey, validatedFile.contentType());

        UploadFileRecord record = new UploadFileRecord();
        record.setFileKey(fileKey);
        record.setObjectKey(objectKey);
        record.setOriginalFilename(file.getOriginalFilename() == null ? "upload" + extension : file.getOriginalFilename());
        record.setContentType(validatedFile.contentType());
        record.setSize((long) bytes.length);
        record.setLinked(false);
        uploadFileRecordRepository.save(record);

        return UploadResp.builder()
            .fileKey(fileKey)
            .objectKey(objectKey)
            .previewUrl(visitorSecurityTokenService.buildTempFilePreviewUrl(objectKey))
            .build();
    }

    public UploadFileRecord mustGet(String fileKey) {
        return uploadFileRecordRepository.findByFileKey(fileKey)
            .orElseThrow(() -> new BusinessException("上传文件不存在或已失效"));
    }

    @Transactional
    public String linkToBiz(String fileKey, String bizId, String suffixName) {
        UploadFileRecord record = mustGet(fileKey);
        if (record.isLinked()) {
            throw new BusinessException("上传文件已被使用，请重新上传");
        }
        String extension = record.getObjectKey().contains(".") ? record.getObjectKey().substring(record.getObjectKey().lastIndexOf(".")) : "";
        String targetObjectKey = "record/" + bizId + "/" + suffixName + extension;
        String storedKey = objectStorageService.moveToBizPath(record.getObjectKey(), targetObjectKey);
        record.setLinked(true);
        record.setLinkedBizId(bizId);
        record.setObjectKey(storedKey);
        uploadFileRecordRepository.save(record);
        return storedKey;
    }
}
