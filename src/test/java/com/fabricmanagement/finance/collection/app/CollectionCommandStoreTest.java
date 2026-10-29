package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionCommandReceiptRepository;
import com.fabricmanagement.finance.common.app.FinancePostingDates;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import jakarta.validation.Validation;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CollectionCommandStoreTest {
  @Test
  void checksVersionsTimesAndCanonicalReplayWithoutRunningTheApplication() {
    UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), operation = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    var repository = mock(CollectionCommandReceiptRepository.class);
    var clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
    try (var validation = Validation.buildDefaultValidatorFactory()) {
      var service =
          new CollectionCommandStore(
              mock(PaymentRepository.class),
              repository,
              validation.getValidator(),
              clock,
              new FinancePostingDates(clock, () -> ZoneId.of("Europe/London")));
      assertThat(service.hash("MATCH", operation, Map.of("amount", "10.00", "currency", "GBP")))
          .isEqualTo(
              service.hash(
                  "MATCH",
                  operation,
                  new LinkedHashMap<>(Map.of("currency", "GBP", "amount", "10.00"))));
      assertThatThrownBy(() -> service.version(1L, 2L)).isInstanceOf(CollectionRuleException.class);
      assertThatThrownBy(
              () -> service.time(LocalDate.of(2026, 10, 23), Instant.parse("2026-10-22T10:00:00Z")))
          .isInstanceOf(CollectionRuleException.class);
      var receipt =
          CollectionCommandReceipt.create(
              operation, actor, "hash", "OBLIGATION", UUID.randomUUID(), 3, null, List.of());
      when(repository.findByTenantIdAndOperationId(tenant, operation))
          .thenReturn(Optional.of(receipt));
      assertThat(service.replay(operation, actor, "hash")).contains(receipt);
      assertThatThrownBy(() -> service.replay(operation, actor, "changed"))
          .isInstanceOf(CollectionRuleException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void businessDayUsesTheTenantZoneAcrossBritishSummerTimeMidnight() {
    var clock = Clock.fixed(Instant.parse("2026-10-22T23:30:00Z"), ZoneOffset.UTC);
    try (var validation = Validation.buildDefaultValidatorFactory()) {
      var service =
          new CollectionCommandStore(
              mock(PaymentRepository.class),
              mock(CollectionCommandReceiptRepository.class),
              validation.getValidator(),
              clock,
              new FinancePostingDates(clock, () -> ZoneId.of("Europe/London")));
      service.time(LocalDate.of(2026, 10, 23), Instant.parse("2026-10-22T23:15:00Z"));
      assertThatThrownBy(
              () -> service.time(LocalDate.of(2026, 10, 24), Instant.parse("2026-10-22T23:15:00Z")))
          .isInstanceOf(CollectionRuleException.class);
    }
  }

  @Test
  void writersWaitForTheTenantLockWhileAReaderOnlyTriesItAndMovesOn() {
    UUID tenant = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    var payments = mock(PaymentRepository.class);
    var clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
    when(payments.tryLockFinanceTenant(tenant)).thenReturn(false, true);
    try {
      var store =
          new CollectionCommandStore(
              payments,
              mock(CollectionCommandReceiptRepository.class),
              mock(jakarta.validation.Validator.class),
              clock,
              new FinancePostingDates(clock, () -> ZoneId.of("Europe/London")));

      assertThat(store.tryLock()).isFalse();
      assertThat(store.tryLock()).isTrue();
      verify(payments, never()).lockFinanceTenant(any());

      store.lock();
      verify(payments).lockFinanceTenant(tenant);
    } finally {
      TenantContext.clear();
    }
  }
}
