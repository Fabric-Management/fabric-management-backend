package com.fabricmanagement.flowboard.task.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Görevle ilişkilendirilmiş dosya eklentileri (S3/MinIO referansları). */
@Entity
@Table(schema = "flowboard", name = "task_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAttachment extends BaseEntity {

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "file_name", nullable = false, length = 500)
  private String fileName;

  @Column(name = "file_type", nullable = false, length = 50)
  private String fileType; // MIME Type

  @Column(name = "file_size_bytes", nullable = false)
  private long fileSizeBytes;

  @Column(name = "storage_path", nullable = false, length = 1000)
  private String storagePath;

  @Column(name = "uploaded_by_user_id", nullable = false)
  private UUID uploadedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "attachment_type", nullable = false, length = 20)
  private AttachmentType attachmentType;

  @Column(length = 255)
  private String description;

  public TaskAttachment(
      UUID tenantId,
      UUID taskId,
      String fileName,
      String fileType,
      long fileSizeBytes,
      String storagePath,
      UUID uploadedByUserId,
      AttachmentType attachmentType,
      String description) {
    this.setTenantId(tenantId);
    this.taskId = taskId;
    this.fileName = fileName;
    this.fileType = fileType;
    this.fileSizeBytes = fileSizeBytes;
    this.storagePath = storagePath;
    this.uploadedByUserId = uploadedByUserId;
    this.attachmentType = attachmentType;
    this.description = description;
  }

  @Override
  protected String getModuleCode() {
    return "TSK";
  }
}
