package com.fabricmanagement.finance.common.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import com.fabricmanagement.finance.common.domain.DocumentNumberCounter;
import com.fabricmanagement.finance.common.domain.DocumentNumberCounterKey;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class DocumentNumberCounterIT extends AbstractIntegrationTest {

  @Autowired private FinanceDocumentNumberGenerator generator;
  @Autowired private DocumentNumberCounterRepository repository;

  @Test
  void nextNumber_initializesCounterIfMissing() {
    UUID tenantId = UUID.randomUUID();
    String number = generator.nextNumber(tenantId, "PAY", 2026);
    assertThat(number).isEqualTo("PAY-2026-000001");

    // Verify it was saved
    Optional<DocumentNumberCounter> counter =
        repository.findById(new DocumentNumberCounterKey(tenantId, "PAY", 2026));
    assertThat(counter).isPresent();
    assertThat(counter.get().getLastValue()).isEqualTo(1L);
  }

  @Test
  void nextNumber_incrementsExistingCounter() {
    UUID tenantId = UUID.randomUUID();
    generator.nextNumber(tenantId, "SF", 2026); // returns 1
    String number = generator.nextNumber(tenantId, "SF", 2026);
    assertThat(number).isEqualTo("SF-2026-000002");
  }
}
