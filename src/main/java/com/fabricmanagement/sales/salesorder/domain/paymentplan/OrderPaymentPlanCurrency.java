package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "order_payment_plan_currency", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentPlanCurrency extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID revisionId;

  @Column(nullable = false, length = 3, updatable = false)
  private String currency;

  @Column(precision = 19, scale = 4, updatable = false)
  private BigDecimal baseAmount;

  @Type(JsonType.class)
  @Column(columnDefinition = "jsonb", updatable = false)
  private PaymentPlanInput.Rules rules;

  public static OrderPaymentPlanCurrency create(
      UUID revision, PaymentPlanContent.CurrencyGroup group) {
    var value = new OrderPaymentPlanCurrency();
    value.revisionId = revision;
    value.currency = group.currency();
    value.baseAmount = group.baseAmount();
    value.rules = group.rules();
    return value;
  }

  @Override
  protected String getModuleCode() {
    return "SPC";
  }
}
