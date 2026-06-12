package com.fabricmanagement.finance.common.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.finance.common.domain.DocumentNumberCounter;
import com.fabricmanagement.finance.common.infra.DocumentNumberCounterRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceDocumentNumberGeneratorTest {

  @Mock private DocumentNumberCounterRepository counterRepository;

  @InjectMocks private FinanceDocumentNumberGenerator generator;

  @Test
  void nextNumber_initializesAndFormatsProperly() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    DocumentNumberCounter counter = new DocumentNumberCounter(tenantId, "SF", 2026, 0L);
    when(counterRepository.findForUpdate(tenantId, "SF", 2026)).thenReturn(Optional.of(counter));

    // Act
    String number = generator.nextNumber(tenantId, "SF", 2026);

    // Assert
    assertThat(number).isEqualTo("SF-2026-000001");
  }

  @Test
  void nextNumber_incrementsValue() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    DocumentNumberCounter counter = new DocumentNumberCounter(tenantId, "PF", 2026, 41L);
    when(counterRepository.findForUpdate(tenantId, "PF", 2026)).thenReturn(Optional.of(counter));

    // Act
    String number = generator.nextNumber(tenantId, "PF", 2026);

    // Assert
    assertThat(number).isEqualTo("PF-2026-000042");
  }
}
