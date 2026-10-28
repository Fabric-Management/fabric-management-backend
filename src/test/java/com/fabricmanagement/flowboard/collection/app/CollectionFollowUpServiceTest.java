package com.fabricmanagement.flowboard.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.board.infra.repository.BoardRepository;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.collection.domain.*;
import com.fabricmanagement.flowboard.collection.dto.CollectionFollowUpDtos.*;
import com.fabricmanagement.flowboard.collection.infra.repository.*;
import com.fabricmanagement.flowboard.routing.app.*;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.port.out.RoutingUserQueryPort.UserPermissions;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.infra.repository.*;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class CollectionFollowUpServiceTest {
  @Mock CollectionFollowUpPort source;
  @Mock CollectionFollowUpRepository cases;
  @Mock CollectionContactRepository contacts;
  @Mock CollectionFollowUpCommandRepository receipts;
  @Mock TaskRepository tasks;
  @Mock TaskAssigneeRepository assignees;
  @Mock TaskVersionLockRepository versions;
  @Mock TaskProvisioningService provisioning;
  @Mock BoardRepository boards;
  @Mock RoutingEvaluationService routing;
  @Mock RoutingRepository pools;
  @Mock RoutingEligibilityService eligibility;
  @Mock jakarta.validation.Validator validator;
  final UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      id = UUID.randomUUID(),
      obligation = UUID.randomUUID();
  final Clock clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);
  CollectionFollowUpService service;
  CollectionFollowUp c;
  Task task;
  CollectionFollowUpPort.Facts facts;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(actor);
    lenient()
        .when(source.dayOf(any()))
        .thenAnswer(
            call ->
                ((Instant) call.getArgument(0)).atZone(ZoneId.of("Europe/London")).toLocalDate());
    service =
        new CollectionFollowUpService(
            source,
            cases,
            contacts,
            receipts,
            tasks,
            assignees,
            versions,
            provisioning,
            boards,
            routing,
            pools,
            eligibility,
            clock,
            validator);
    c = CollectionFollowUp.open(obligation, 1);
    c.setId(id);
    c.setTenantId(tenant);
    c.setVersion(3L);
    c.task(UUID.randomUUID());
    c.assign(actor);
    task =
        Task.create(
            "TSK-0042",
            UUID.randomUUID(),
            "Collection follow-up",
            TaskType.COLLECTION_FOLLOW_UP,
            ModuleType.GENERAL,
            Priority.MEDIUM,
            LocalDate.of(2026, 10, 30),
            null,
            "COLLECTION_OBLIGATION",
            obligation);
    task.setId(c.getTaskId());
    task.setTenantId(tenant);
    task.setVersion(2L);
    task.govern(
        "COLLECTION_FOLLOW_UP:" + obligation + ":1",
        TaskWorkflowRegistry.COLLECTION_DEFINITION_ID,
        1);
    facts =
        new CollectionFollowUpPort.Facts(
            obligation,
            7,
            UUID.randomUUID(),
            "GBP",
            new BigDecimal("4284.00"),
            new BigDecimal("4284.00"),
            BigDecimal.ZERO,
            LocalDate.of(2026, 10, 30),
            null,
            false,
            false,
            false,
            false,
            actor);
    lenient().when(cases.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(c));
    lenient()
        .when(cases.findFirstByTenantIdAndObligationIdOrderByEpisodeDesc(tenant, obligation))
        .thenReturn(Optional.of(c));
    lenient().when(tasks.findById(c.getTaskId())).thenReturn(Optional.of(task));
    lenient().when(source.current(obligation)).thenReturn(facts);
    lenient().when(source.mayRead(actor, obligation)).thenReturn(true);
    lenient().when(source.today()).thenReturn(LocalDate.of(2026, 10, 22));
    lenient()
        .when(eligibility.user(tenant, actor))
        .thenReturn(
            new UserPermissions(
                tenant,
                actor,
                "Harriet Ward",
                true,
                new PermissionResult(
                    Map.of(
                        "flowboard",
                        Map.of("read", DataScope.OWN, "write", DataScope.OWN),
                        "finance",
                        Map.of("read", DataScope.OWN)),
                    false)));
    lenient()
        .when(pools.isActiveMember(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, actor))
        .thenReturn(true);
    lenient()
        .when(
            eligibility.eligibility(
                eq(tenant),
                eq(obligation),
                any(),
                eq(true),
                eq(RoutingPoolKey.COLLECTION_FOLLOW_UP)))
        .thenReturn(List.of());
    lenient()
        .when(assignees.findAllByTaskIdAndIsActiveTrue(c.getTaskId()))
        .thenReturn(List.of(TaskAssignee.assignToUser(c.getTaskId(), actor, AssignedBy.SYSTEM)));
    lenient().when(validator.validate(any())).thenReturn(Set.of());
    lenient()
        .when(contacts.saveAndFlush(any()))
        .thenAnswer(
            a -> {
              CollectionContact contact = a.getArgument(0);
              contact.setId(UUID.randomUUID());
              return contact;
            });
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  ContactRequest request(UUID operation, long version) {
    return new ContactRequest(
        operation,
        version,
        7L,
        2L,
        CollectionContact.Outcome.PROMISE_TO_PAY,
        Instant.parse("2026-10-22T11:00:00Z"),
        LocalDate.of(2026, 11, 2),
        new BigDecimal("1000.00"),
        LocalDate.of(2026, 11, 2),
        "Harriet spoke to the customer");
  }

  @Test
  void financeOnlyHolderCanRecordPromiseWithoutPostingMoney() {
    var result = service.recordContact(id, actor, request(UUID.randomUUID(), 3));
    assertThat(result.contactId()).isNotNull();
    assertThat(c.getPromisedOn()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(facts.outstanding()).isEqualByComparingTo("4284.00");
    assertThat(task.getClosedAt()).isNull();
    verify(contacts).saveAndFlush(any());
    verify(source, never()).eligible(any(), any());
  }

  @Test
  void promiseUsesCurrencyPrecisionWithoutRoundingAwayMoney() {
    var original = request(UUID.randomUUID(), 3);
    var precise =
        new ContactRequest(
            original.operationId(),
            original.expectedVersion(),
            original.expectedObligationVersion(),
            original.expectedTaskVersion(),
            original.outcome(),
            original.occurredAt(),
            original.promisedOn(),
            new BigDecimal("1000.000"),
            original.nextFollowUpOn(),
            original.note());

    service.recordContact(id, actor, precise);

    var saved = ArgumentCaptor.forClass(CollectionContact.class);
    verify(contacts).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getPromisedAmount()).isEqualTo(new BigDecimal("1000.00"));
    assertThat(c.getPromisedAmount()).isEqualTo(new BigDecimal("1000.00"));
  }

  @Test
  void promiseCannotRoundAFractionOfAPennyIntoAValidAmount() {
    var original = request(UUID.randomUUID(), 3);
    var excessive =
        new ContactRequest(
            original.operationId(),
            original.expectedVersion(),
            original.expectedObligationVersion(),
            original.expectedTaskVersion(),
            original.outcome(),
            original.occurredAt(),
            original.promisedOn(),
            new BigDecimal("1000.001"),
            original.nextFollowUpOn(),
            original.note());

    assertThatThrownBy(() -> service.recordContact(id, actor, excessive))
        .hasMessageContaining("currency precision");
    verifyNoInteractions(contacts);
    verify(receipts).findByTenantIdAndOperationId(tenant, excessive.operationId());
    verifyNoMoreInteractions(receipts);
  }

  @Test
  void sameActorAndBodyReplayReturnsStoredResultBeforeStaleVersion() {
    var operation = UUID.randomUUID();
    var request = request(operation, 3);
    service.recordContact(id, actor, request);
    var saved = ArgumentCaptor.forClass(CollectionFollowUpCommand.class);
    verify(receipts).saveAndFlush(saved.capture());
    when(receipts.findByTenantIdAndOperationId(tenant, operation))
        .thenReturn(Optional.of(saved.getValue()));
    c.setVersion(99L);
    assertThat(service.recordContact(id, actor, request).replayed()).isTrue();
    verify(contacts, times(1)).saveAndFlush(any());
  }

  @Test
  void changedBodyCannotReuseTheOperation() {
    var operation = UUID.randomUUID();
    when(receipts.findByTenantIdAndOperationId(tenant, operation))
        .thenReturn(
            Optional.of(
                CollectionFollowUpCommand.record(
                    operation, actor, id, "different", null, 3, null, null)));
    assertThatThrownBy(() -> service.recordContact(id, actor, request(operation, 3)))
        .hasMessageContaining("another command");
    verifyNoInteractions(contacts);
  }

  @Test
  void staleObligationOrTaskCannotAddContact() {
    var stale =
        new ContactRequest(
            UUID.randomUUID(),
            3L,
            6L,
            2L,
            CollectionContact.Outcome.REACHED,
            clock.instant(),
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> service.recordContact(id, actor, stale))
        .hasMessageContaining("Reload");
    verifyNoInteractions(contacts);
  }

  @Test
  void removedPoolMemberCannotUseCachedCapability() {
    when(pools.isActiveMember(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, actor))
        .thenReturn(false);
    assertThatThrownBy(() -> service.recordContact(id, actor, request(UUID.randomUUID(), 3)))
        .hasMessageContaining("ASSIGNED_ELSEWHERE");
    verifyNoInteractions(contacts);
  }

  @Test
  void noFinanceScopeAndForeignTenantAreNotFound() {
    when(source.mayRead(actor, obligation)).thenReturn(false);
    assertThatThrownBy(() -> service.detail(id, actor)).hasMessageContaining("not found");
    verify(source, never()).current(any());
    TenantContext.setCurrentTenantId(UUID.randomUUID());
    assertThatThrownBy(() -> service.contacts(id, actor, 0, 20)).hasMessageContaining("not found");
  }

  @Test
  void settlementClosesTheEpisodeButKeepsContactHistory() {
    var settled =
        new CollectionFollowUpPort.Facts(
            obligation,
            8,
            facts.partnerId(),
            "GBP",
            facts.expected(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            facts.agreedDate(),
            null,
            false,
            false,
            false,
            false,
            actor);
    when(source.current(obligation)).thenReturn(settled);
    service.synchronize(obligation);
    assertThat(c.getClosedAt()).isEqualTo(clock.instant());
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
    verifyNoInteractions(contacts, receipts);
  }

  @Test
  void futureContactAndInvalidPromiseAreRejected() {
    var bad =
        new ContactRequest(
            UUID.randomUUID(),
            3L,
            7L,
            2L,
            CollectionContact.Outcome.PROMISE_TO_PAY,
            clock.instant().plusSeconds(1),
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> service.recordContact(id, actor, bad)).hasMessageContaining("future");
    verifyNoInteractions(contacts);
  }

  @Test
  void aManagerCanAppointOnlyAnEligiblePoolMemberAndKeepsTheReason() {
    var owner = UUID.randomUUID();
    var user =
        new UserPermissions(
            tenant,
            actor,
            "Harriet Ward",
            true,
            new PermissionResult(
                Map.of(
                    "flowboard",
                    Map.of(
                        "read",
                        DataScope.ORGANIZATION,
                        "write",
                        DataScope.ORGANIZATION,
                        "manage-routing",
                        DataScope.ORGANIZATION),
                    "finance",
                    Map.of("read", DataScope.ORGANIZATION)),
                false));
    when(eligibility.user(tenant, actor)).thenReturn(user);
    when(pools.isActiveMember(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, owner)).thenReturn(true);
    when(eligibility.user(tenant, owner))
        .thenReturn(new UserPermissions(tenant, owner, "Oliver Reed", true, user.permissions()));
    service.assign(
        id,
        actor,
        new AssignRequest(UUID.randomUUID(), 3L, 7L, 2L, owner, "Cover Harriet's annual leave"));
    assertThat(c.getResponsibleUserId()).isEqualTo(owner);
    verify(source).assign(obligation, owner);
    var saved = ArgumentCaptor.forClass(CollectionFollowUpCommand.class);
    verify(receipts).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getReason()).contains("annual leave");
    verifyNoInteractions(contacts);
  }

  @Test
  void revokedFlowboardWriteIsCheckedBeforeReplay() {
    when(eligibility.user(tenant, actor))
        .thenReturn(
            new UserPermissions(
                tenant,
                actor,
                "Harriet Ward",
                true,
                new PermissionResult(
                    Map.of(
                        "flowboard",
                        Map.of("read", DataScope.OWN),
                        "finance",
                        Map.of("read", DataScope.OWN)),
                    false)));
    assertThatThrownBy(() -> service.recordContact(id, actor, request(UUID.randomUUID(), 3)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(contacts, receipts);
  }

  @Test
  void aLateRecordedPromiseRetainsItsActualDatesWithoutMovingTheAgreement() {
    var request =
        new ContactRequest(
            UUID.randomUUID(),
            3L,
            7L,
            2L,
            CollectionContact.Outcome.PROMISE_TO_PAY,
            Instant.parse("2026-10-20T11:00:00Z"),
            LocalDate.of(2026, 10, 21),
            new BigDecimal("1000.00"),
            LocalDate.of(2026, 10, 21),
            "Customer promised payment during the earlier conversation");
    service.recordContact(id, actor, request);
    assertThat(c.getPromisedOn()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(c.getDueDate()).isEqualTo(LocalDate.of(2026, 10, 30));
    assertThat(facts.outstanding()).isEqualByComparingTo("4284.00");
  }

  private CollectionFollowUpPort.Facts ledger(long version, BigDecimal outstanding, UUID owner) {
    return new CollectionFollowUpPort.Facts(
        obligation,
        version,
        facts.partnerId(),
        "GBP",
        facts.expected(),
        outstanding,
        BigDecimal.ZERO,
        facts.agreedDate(),
        null,
        false,
        false,
        false,
        false,
        owner);
  }

  /** The case has seen the ledger as it is in {@code facts}. */
  private void observed(CollectionFollowUpPort.Facts seen) {
    c.observe(
        seen.version(),
        seen.phase(),
        seen.dueDate(),
        seen.currency(),
        seen.outstanding(),
        seen.sourceMissing(),
        seen.stale(),
        clock.instant());
  }

  @Test
  void aCaseThatAlreadyShowsTheLedgerIsLeftAloneAndCostsReadsOnly() {
    observed(facts);

    assertThat(service.repair(obligation)).isFalse();

    verifyNoInteractions(routing, provisioning, contacts, receipts);
    verify(source, never()).assign(any(), any());
    verify(tasks, never()).saveAndFlush(any());
    verify(cases, never()).saveAndFlush(any());
  }

  @Test
  void aCaseBehindTheLedgerIsBroughtInLineAndSaysSo() {
    observed(ledger(6, facts.outstanding(), actor));

    assertThat(service.repair(obligation)).isTrue();

    verify(routing).evaluate(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, c.getTaskId());
  }

  @Test
  void aCaseNobodyHoldsYetIsNeverInLineBecauseRoutingIsTriedAgain() {
    observed(facts);
    c.assign(null);

    assertThat(service.repair(obligation)).isTrue();

    verify(routing).evaluate(tenant, RoutingPoolKey.COLLECTION_FOLLOW_UP, c.getTaskId());
  }

  @Test
  void aCaseHeldBySomeoneTheLedgerDoesNotNameIsBroughtInLine() {
    observed(facts);
    when(source.current(obligation)).thenReturn(ledger(7, facts.outstanding(), UUID.randomUUID()));

    assertThat(service.repair(obligation)).isTrue();
  }

  @Test
  void anOpenObligationWithoutAnOpenCaseIsNotInLineAndHasOneOpened() {
    when(cases.findFirstByTenantIdAndObligationIdOrderByEpisodeDesc(tenant, obligation))
        .thenReturn(Optional.empty());
    // The test has no global board, which stops the opening once the new case is recorded.
    assertThatThrownBy(() -> service.repair(obligation)).hasMessageContaining("global board");

    var opened = ArgumentCaptor.forClass(CollectionFollowUp.class);
    verify(cases).saveAndFlush(opened.capture());
    assertThat(opened.getValue().getObligationId()).isEqualTo(obligation);
    assertThat(opened.getValue().getEpisode()).isEqualTo(1);
  }

  @Test
  void aClosedObligationWhoseCaseIsAlreadyClosedNeedsNothing() {
    c.close(clock.instant(), "RECORDED_SETTLED");
    when(source.current(obligation)).thenReturn(ledger(8, BigDecimal.ZERO, actor));

    assertThat(service.repair(obligation)).isFalse();

    verify(cases, never()).saveAndFlush(any());
    verifyNoInteractions(routing, provisioning);
  }

  @Test
  void aClosedObligationWhoseCaseIsStillOpenClosesTheCase() {
    when(source.current(obligation)).thenReturn(ledger(8, BigDecimal.ZERO, actor));

    assertThat(service.repair(obligation)).isTrue();

    assertThat(c.getClosedAt()).isEqualTo(clock.instant());
    assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
  }

  @Test
  void theOpenCasesAreListedInObligationOrderForTheScan() {
    UUID after = UUID.randomUUID(), next = UUID.randomUUID();
    when(cases.openObligationIds(tenant, after, PageRequest.of(0, 50))).thenReturn(List.of(next));

    assertThat(service.openObligations(after, 50)).containsExactly(next);
  }
}
