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
import java.time.Instant;
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
            "Signed physical instrument",
            new CollectionCheque.Drawer(
                com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind.OWN,
                null,
                "Northbridge Cloth"));
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
    when(applications.active(tenant, id)).thenReturn(List.of(application(rowId, "4284")));
    lenient()
        .when(obligations.findByTenantIdAndIdIn(tenant, List.of(rowId)))
        .thenReturn(List.of(row));
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
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("CORRECT_DRAWER"))
        .singleElement()
        .satisfies(c -> assertThat(c.allowed()).isTrue());
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

  private CollectionChequeApplication application(UUID obligation, String amount) {
    return CollectionChequeApplication.create(
        id,
        obligation,
        new BigDecimal(amount),
        LocalDate.parse("2026-10-30"),
        Instant.parse("2026-10-30T12:00:00Z"),
        actor,
        "Cheque handed to finance");
  }

  /**
   * PAYMENT-PLAN-9 S1: two active rows on one instalment are one obligation for access and
   * capabilities, and both reduce the unapplied amount.
   */
  @Test
  void severalActiveApplicationsToOneInstalmentAreOneObligationAndBothReduceTheUnappliedAmount() {
    when(applications.active(tenant, id))
        .thenReturn(List.of(application(rowId, "2000"), application(rowId, "1000")));
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    var result = queries.cheque(id, actor);
    assertThat(new BigDecimal(result.unappliedAmount())).isEqualByComparingTo("1284");
    assertThat(result.applications()).hasSize(2);
    assertThat(result.capabilities())
        .filteredOn(c -> c.action().equals("CLEAR"))
        .singleElement()
        .satisfies(c -> assertThat(c.allowed()).isTrue());
    verify(obligations).findByTenantIdAndIdIn(tenant, List.of(rowId));
  }

  /** A covered obligation that cannot be loaded still hides the whole instrument. */
  @Test
  void aMissingObligationAmongTheDistinctTargetsHidesTheInstrument() {
    UUID gone = UUID.randomUUID();
    when(applications.active(tenant, id))
        .thenReturn(
            List.of(
                application(rowId, "2000"), application(rowId, "1000"), application(gone, "500")));
    when(obligations.findByTenantIdAndIdIn(tenant, List.of(rowId, gone))).thenReturn(List.of(row));
    assertThatThrownBy(() -> queries.cheque(id, actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
  }

  /**
   * PAYMENT-PLAN-9 S2 (D10, R6): a finance user responsible for no order reads and works a cheque
   * applied to nothing under the organisation scope; capabilities follow the same rule as commands.
   */
  @Test
  void anUnlinkedChequeIsReadAndWorkedUnderTheOrganisationScopeWithoutAnyOrder() {
    when(applications.active(tenant, id)).thenReturn(List.of());
    when(access.reach(actor, "read"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    when(access.reach(actor, "write"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    var result = queries.cheque(id, actor);
    assertThat(new BigDecimal(result.unappliedAmount())).isEqualByComparingTo("4284");
    assertThat(result.applications()).isEmpty();
    for (var action : List.of("PRESENT", "CLEAR", "RETURN", "CANCEL", "CORRECT", "CORRECT_DRAWER"))
      assertThat(result.capabilities())
          .filteredOn(c -> c.action().equals(action))
          .singleElement()
          .satisfies(c -> assertThat(c.allowed()).as(action).isTrue());
    verify(obligations, never()).findByTenantIdAndIdIn(any(), any());
  }

  /** R6: under OWN or DEPARTMENT scope an unlinked cheque is reached through its creator only. */
  @Test
  void anUnlinkedChequeIsReachedThroughItsCreatorUnderOwnAndDepartmentScope() {
    UUID colleague = UUID.randomUUID();
    when(applications.active(tenant, id)).thenReturn(List.of());
    // OWN, created by someone else: hidden.
    assertThatThrownBy(() -> queries.cheque(id, actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    // DEPARTMENT, created by someone outside the department: hidden.
    when(access.reach(actor, "read"))
        .thenReturn(
            new CollectionAccessService.Reach(
                tenant, DataScope.DEPARTMENT, Set.of(actor, colleague)));
    assertThatThrownBy(() -> queries.cheque(id, actor))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.NotFoundException.class);
    // DEPARTMENT, created by a colleague: readable, and workable with the same write scope.
    cheque.setCreatedBy(colleague);
    when(access.reach(actor, "write"))
        .thenReturn(
            new CollectionAccessService.Reach(
                tenant, DataScope.DEPARTMENT, Set.of(actor, colleague)));
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("CORRECT_DRAWER"))
        .singleElement()
        .satisfies(c -> assertThat(c.allowed()).isTrue());
    // OWN over one's own cheque: readable and workable.
    cheque.setCreatedBy(actor);
    when(access.reach(actor, "read"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("CORRECT_DRAWER"))
        .singleElement()
        .satisfies(c -> assertThat(c.allowed()).isTrue());
    cheque.setCreatedBy(colleague);
    when(access.reach(actor, "read"))
        .thenReturn(
            new CollectionAccessService.Reach(
                tenant, DataScope.DEPARTMENT, Set.of(actor, colleague)));
    // OWN write over a colleague's cheque: readable through the department, not workable.
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("CORRECT_DRAWER"))
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.allowed()).isFalse();
              assertThat(c.reason()).isEqualTo("PERMISSION_DENIED");
            });
  }

  /**
   * R6: undoing an unlinked cheque's clearing is offered only with the reverse grant over its
   * creator.
   */
  @Test
  void undoingAnUnlinkedClearingIsOfferedOnlyWithTheReverseGrantOverItsCreator() {
    when(applications.active(tenant, id)).thenReturn(List.of());
    cheque.setCreatedBy(actor);
    cheque.transition(ChequeAction.CLEAR, UUID.randomUUID(), "Bank confirmed clearing");
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("DISHONOUR"))
        .singleElement()
        .satisfies(c -> assertThat(c.reason()).isEqualTo("PERMISSION_DENIED"));
    when(access.reach(actor, "reverse"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("DISHONOUR"))
        .singleElement()
        .satisfies(c -> assertThat(c.allowed()).isTrue());
  }

  /** S3: ALLOCATE and reversal are offered only while the cheque is held and has room. */
  @Test
  void allocationAndReversalAreOfferedOnlyWhileTheChequeIsHeldWorkableAndNotFullyApplied() {
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    // Fully applied (4,284 of 4,284): nothing left to allocate, but the application is reversible.
    var full = queries.cheque(id, actor);
    assertThat(full.capabilities())
        .filteredOn(c -> c.action().equals("ALLOCATE"))
        .singleElement()
        .satisfies(c -> assertThat(c.reason()).isEqualTo("NOTHING_UNAPPLIED"));
    assertThat(full.applications())
        .singleElement()
        .satisfies(a -> assertThat(a.reversible()).isTrue());
    // Partly applied: allocation offered.
    when(applications.active(tenant, id)).thenReturn(List.of(application(rowId, "1000")));
    assertThat(queries.cheque(id, actor).capabilities())
        .filteredOn(c -> c.action().equals("ALLOCATE"))
        .singleElement()
        .satisfies(c -> assertThat(c.allowed()).isTrue());
    // Cleared: neither.
    cheque.transition(ChequeAction.CLEAR, UUID.randomUUID(), "Bank confirmed clearing");
    var cleared = queries.cheque(id, actor);
    assertThat(cleared.capabilities())
        .filteredOn(c -> c.action().equals("ALLOCATE"))
        .singleElement()
        .satisfies(c -> assertThat(c.reason()).isEqualTo("WRONG_STATE"));
    assertThat(cleared.applications())
        .singleElement()
        .satisfies(a -> assertThat(a.reversible()).isFalse());
  }

  @Test
  void aViewerWhoMayNotWriteTheCoveredObligationIsOfferedNeitherAllocationNorReversal() {
    when(access.reach(actor, "read"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    when(access.reach(actor, "write"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(UUID.randomUUID())));
    when(applications.active(tenant, id)).thenReturn(List.of(application(rowId, "1000")));
    var result = queries.cheque(id, actor);
    assertThat(result.capabilities())
        .filteredOn(c -> c.action().equals("ALLOCATE"))
        .singleElement()
        .satisfies(c -> assertThat(c.reason()).isEqualTo("PERMISSION_DENIED"));
    assertThat(result.applications())
        .singleElement()
        .satisfies(a -> assertThat(a.reversible()).isFalse());
  }

  /**
   * PAYMENT-PLAN-9 S3: a reversed application's target is shown only to a viewer who may read it.
   */
  @Test
  void aReversedApplicationsTargetIsShownOnlyToAViewerWhoMayReadIt() {
    UUID otherId = UUID.randomUUID();
    var other =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), cheque.getPartnerId(), "GBP");
    other.setId(otherId);
    other.setTenantId(tenant);
    other.assignResponsible(UUID.randomUUID());
    var reversed = application(otherId, "500");
    reversed.setId(UUID.randomUUID());
    reversed.setCreatedAt(Instant.parse("2026-10-30T12:00:00Z"));
    reversed.reverse(
        LocalDate.parse("2026-10-31"),
        Instant.parse("2026-10-31T12:00:00Z"),
        Instant.parse("2026-10-31T12:00:00Z"),
        actor,
        "Applied to the wrong instalment");
    when(applications.findByTenantIdAndChequeId(tenant, id)).thenReturn(List.of(reversed));
    when(obligations.findByTenantIdAndIdIn(tenant, List.of(otherId))).thenReturn(List.of(other));
    var event =
        CollectionChequeEvent.application(
            cheque,
            "REVERSE_APPLICATION",
            reversed.getId(),
            LocalDate.parse("2026-10-31"),
            Instant.parse("2026-10-31T12:00:00Z"),
            actor,
            "Applied to the wrong instalment",
            "Remittance advice");
    when(events.findByTenantIdAndChequeId(eq(tenant), eq(id), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(event)));
    var page =
        queries.chequeHistory(id, actor, org.springframework.data.domain.PageRequest.of(0, 20));
    assertThat(page.getContent())
        .singleElement()
        .satisfies(
            value -> {
              assertThat(value.action()).isEqualTo("REVERSE_APPLICATION");
              assertThat(value.application().id()).isEqualTo(reversed.getId());
              assertThat(value.application().obligationId()).isNull();
              assertThat(value.application().targetVisible()).isFalse();
              assertThat(new BigDecimal(value.application().amount())).isEqualByComparingTo("500");
            });
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
