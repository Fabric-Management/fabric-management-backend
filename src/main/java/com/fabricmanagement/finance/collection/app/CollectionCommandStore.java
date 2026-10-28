package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.CollectionCommandReceiptRepository;
import com.fabricmanagement.finance.common.app.FinancePostingDates;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectionCommandStore {
  private final PaymentRepository payments;
  private final CollectionCommandReceiptRepository receipts;
  private final jakarta.validation.Validator validator;
  private final Clock clock;
  private final FinancePostingDates dates;

  public void lock() {
    payments.lockFinanceTenant(TenantContext.requireTenantId());
  }

  /** The tenant lock if it is free this instant; {@code false} when a writer holds it. */
  public boolean tryLock() {
    return payments.tryLockFinanceTenant(TenantContext.requireTenantId());
  }

  public void validate(Object input) {
    if (!validator.validate(input).isEmpty())
      throw CollectionRuleException.invalid("Invalid collection command");
  }

  public String hash(String action, UUID target, Object body) {
    return CanonicalJsonFingerprint.of(new Fingerprint(action, target, body));
  }

  public Optional<CollectionCommandReceipt> replay(UUID operation, UUID actor, String hash) {
    return receipts
        .findByTenantIdAndOperationId(TenantContext.requireTenantId(), operation)
        .map(
            receipt -> {
              receipt.requireReplay(actor, hash);
              return receipt;
            });
  }

  public CollectionCommandReceipt record(
      UUID operation,
      UUID actor,
      String hash,
      String type,
      UUID resource,
      long version,
      UUID payment,
      List<UUID> matches) {
    return receipts.saveAndFlush(
        CollectionCommandReceipt.create(
            operation, actor, hash, type, resource, version, payment, matches));
  }

  public void version(Long expected, Long current) {
    if (expected == null || !expected.equals(current))
      throw CollectionRuleException.conflict(
              "COLLECTION_VERSION_CONFLICT",
              "The collection resource changed; reload before starting another command")
          .withDetail("currentVersion", current);
  }

  public Instant now() {
    return clock.instant();
  }

  public void time(LocalDate effective, Instant occurred) {
    if (effective == null || occurred == null || occurred.isAfter(clock.instant()))
      throw CollectionRuleException.invalid(
          "Actual collection evidence requires a valid date and an occurrence no later than now");
    if (effective.isAfter(dates.today()))
      throw CollectionRuleException.invalid(
          "An actual posting cannot have a future effective date");
  }

  private record Fingerprint(String action, UUID target, Object body) {}
}
