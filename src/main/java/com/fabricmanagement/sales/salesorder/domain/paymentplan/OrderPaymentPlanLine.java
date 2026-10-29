package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_payment_plan_line", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentPlanLine extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID currencyGroupId;

  @Column(nullable = false, updatable = false)
  private UUID obligationId;

  @Column(nullable = false, updatable = false)
  private int ordinal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20, updatable = false)
  private Kind kind;

  @Column(precision = 19, scale = 4, updatable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, updatable = false)
  private Method method;

  @Enumerated(EnumType.STRING)
  @Column(length = 30, updatable = false)
  private DateTrigger dateTrigger;

  @Column(updatable = false)
  private LocalDate expectedDate;

  @Column(updatable = false)
  private LocalDate chequePaymentDate;

  @Column(nullable = false, updatable = false)
  private boolean manual;

  public static OrderPaymentPlanLine create(UUID groupId, PaymentPlanContent.Line line) {
    var value = new OrderPaymentPlanLine();
    value.currencyGroupId = groupId;
    value.obligationId = line.obligationId();
    value.ordinal = line.ordinal();
    value.kind = line.kind();
    value.amount = line.amount();
    value.method = line.method();
    value.dateTrigger = line.dateTrigger();
    value.expectedDate = line.expectedDate();
    value.chequePaymentDate = line.chequePaymentDate();
    value.manual = line.manual();
    return value;
  }

  public PaymentPlanContent.Line content() {
    return new PaymentPlanContent.Line(
        obligationId,
        ordinal,
        kind,
        amount,
        method,
        dateTrigger,
        expectedDate,
        chequePaymentDate,
        manual);
  }

  @Override
  protected String getModuleCode() {
    return "SPL";
  }
}
