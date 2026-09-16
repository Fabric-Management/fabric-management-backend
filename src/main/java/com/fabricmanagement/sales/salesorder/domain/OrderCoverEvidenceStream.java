package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Evidence identity/revision allocation only; does not create a cover case or enrol an order. */
@Entity
@Table(name = "order_cover_evidence_stream", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCoverEvidenceStream extends BaseEntity {
  @Column(name = "case_id", nullable = false, updatable = false)
  private UUID caseId;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "last_revision", nullable = false)
  private long lastRevision;

  public static OrderCoverEvidenceStream create(UUID tenantId, UUID orderId, UUID caseId) {
    var stream = new OrderCoverEvidenceStream();
    stream.setTenantId(tenantId);
    stream.setUid(stream.generateUid());
    stream.salesOrderId = orderId;
    stream.caseId = caseId;
    return stream;
  }

  public void requireOrder(UUID orderId) {
    if (!salesOrderId.equals(orderId)) {
      throw new OrderDomainException("Evidence identity belongs to another order", 404);
    }
  }

  public long nextRevision() {
    return ++lastRevision;
  }

  @Override
  protected String getModuleCode() {
    return "OCES";
  }
}
