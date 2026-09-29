package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A file received from or about the customer: sample photo, specification or a reply that came in
 * by e-mail or message (SOI IK-12). Stored with the order; the reply itself is the evidence,
 * nothing is sent from here.
 */
@Entity
@Table(name = "intake_attachment", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeAttachment extends BaseEntity {

  public static final long MAX_BYTES = 10L * 1024 * 1024;
  public static final Set<String> ALLOWED_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain");

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "sales_order_id", updatable = false)
  private UUID salesOrderId;

  @Column(name = "request_id")
  private UUID requestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, updatable = false, length = 30)
  private IntakeAttachmentKind kind;

  @Column(name = "file_name", nullable = false, updatable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, updatable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false, updatable = false)
  private long sizeBytes;

  @Column(name = "sha256", nullable = false, updatable = false, length = 64)
  private String sha256;

  @Column(name = "content", nullable = false, updatable = false, columnDefinition = "bytea")
  private byte[] content;

  @Column(name = "uploaded_by", nullable = false, updatable = false)
  private UUID uploadedBy;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private Instant uploadedAt;

  public static IntakeAttachment store(
      UUID customerId,
      UUID salesOrderId,
      UUID requestId,
      IntakeAttachmentKind kind,
      String fileName,
      String contentType,
      byte[] content,
      String sha256,
      UUID uploadedBy,
      Instant uploadedAt) {
    if (customerId == null || kind == null || uploadedBy == null || uploadedAt == null) {
      throw new IllegalArgumentException("Customer, kind, uploader and time are required");
    }
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("The file is empty");
    }
    if (content.length > MAX_BYTES) {
      throw new IllegalArgumentException("Files are limited to 10 MB");
    }
    String type = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    if (!ALLOWED_TYPES.contains(type)) {
      throw new IllegalArgumentException("Only images, PDF and plain text are accepted");
    }
    String name = fileName == null || fileName.isBlank() ? "file" : fileName.trim();
    IntakeAttachment attachment = new IntakeAttachment();
    attachment.customerId = customerId;
    attachment.salesOrderId = salesOrderId;
    attachment.requestId = requestId;
    attachment.kind = kind;
    attachment.fileName = name.length() > 255 ? name.substring(0, 255) : name;
    attachment.contentType = type;
    attachment.sizeBytes = content.length;
    attachment.sha256 = sha256;
    attachment.content = content;
    attachment.uploadedBy = uploadedBy;
    attachment.uploadedAt = uploadedAt;
    return attachment;
  }

  public void linkTo(UUID requestId) {
    if (this.requestId != null && !this.requestId.equals(requestId)) {
      throw new IllegalStateException("The file already belongs to another request");
    }
    this.requestId = requestId;
  }

  @Override
  protected String getModuleCode() {
    return "IAT";
  }
}
