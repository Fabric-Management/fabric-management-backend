package com.fabricmanagement.finance.collection.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectionSettlementAdapterTest {
  final UUID tenant = UUID.randomUUID(), order = UUID.randomUUID(), plan = UUID.randomUUID();
  CollectionObligationRepository obligations;
  CollectionPaymentMatchRepository matches;
  CollectionSettlementAdapter adapter;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    obligations = mock(CollectionObligationRepository.class);
    matches = mock(CollectionPaymentMatchRepository.class);
    adapter = new CollectionSettlementAdapter(obligations, matches);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  CollectionObligation obligation(UUID rowId, UUID logicalId) {
    var value = CollectionObligation.create(order, plan, logicalId, UUID.randomUUID(), "GBP");
    value.setId(rowId);
    return value;
  }

  CollectionPaymentMatchRepository.Balance balance(UUID obligationId, String amount) {
    var value = mock(CollectionPaymentMatchRepository.Balance.class);
    when(value.getId()).thenReturn(obligationId);
    when(value.getAmount()).thenReturn(new BigDecimal(amount));
    return value;
  }

  @Test
  void cashIsReportedUnderThePlanLineIdentityNotTheFinanceRowId() {
    UUID rowA = UUID.randomUUID(), rowB = UUID.randomUUID();
    UUID lineA = UUID.randomUUID(), lineB = UUID.randomUUID();
    when(obligations.findByTenantIdAndOrderId(tenant, order))
        .thenReturn(List.of(obligation(rowA, lineA), obligation(rowB, lineB)));
    var cash = balance(rowA, "40.0000");
    when(matches.settled(eq(tenant), anyList())).thenReturn(List.of(cash));
    var settled = adapter.settledByObligation(tenant, order);
    assertThat(settled).containsOnlyKeys(lineA);
    assertThat(settled.get(lineA)).isEqualByComparingTo("40");
  }

  @Test
  void anOrderWithoutObligationsRunsNoMatchQuery() {
    when(obligations.findByTenantIdAndOrderId(tenant, order)).thenReturn(List.of());
    assertThat(adapter.settledByObligation(tenant, order)).isEmpty();
    verifyNoInteractions(matches);
  }

  @Test
  void obligationsWithNothingCollectedAreAbsent() {
    when(obligations.findByTenantIdAndOrderId(tenant, order))
        .thenReturn(List.of(obligation(UUID.randomUUID(), UUID.randomUUID())));
    when(matches.settled(eq(tenant), anyList())).thenReturn(List.of());
    assertThat(adapter.settledByObligation(tenant, order)).isEmpty();
  }

  @Test
  void aQuestionAboutAnotherTenantFailsInsteadOfAnsweringNothingCollected() {
    assertThatThrownBy(() -> adapter.settledByObligation(UUID.randomUUID(), order))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(obligations, matches);
  }
}
