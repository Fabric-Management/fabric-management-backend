package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.ChequeAction;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollectionChequeQueryAccessTest {
  @Mock CollectionAccessService access;
  @Mock CollectionCommandStore commands;
  @Mock CollectionObligationRepository obligations;
  @Mock CollectionPaymentMatchRepository matches;
  @Mock CollectionProjectionRepository projections;
  @Mock CollectionProjectionService projection;
  @Mock CollectionChequeRepository cheques;
  @Mock CollectionChequeApplicationRepository applications;
  @Mock CollectionChequeEventRepository events;
  @Mock CollectionExpectationRepository expectations;
  @Mock PaymentRepository payments;
  @InjectMocks CollectionQueryService queries;
  UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      id = UUID.randomUUID(),
      rowId = UUID.randomUUID();
  CollectionObligation row;
  CollectionCheque cheque;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    cheque =
        CollectionCheque.receive(
            UUID.randomUUID(),
            "GBP",
            new BigDecimal("4284"),
            LocalDate.parse("2026-10-30"),
            LocalDate.parse("2026-11-20"),
            "NORTHBANK-42",
            "Signed physical instrument");
    cheque.setId(id);
    cheque.setTenantId(tenant);
    cheque.setCreatedBy(UUID.randomUUID());
    row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), cheque.getPartnerId(), "GBP");
    row.setId(rowId);
    row.setTenantId(tenant);
    row.assignResponsible(actor);
    when(cheques.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(cheque));
    when(applications.findByTenantIdAndChequeId(tenant, id))
        .thenReturn(List.of(CollectionChequeApplication.create(id, rowId, new BigDecimal("4284"))));
    when(obligations.findByTenantIdAndIdIn(tenant, List.of(rowId))).thenReturn(List.of(row));
    when(access.reach(actor, "read"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    lenient()
        .when(access.reach(actor, "reverse"))
        .thenReturn(new CollectionAccessService.Reach(tenant, null, Set.of(actor)));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void appointedOwnerCanReadAndWorkAnInstrumentCreatedByAColleague() {
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    var result = queries.cheque(id, actor);
    assertThat(result.capabilities())
        .anySatisfy(
            c -> {
              assertThat(c.action()).isEqualTo("CLEAR");
              assertThat(c.allowed()).isTrue();
            });
  }

  @Test
  void undoingAClearedInstrumentIsOfferedOnlyToThoseHoldingTheReverseGrant() {
    cheque.transition(ChequeAction.CLEAR, UUID.randomUUID(), "Bank confirmed clearing");
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    for (var action : List.of("DISHONOUR", "RETURN"))
      assertThat(queries.cheque(id, actor).capabilities())
          .filteredOn(c -> c.action().equals(action))
          .singleElement()
          .satisfies(
              c -> {
                assertThat(c.allowed()).isFalse();
                assertThat(c.reason()).isEqualTo("PERMISSION_DENIED");
              });
    when(access.reach(actor, "reverse"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    for (var action : List.of("DISHONOUR", "RETURN"))
      assertThat(queries.cheque(id, actor).capabilities())
          .filteredOn(c -> c.action().equals(action))
          .singleElement()
          .satisfies(c -> assertThat(c.allowed()).isTrue());
  }

  @Test
  void dishonouringAnInstrumentThatMovedNoMoneyStaysAWriteAction() {
    cheque.transition(ChequeAction.PRESENT, null, "Presented at the bank");
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("DISHONOUR"))
        .singleElement()
        .satisfies(c -> assertThat(c.allowed()).isTrue());
  }

  @Test
  void hiddenApplicationHidesWholeInstrumentEvenFromItsCreator() {
    row.assignResponsible(UUID.randomUUID());
    assertThatThrownBy(() -> queries.cheque(id, actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    verifyNoInteractions(payments);
  }
}
