package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.IntakeAttachment;
import com.fabricmanagement.sales.orderintake.domain.IntakeAttachmentKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IntakeAttachmentRepository extends JpaRepository<IntakeAttachment, UUID> {

  /** Metadata only; the file content is read on download. */
  interface Summary {
    UUID getId();

    UUID getCustomerId();

    UUID getSalesOrderId();

    UUID getRequestId();

    IntakeAttachmentKind getKind();

    String getFileName();

    String getContentType();

    long getSizeBytes();

    UUID getUploadedBy();

    Instant getUploadedAt();
  }

  @Query(
      "SELECT a.id AS id, a.customerId AS customerId, a.salesOrderId AS salesOrderId,"
          + " a.requestId AS requestId, a.kind AS kind, a.fileName AS fileName,"
          + " a.contentType AS contentType, a.sizeBytes AS sizeBytes,"
          + " a.uploadedBy AS uploadedBy, a.uploadedAt AS uploadedAt"
          + " FROM IntakeAttachment a WHERE a.tenantId = :tenantId AND a.salesOrderId = :orderId"
          + " AND a.isActive = true ORDER BY a.uploadedAt, a.id")
  List<Summary> summariesForOrder(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);

  @Query(
      "SELECT a.id AS id, a.customerId AS customerId, a.salesOrderId AS salesOrderId,"
          + " a.requestId AS requestId, a.kind AS kind, a.fileName AS fileName,"
          + " a.contentType AS contentType, a.sizeBytes AS sizeBytes,"
          + " a.uploadedBy AS uploadedBy, a.uploadedAt AS uploadedAt"
          + " FROM IntakeAttachment a WHERE a.tenantId = :tenantId AND a.requestId = :requestId"
          + " AND a.isActive = true ORDER BY a.uploadedAt, a.id")
  List<Summary> summariesForRequest(
      @Param("tenantId") UUID tenantId, @Param("requestId") UUID requestId);

  Optional<IntakeAttachment> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  boolean existsByTenantIdAndRequestIdAndIsActiveTrue(UUID tenantId, UUID requestId);
}
