package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class CollectionChequePreviewServiceTest {
  private final UUID tenant = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID partner = UUID.randomUUID();
  private final LocalDate date = LocalDate.of(2026, 12, 31);
  private final CollectionAccessService access = mock(CollectionAccessService.class);
  private final CollectionObligationRepository obligations =
      mock(CollectionObligationRepository.class);
  private final CollectionChequeRepository cheques = mock(CollectionChequeRepository.class);
  private final CollectionChequeTargets targets = mock(CollectionChequeTargets.class);
  private final CollectionSourcePort source = mock(CollectionSourcePort.class);
  private final CollectionChequePreviewService preview =
      new CollectionChequePreviewService(access, obligations, cheques, targets, source);

  @BeforeEach
  void enterTenant() {
    TenantContext.setCurrentTenantId(tenant);
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    when(access.reach(actor, "read"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
  }

  @AfterEach
  void leaveTenant() {
    TenantContext.clear();
  }

  @Test
  void filtersBySharedCapabilityAndOrdersDatedThenUndatedWithOrderNumberAndIdTies() {
    var first = obligation("BO-01", date, date, true);
    var sameOrder = obligation("BO-01", date, date, true);
    ReflectionTestUtils.setField(first, "orderId", sameOrder.getOrderId());
    ReflectionTestUtils.setField(first, "id", new UUID(0, 1));
    ReflectionTestUtils.setField(sameOrder, "id", new UUID(0, 2));
    var repeatOrder = obligation("BO-02", date, date, true);
    var undated = obligation("BO-03", null, date, true);
    var laterDue = obligation("BO-04", date, date.plusDays(2), true);
    var earlierExpected = obligation("BO-05", date.minusDays(1), null, true);
    var covered = obligation("BO-06", date, date, true);
    var settled = obligation("BO-07", date, date, true);
    var sourceMissing = obligation("BO-08", date, date, true);
    var forbidden = obligation("BO-09", date, date, false);
    var cancelled = obligation("BO-10", date, date, true);
    ReflectionTestUtils.setField(cancelled, "cancelled", true);
    var transfer = obligation("BO-11", date, date, true);
    ReflectionTestUtils.setField(transfer, "method", Method.BANK_TRANSFER);
    var rows =
        List.of(
            undated,
            sameOrder,
            forbidden,
            laterDue,
            first,
            repeatOrder,
            covered,
            settled,
            sourceMissing,
            cancelled,
            transfer,
            earlierExpected);
    when(obligations.findBy(
            org.mockito.ArgumentMatchers.<Specification<CollectionObligation>>any(), any()))
        .thenReturn(rows);
    var open =
        new CollectionChequeTargets.Facts(true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    var amount = new BigDecimal("500");
    when(targets.load(eq(tenant), anyList()))
        .thenAnswer(
            call -> {
              List<CollectionObligation> requested = call.getArgument(1);
              var facts = new java.util.HashMap<UUID, CollectionChequeTargets.Facts>();
              requested.forEach(row -> facts.put(row.getId(), open));
              facts.replace(
                  covered.getId(),
                  new CollectionChequeTargets.Facts(
                      true, BigDecimal.ZERO, amount, BigDecimal.ZERO));
              facts.replace(
                  settled.getId(),
                  new CollectionChequeTargets.Facts(
                      true, amount, BigDecimal.ZERO, BigDecimal.ZERO));
              facts.replace(
                  sourceMissing.getId(),
                  new CollectionChequeTargets.Facts(
                      false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
              return facts;
            });
    when(source.orderNumbers(eq(tenant), anyCollection()))
        .thenReturn(
            Map.of(
                first.getOrderId(),
                "SO-011",
                repeatOrder.getOrderId(),
                "SO-012",
                laterDue.getOrderId(),
                "SO-014",
                earlierExpected.getOrderId(),
                "SO-015"));
    var result =
        preview.preview(
            new PreviewRequest(partner, null, false, List.of(row("1", RowIntent.AUTO))), actor);
    assertThat(result.candidates().getFirst().obligations())
        .extracting(Target::obligationId)
        .containsExactly(
            earlierExpected.getId(),
            first.getId(),
            sameOrder.getId(),
            repeatOrder.getId(),
            laterDue.getId(),
            undated.getId());
    assertThat(result.rows().getFirst().applications().getFirst().target().orderNumber())
        .isEqualTo("SO-015");
    verify(source, times(1)).orderNumbers(eq(tenant), anyCollection());
    verifyNoMoreInteractions(source);
  }

  @Test
  void broaderWriteReachDoesNotDiscloseAnUnreadableFixedTarget() {
    when(access.reach(actor, "write"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    var visible = obligation("BO-01", date, date, true);
    var hidden = obligation("BO-02", date, date.minusDays(30), false);
    ReflectionTestUtils.setField(hidden, "orderId", visible.getOrderId());
    when(obligations.findBy(
            org.mockito.ArgumentMatchers.<Specification<CollectionObligation>>any(), any()))
        .thenReturn(List.of(visible, hidden));
    var open =
        new CollectionChequeTargets.Facts(true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    when(targets.load(eq(tenant), anyList()))
        .thenReturn(Map.of(visible.getId(), open, hidden.getId(), open));
    when(targets.load(tenant, List.of(visible))).thenReturn(Map.of(visible.getId(), open));
    when(source.orderNumbers(eq(tenant), anyCollection()))
        .thenReturn(Map.of(visible.getOrderId(), "SO-011"));
    var fixed =
        new PreviewRowInput(
            "2",
            "CH-2",
            "GBP",
            BigDecimal.ONE,
            date,
            DrawerKind.OWN,
            RowIntent.FIXED,
            List.of(new ApplicationInput(hidden.getId(), BigDecimal.ONE)));

    var result =
        preview.preview(
            new PreviewRequest(partner, null, false, List.of(row("1", RowIntent.AUTO), fixed)),
            actor);

    assertThat(result.candidates().getFirst().obligations())
        .extracting(Target::obligationId)
        .containsExactly(visible.getId());
    assertThat(result.rows().get(1).applications())
        .containsExactly(new PreviewApplication(hidden.getId(), "1", null));
    assertThat(result.rows().get(1).issues())
        .containsExactly(new RowIssue(IssueCode.CHEQUE_APPLICATION_INCOMPATIBLE, hidden.getId()));
    assertThat(result.rows().get(1).warnings()).isEmpty();
    assertThat(result.comparisons().getFirst().partial()).isTrue();
    assertThat(result.comparisons().getFirst().agreedAmount()).isEqualTo("500.00");
  }

  @Test
  void aFullBatchDoesNotHideAnEarlierTargetFromTheNextBatch() {
    var leading = obligation("First by date", date.minusDays(1), date, true);
    var batch =
        java.util.stream.IntStream.range(0, 500)
            .mapToObj(i -> obligation("Repeat", date, date, true))
            .toList();
    when(obligations.findBy(
            org.mockito.ArgumentMatchers.<Specification<CollectionObligation>>any(), any()))
        .thenReturn(batch, List.of(leading));
    var open =
        new CollectionChequeTargets.Facts(true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    when(targets.load(eq(tenant), anyList()))
        .thenAnswer(
            call -> {
              List<CollectionObligation> requested = call.getArgument(1);
              return requested.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          CollectionObligation::getId, ignored -> open));
            });
    var incomplete =
        new PreviewRowInput(
            "1", "CH-1", "GBP", null, date, DrawerKind.OWN, RowIntent.AUTO, List.of());

    var result =
        preview.preview(new PreviewRequest(partner, null, false, List.of(incomplete)), actor);

    assertThat(result.candidates().getFirst().truncated()).isTrue();
    assertThat(result.candidates().getFirst().obligations()).hasSize(500);
    assertThat(result.candidates().getFirst().obligations().getFirst().obligationId())
        .isEqualTo(leading.getId());
    verify(targets).load(tenant, batch);
    verify(targets).load(tenant, List.of(leading));
  }

  @Test
  void noAllocationDoesNotReadObligationsOrSource() {
    var result =
        preview.preview(
            new PreviewRequest(partner, UUID.randomUUID(), true, List.of(row("1", RowIntent.NONE))),
            actor);
    assertThat(result.candidates()).isEmpty();
    assertThat(result.totals().getFirst().unallocatedAmount()).isEqualTo("1.00");
    verifyNoInteractions(obligations, targets, source);
    verify(cheques).references(tenant, partner, Set.of("GBP"), Set.of("CH-1"));
    verifyNoMoreInteractions(cheques);
  }

  @Test
  void duplicateKeysAndNonFixedApplicationsAreRejectedBeforeReadingTargets() {
    var row = row("1", RowIntent.AUTO);
    assertThatThrownBy(
            () ->
                preview.preview(new PreviewRequest(partner, null, false, List.of(row, row)), actor))
        .isInstanceOf(CollectionRuleException.class);
    var contradictory =
        new PreviewRowInput(
            "2",
            "CH-2",
            "GBP",
            BigDecimal.ONE,
            date,
            DrawerKind.OWN,
            RowIntent.NONE,
            List.of(new ApplicationInput(UUID.randomUUID(), BigDecimal.ONE)));
    assertThatThrownBy(
            () ->
                preview.preview(
                    new PreviewRequest(partner, null, false, List.of(contradictory)), actor))
        .isInstanceOf(CollectionRuleException.class);
    verifyNoInteractions(obligations, targets, source, cheques);
  }

  @Test
  void writeGrantIsRequiredBeforeReadingAnyPreviewData() {
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, null, Set.of(actor)));
    assertThatThrownBy(
            () ->
                preview.preview(
                    new PreviewRequest(partner, null, false, List.of(row("1", RowIntent.NONE))),
                    actor))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(obligations, targets, source, cheques);
  }

  private PreviewRowInput row(String key, RowIntent intent) {
    return new PreviewRowInput(
        key, "CH-" + key, "GBP", BigDecimal.ONE, date, DrawerKind.OWN, intent, List.of());
  }

  private CollectionObligation obligation(
      String order, LocalDate expected, LocalDate due, boolean owned) {
    var row =
        CollectionChequeProposalTest.candidate(UUID.randomUUID(), order, "GBP", "500", due).row();
    ReflectionTestUtils.setField(row, "tenantId", tenant);
    ReflectionTestUtils.setField(row, "expectedDate", expected);
    ReflectionTestUtils.setField(row, "createdBy", owned ? actor : UUID.randomUUID());
    return row;
  }
}
