package com.fabricmanagement.platform.realtime.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveTestDoubles.RecordingTransactionManager;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;

/** One revision read as the actor (CEDIT-05 §4.5–4.6, L13). */
class LiveTenantScopeTest {

  private final RecordingTransactionManager transactions = new RecordingTransactionManager();
  private final LiveActor actor =
      new LiveActor(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(600));

  @AfterEach
  void clear() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("L13: the actor's tenant is bound before a new, short, read-only transaction")
  void tenantIsBoundBeforeTheTransaction() {
    LiveStreamProperties properties = new LiveStreamProperties();
    properties.setReadTimeout(Duration.ofMillis(1500));
    LiveTenantScope scope = new LiveTenantScope(transactions, properties);

    UUID seen = scope.read(actor, TenantContext::getCurrentTenantIdOrNull);

    assertThat(seen).isEqualTo(actor.tenantId());
    assertThat(transactions.tenantsAtBegin).containsExactly(actor.tenantId());
    TransactionDefinition definition = transactions.definitions.getFirst();
    assertThat(definition.isReadOnly()).isTrue();
    assertThat(definition.getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThat(definition.getIsolationLevel())
        .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
    assertThat(definition.getTimeout()).isEqualTo(2);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  @DisplayName("L13: after a failed read the previous tenant and principal are back, unchanged")
  void failureRestoresThePreviousContext() {
    LiveTenantScope scope = new LiveTenantScope(transactions, new LiveStreamProperties());
    UUID previousTenant = UUID.randomUUID();
    UUID previousUser = UUID.randomUUID();
    TenantContext.setCurrentTenantId(previousTenant);
    TenantContext.setCurrentUserId(previousUser);
    Authentication previous = new UsernamePasswordAuthenticationToken("someone", "n/a");
    SecurityContextHolder.getContext().setAuthentication(previous);

    assertThatThrownBy(
            () ->
                scope.read(
                    actor,
                    () -> {
                      assertThat(TenantContext.getCurrentTenantIdOrNull())
                          .isEqualTo(actor.tenantId());
                      assertThat(TenantContext.getCurrentUserId()).isEqualTo(actor.userId());
                      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                      throw new IllegalStateException("read failed");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(transactions.rollbacks.get()).isEqualTo(1);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(previousTenant);
    assertThat(TenantContext.getCurrentUserId()).isEqualTo(previousUser);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
  }

  @Test
  @DisplayName("L13: a worker without context is left without context")
  void emptyThreadStaysEmpty() {
    LiveTenantScope scope = new LiveTenantScope(transactions, new LiveStreamProperties());

    scope.read(actor, () -> "read");

    assertThat(TenantContext.isSet()).isFalse();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
