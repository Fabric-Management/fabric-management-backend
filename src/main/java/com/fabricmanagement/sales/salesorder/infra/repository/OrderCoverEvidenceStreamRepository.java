package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidenceStream;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderCoverEvidenceStreamRepository
    extends JpaRepository<OrderCoverEvidenceStream, UUID> {
  /**
   * Establish identity before reading requirements; a concurrent insert is retried in a fresh RR
   * transaction.
   */
  @Modifying
  @Query(
      value =
"""
INSERT INTO sales_ord.order_cover_evidence_stream
  (id, tenant_id, uid, created_at, created_by, updated_at, updated_by,
   is_active, version, case_id, sales_order_id, last_revision)
SELECT :id, :tenantId, :uid, CURRENT_TIMESTAMP, :actorId, CURRENT_TIMESTAMP, :actorId,
       TRUE, 0, :caseId, o.id, 0
FROM sales_ord.sales_order o WHERE o.tenant_id = :tenantId AND o.id = :orderId
ON CONFLICT (tenant_id, case_id) DO NOTHING
""",
      nativeQuery = true)
  int establishScope(
      @Param("id") UUID id,
      @Param("tenantId") UUID tenantId,
      @Param("orderId") UUID orderId,
      @Param("caseId") UUID caseId,
      @Param("uid") String uid,
      @Param("actorId") UUID actorId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from OrderCoverEvidenceStream s where s.tenantId = :tenantId and s.caseId ="
          + " :caseId")
  Optional<OrderCoverEvidenceStream> lockScope(
      @Param("tenantId") UUID tenantId, @Param("caseId") UUID caseId);

  List<OrderCoverEvidenceStream> findByTenantIdAndSalesOrderIdOrderByCaseId(
      UUID tenantId, UUID salesOrderId);

  Page<OrderCoverEvidenceStream> findByTenantId(UUID tenantId, Pageable pageable);
}
