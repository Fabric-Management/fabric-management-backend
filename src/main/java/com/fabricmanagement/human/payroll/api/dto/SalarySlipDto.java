package com.fabricmanagement.human.payroll.api.dto;

import com.fabricmanagement.human.payroll.domain.PayRunPayout;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SalarySlipDto {
  private UUID payoutId;
  private UUID payRunId;
  private String payPeriodName;
  private LocalDate periodStartDate;
  private LocalDate periodEndDate;
  private BigDecimal netAmount;
  private String currency;
  private String status;
  private Instant processedAt;

  public static SalarySlipDto from(PayRunPayout payout) {
    if (payout == null) {
      return null;
    }

    var payRun = payout.getPayRun();
    var payPeriod = payRun != null ? payRun.getPayPeriod() : null;

    return SalarySlipDto.builder()
        .payoutId(payout.getId())
        .payRunId(payRun != null ? payRun.getId() : null)
        .payPeriodName(payPeriod != null ? payPeriod.getPeriodCode() : null)
        .periodStartDate(payPeriod != null ? payPeriod.getStartDate() : null)
        .periodEndDate(payPeriod != null ? payPeriod.getEndDate() : null)
        .netAmount(payout.getNetAmount())
        .currency(payout.getCurrency())
        .status(payout.getStatus())
        .processedAt(payout.getProcessedAt())
        .build();
  }
}
