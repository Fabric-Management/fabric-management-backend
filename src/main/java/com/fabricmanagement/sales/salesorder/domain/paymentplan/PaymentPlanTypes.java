package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import io.swagger.v3.oas.annotations.media.Schema;

public final class PaymentPlanTypes {
  private PaymentPlanTypes() {}

  @Schema(name = "SalesPaymentPlanState", enumAsRef = true)
  public enum State {
    DRAFT,
    PROPOSED,
    AGREED,
    SUPERSEDED,
    CANCELLED
  }

  @Schema(name = "SalesPaymentPlanMethod", enumAsRef = true)
  public enum Method {
    CASH,
    BANK_TRANSFER,
    CHEQUE,
    CREDIT_CARD,
    OTHER
  }

  @Schema(name = "SalesPaymentPlanFrequency", enumAsRef = true)
  public enum Frequency {
    DAY,
    WEEK,
    MONTH
  }

  @Schema(name = "SalesPaymentPlanOrderTermUnit", enumAsRef = true)
  public enum OrderTermUnit {
    DAY,
    MONTH
  }

  @Schema(name = "SalesPaymentPlanLineKind", enumAsRef = true)
  public enum Kind {
    DOWN_PAYMENT,
    INSTALMENT
  }

  @Schema(name = "SalesPaymentPlanDateTrigger", enumAsRef = true)
  public enum DateTrigger {
    EXPLICIT_DATE,
    ON_ORDER_CONFIRMATION
  }

  @Schema(name = "SalesPaymentPlanChequeDateMode", enumAsRef = true)
  public enum ChequeDateMode {
    COMMON_DATE,
    PER_ROW_DATE
  }
}
