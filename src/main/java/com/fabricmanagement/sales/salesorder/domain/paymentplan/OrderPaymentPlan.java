package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_payment_plan", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentPlan extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID salesOrderId;

  private UUID currentDraftRevisionId;
  private UUID activeAgreedRevisionId;
  private java.time.LocalDate confirmationDate;

  @Column(nullable = false)
  private int lastRevisionNo;

  @Column(nullable = false)
  private boolean cancelled;

  public static OrderPaymentPlan create(UUID orderId) {
    var value = new OrderPaymentPlan();
    value.salesOrderId = orderId;
    return value;
  }

  public int nextRevision() {
    return ++lastRevisionNo;
  }

  public void draft(UUID revisionId) {
    currentDraftRevisionId = revisionId;
  }

  public void agree(UUID revisionId) {
    activeAgreedRevisionId = revisionId;
  }

  public void confirmOn(java.time.LocalDate date) {
    if (confirmationDate == null) confirmationDate = date;
  }

  public void cancel() {
    cancelled = true;
  }

  @Override
  protected String getModuleCode() {
    return "SPP";
  }
}
