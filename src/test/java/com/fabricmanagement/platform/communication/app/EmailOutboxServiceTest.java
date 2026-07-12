package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.communication.domain.EmailOutbox;
import com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.platform.communication.infra.repository.EmailOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailOutboxServiceTest {

  @Mock private EmailOutboxRepository emailOutboxRepository;
  @Mock private EmailStrategy emailStrategy;
  @Mock private EmailRecipientPolicy emailRecipientPolicy;
  @Mock private SystemTransactionExecutor systemTransactionExecutor;
  @Mock private MeterRegistry meterRegistry;

  private EmailOutboxService service;

  @BeforeEach
  void setUp() {
    service =
        new EmailOutboxService(
            emailOutboxRepository,
            emailStrategy,
            emailRecipientPolicy,
            systemTransactionExecutor,
            meterRegistry);
  }

  @Test
  void queueSystemEmailBypassesRecipientPolicyAndKeepsFixedRecipient() {
    UUID tenantId = UUID.randomUUID();
    String recipient = "fixed-ops@example.test";
    when(emailOutboxRepository.save(any(EmailOutbox.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    EmailOutbox saved =
        service.queueSystemEmail(tenantId, recipient, "Ops report", "<p>Details</p>");

    assertThat(saved.getTenantId()).isEqualTo(tenantId);
    assertThat(saved.getRecipient()).isEqualTo(recipient);
    assertThat(saved.getOriginalRecipient()).isNull();
    assertThat(saved.getSubject()).isEqualTo("Ops report");
    verify(emailOutboxRepository).save(saved);
    verifyNoInteractions(emailRecipientPolicy);
  }
}
