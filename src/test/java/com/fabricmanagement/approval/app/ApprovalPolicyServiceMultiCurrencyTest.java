package com.fabricmanagement.approval.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import com.fabricmanagement.approval.infra.repository.ApprovalPolicyRepository;
import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** A policy's threshold is in its own currency; a multi-currency order is measured against it. */
class ApprovalPolicyServiceMultiCurrencyTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

  private final ApprovalPolicyRepository repository = mock(ApprovalPolicyRepository.class);
  private final ExchangeRateService rates = mock(ExchangeRateService.class);
  private ApprovalPolicyService service;
  private ApprovalPolicy tryPolicy;

  @BeforeEach
  void setUp() {
    service =
        new ApprovalPolicyService(
            repository, rates, Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC));
    tryPolicy =
        new ApprovalPolicy(
            TENANT,
            ApprovalEntityType.SALES_ORDER,
            PolicyTargetLevel.ALL,
            ApproverRole.values()[0],
            10,
            48);
    ReflectionTestUtils.setField(tryPolicy, "minAmountThreshold", new BigDecimal("10000"));
    ReflectionTestUtils.setField(tryPolicy, "currency", "TRY");
    when(repository.findActivePoliciesForEntity(TENANT, ApprovalEntityType.SALES_ORDER))
        .thenReturn(List.of(tryPolicy));
    when(rates.convert(eq(TENANT), any(BigDecimal.class), eq("TRY"), eq("TRY"), eq(TODAY)))
        .thenAnswer(call -> ConvertedMoney.sameUnit(call.getArgument(1), "TRY"));
    rate("USD", "33");
  }

  @Test
  void aTryThresholdGovernsAnOrderPricedInDollars() {
    assertThat(service.getActivePolicyFor(TENANT, ApprovalEntityType.SALES_ORDER, usd("1000")))
        .contains(tryPolicy);
    assertThat(service.getActivePolicyFor(TENANT, ApprovalEntityType.SALES_ORDER, usd("100")))
        .isEmpty();
  }

  @Test
  void theCurrenciesOfOneOrderAreConvertedAndSummedAgainstTheThreshold() {
    // 6,000 TRY + 200 USD (6,600 TRY) = 12,600 TRY: each part alone stays under 10,000.
    List<Money> amounts =
        List.of(Money.of(new BigDecimal("6000"), "TRY"), Money.of(new BigDecimal("200"), "USD"));

    assertThat(service.getActivePolicyFor(TENANT, ApprovalEntityType.SALES_ORDER, amounts))
        .contains(tryPolicy);
  }

  @Test
  void aMissingRateKeepsThePolicyApplying() {
    when(rates.convert(eq(TENANT), any(BigDecimal.class), eq("EUR"), eq("TRY"), eq(TODAY)))
        .thenThrow(new ExchangeRateRequiredException("EUR", "TRY", TODAY));

    assertThat(
            service.getActivePolicyFor(
                TENANT,
                ApprovalEntityType.SALES_ORDER,
                List.of(Money.of(new BigDecimal("1"), "EUR"))))
        .contains(tryPolicy);
  }

  private void rate(String from, String rate) {
    when(rates.convert(eq(TENANT), any(BigDecimal.class), eq(from), eq("TRY"), eq(TODAY)))
        .thenAnswer(
            call -> {
              BigDecimal amount = call.getArgument(1);
              return ConvertedMoney.of(
                  amount,
                  from,
                  amount.multiply(new BigDecimal(rate)),
                  "TRY",
                  new BigDecimal(rate),
                  TODAY);
            });
  }

  private static List<Money> usd(String amount) {
    return List.of(Money.of(new BigDecimal(amount), "USD"));
  }
}
