package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionCheque;
import com.fabricmanagement.finance.collection.domain.CollectionCommandReceipt;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.dto.CollectionRequests;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeHandoverRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.common.app.FinanceDocumentNumberGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;

class CollectionChequeHandoverServiceTest {
  private final UUID tenant = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID customer = UUID.randomUUID();
  private final UUID operation = UUID.randomUUID();
  private final UUID obligation = UUID.randomUUID();
  private final LocalDate receivedOn = LocalDate.of(2026, 10, 20);
  private final Instant occurredAt = Instant.parse("2026-10-20T12:00:00Z");
  private final CollectionCommandStore commands = mock(CollectionCommandStore.class);
  private final CollectionAccessService access = mock(CollectionAccessService.class);
  private final CollectionPostingService posting = mock(CollectionPostingService.class);
  private final CollectionProjectionService projections = mock(CollectionProjectionService.class);
  private final CollectionChequeTargets targets = mock(CollectionChequeTargets.class);
  private final CollectionChequeDrawerResolver drawers = mock(CollectionChequeDrawerResolver.class);
  private final CollectionChequeService cheques = mock(CollectionChequeService.class);
  private final CollectionChequeHandoverRepository handovers =
      mock(CollectionChequeHandoverRepository.class);
  private final FinanceDocumentNumberGenerator numbers = mock(FinanceDocumentNumberGenerator.class);
  private final CollectionChequeHandoverService service =
      new CollectionChequeHandoverService(
          commands, access, posting, projections, targets, drawers, cheques, handovers, numbers);

  @BeforeEach
  void enterTenant() {
    TenantContext.setCurrentTenantId(tenant);
  }

  @AfterEach
  void leaveTenant() {
    TenantContext.clear();
  }

  @Test
  void replayAfterWriteGrantAndLockNeverChecksTargetsOrLooksUpDrawer() {
    var request =
        save(
            List.of(new CollectionRequests.Version(obligation, 1L)),
            List.of(
                row(
                    "1",
                    DrawerKind.OWN,
                    List.of(new SaveApplication(obligation, BigDecimal.ONE)))));
    var handover = UUID.randomUUID();
    when(commands.hash("RECEIVE_CHEQUE_HANDOVER", customer, request)).thenReturn("original");
    when(commands.replay(operation, actor, "original"))
        .thenReturn(
            Optional.of(
                CollectionCommandReceipt.create(
                    operation,
                    actor,
                    "original",
                    "CHEQUE_HANDOVER",
                    handover,
                    0,
                    null,
                    List.of())));
    assertThat(service.save(request, actor).resourceId()).isEqualTo(handover);
    var order = inOrder(commands, access);
    order.verify(commands).validate(request);
    order.verify(access).requireGrant(actor, "write");
    order.verify(commands).lock();
    order.verify(commands).replay(operation, actor, "original");
    verifyNoInteractions(posting, projections, targets, drawers, cheques, handovers, numbers);
  }

  @Test
  void missingExtraAndRepeatedVersionsAreRefusedBeforeTheLock() {
    var valid = new CollectionRequests.Version(obligation, 1L);
    var extra = new CollectionRequests.Version(UUID.randomUUID(), 1L);
    var row = row("1", DrawerKind.OWN, List.of(new SaveApplication(obligation, BigDecimal.ONE)));
    List<List<CollectionRequests.Version>> invalid =
        List.of(List.of(), List.of(valid, extra), List.of(valid, valid));
    for (var versions : invalid) {
      assertThatThrownBy(() -> service.save(save(versions, List.of(row)), actor))
          .isInstanceOfSatisfying(
              CollectionRuleException.class,
              refusal ->
                  assertThat(refusal.getErrorCode()).isEqualTo("CHEQUE_HANDOVER_VERSIONS_INVALID"));
    }
    verify(commands, never()).lock();
    verifyNoInteractions(posting, projections, targets, drawers, cheques, handovers, numbers);
  }

  @Test
  void validatesTotalApplicationCountAcrossRowsBeforeTheLock() {
    var entries =
        IntStream.range(0, 500)
            .mapToObj(i -> new SaveApplication(UUID.randomUUID(), BigDecimal.ONE))
            .toList();
    var first = row("1", DrawerKind.OWN, entries);
    var last = row("2", DrawerKind.OWN, List.of(new SaveApplication(obligation, BigDecimal.ONE)));
    assertThatThrownBy(() -> service.save(save(List.of(), List.of(first, last)), actor))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refusal ->
                assertThat(refusal.getErrorCode()).isEqualTo("CHEQUE_HANDOVER_APPLICATION_LIMIT"));
    verify(commands, never()).lock();
    verifyNoInteractions(posting, projections, targets, drawers, cheques, handovers, numbers);
  }

  @Test
  void anInvalidDrawerOnTheSecondRowRefusesEveryRowBeforeTakingANumber() {
    var first = row("1", DrawerKind.OWN, List.of());
    var second = row("2", DrawerKind.ENDORSED, List.of());
    var request = save(List.of(), List.of(first, second));
    when(commands.hash("RECEIVE_CHEQUE_HANDOVER", customer, request)).thenReturn("first");
    when(drawers.forReceipt(customer, DrawerKind.OWN, null, null))
        .thenReturn(new CollectionCheque.Drawer(DrawerKind.OWN, null, "Northbridge Cloth"));
    when(drawers.forReceipt(customer, DrawerKind.ENDORSED, null, null))
        .thenThrow(
            CollectionRuleException.invalid(
                "CHEQUE_DRAWER_NAME_REQUIRED", "Enter the drawer name"));
    assertThatThrownBy(() -> service.save(request, actor))
        .isInstanceOfSatisfying(
            CollectionRuleException.class,
            refusal -> {
              assertThat(refusal.getErrorCode()).isEqualTo("CHEQUE_DRAWER_NAME_REQUIRED");
              assertThat(refusal.getDetails()).containsEntry("rowKey", "2").containsEntry("row", 1);
            });
    verifyNoInteractions(cheques, handovers, numbers);
  }

  private SaveRequest save(List<CollectionRequests.Version> versions, List<SaveRowInput> rows) {
    return new SaveRequest(
        operation,
        customer,
        receivedOn,
        occurredAt,
        "Received from Northbridge Cloth",
        "Cheque handover slip",
        versions,
        rows);
  }

  private SaveRowInput row(String key, DrawerKind kind, List<SaveApplication> applications) {
    return new SaveRowInput(
        key,
        "CH-" + key,
        "GBP",
        new BigDecimal("3000"),
        receivedOn.plusMonths(3),
        kind,
        null,
        null,
        applications);
  }
}
