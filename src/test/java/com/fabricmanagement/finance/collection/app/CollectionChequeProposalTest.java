package com.fabricmanagement.finance.collection.app;

import static com.fabricmanagement.finance.collection.dto.ChequeHandoverDtos.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.security.CollectionAccessService.Reach;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CollectionChequeProposalTest {
  private static final LocalDate DECEMBER = LocalDate.of(2026, 12, 31);
  private static final LocalDate MARCH = LocalDate.of(2027, 3, 1);
  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID PARTNER = UUID.randomUUID();
  private static final UUID ORDER = UUID.randomUUID();
  private static final Reach READ_ALL = new Reach(TENANT, DataScope.ORGANIZATION, Set.of(ACTOR));

  @Test
  void consumesChequesInEntryOrderAndOnlyAgainstTheirOwnCurrency() {
    var first = candidate(ORDER, "SO-001", "GBP", "5000", DECEMBER);
    var second = candidate(ORDER, "SO-001", "GBP", "5000", MARCH);
    var euro = candidate(ORDER, "SO-001", "EUR", "50", MARCH);
    var preview =
        new CollectionChequeProposal(
                List.of(
                    row("1", "3000", MARCH),
                    row("2", "3000", DECEMBER),
                    row("3", "4100", MARCH),
                    new PreviewRowInput(
                        "4",
                        "CH-4",
                        "EUR",
                        money("60"),
                        DECEMBER,
                        DrawerKind.ENDORSED,
                        RowIntent.AUTO,
                        List.of())),
                List.of(first, second, euro),
                Set.of())
            .result(List.of());
    assertThat(preview.rows().get(0).applications()).containsExactly(application(first, "3000.00"));
    assertThat(preview.rows().get(1).applications())
        .containsExactly(application(first, "2000.00"), application(second, "1000.00"));
    assertThat(preview.rows().get(2).applications())
        .containsExactly(application(second, "4000.00"));
    assertThat(preview.rows().get(2).unallocatedAmount()).isEqualTo("100.00");
    assertThat(preview.rows().get(3).applications()).containsExactly(application(euro, "50.00"));
    assertThat(preview.totals()).extracting(CurrencyTotal::currency).containsExactly("EUR", "GBP");
    assertThat(preview.totals().getFirst().endorsedTotal()).isEqualTo("60.00");
    assertThat(preview.totals().getFirst().unallocatedAmount()).isEqualTo("10.00");
  }

  @Test
  void reservesFixedRowsBeforeEarlierAutoRowsAndNeverAllocatesNoneRows() {
    var first = candidate(ORDER, "SO-001", "GBP", "500", DECEMBER);
    var second = candidate(UUID.randomUUID(), "SO-002", "GBP", "100", MARCH);
    var fixed =
        new PreviewRowInput(
            "2",
            "CH-2",
            "GBP",
            money("500"),
            MARCH,
            DrawerKind.OWN,
            RowIntent.FIXED,
            List.of(new ApplicationInput(first.id(), money("400"))));
    var none =
        new PreviewRowInput(
            "3",
            "CH-3",
            "GBP",
            money("200"),
            DECEMBER,
            DrawerKind.ENDORSED,
            RowIntent.NONE,
            List.of());
    var proposal =
        new CollectionChequeProposal(
                List.of(row("1", "200", DECEMBER), fixed, none), List.of(first, second), Set.of())
            .result(List.of());
    assertThat(proposal.rows().getFirst().applications())
        .containsExactly(application(first, "100.00"), application(second, "100.00"));
    assertThat(proposal.rows().get(1).applications()).containsExactly(application(first, "400"));
    assertThat(proposal.rows().get(2).applications()).isEmpty();
    assertThat(proposal.rows().get(2).unallocatedAmount()).isEqualTo("200.00");

    var shrunk =
        new CollectionChequeProposal.Candidate(first.row(), first.orderNumber(), money("300"));
    var changed =
        new CollectionChequeProposal(
                List.of(row("1", "200", DECEMBER), fixed, none), List.of(shrunk, second), Set.of())
            .result(List.of());
    assertThat(changed.rows().getFirst().applications())
        .containsExactly(application(second, "100.00"));
    assertThat(changed.rows().get(1).applications()).containsExactly(application(shrunk, "400"));
    assertThat(changed.rows().get(1).issues())
        .containsExactly(new RowIssue(IssueCode.CHEQUE_OVERAPPLICATION, first.id()));
  }

  @Test
  void excludesIncompleteRowsAndWarnsOnTrimmedDuplicatesWithNoAllocation() {
    var rows =
        List.of(
            new PreviewRowInput(
                "1",
                " CH-51  ",
                "GBP",
                money("300"),
                DECEMBER,
                DrawerKind.OWN,
                RowIntent.NONE,
                List.of()),
            new PreviewRowInput(
                "2", "CH-51", "GBP", null, MARCH, DrawerKind.OWN, RowIntent.NONE, List.of()),
            new PreviewRowInput(
                "3",
                "CH-52",
                "GBP",
                money("100"),
                DECEMBER,
                DrawerKind.ENDORSED,
                RowIntent.NONE,
                List.of()));
    var preview =
        new CollectionChequeProposal(
                rows, List.of(), Set.of(new CollectionChequeProposal.Reference("GBP", "CH-52")))
            .result(List.of());
    assertThat(preview.candidates()).isEmpty();
    assertThat(preview.rows().get(1).incomplete()).isTrue();
    assertThat(preview.rows().get(1).unallocatedAmount()).isNull();
    assertThat(preview.rows())
        .allSatisfy(
            row -> {
              assertThat(row.applications()).isEmpty();
              assertThat(row.issues()).isEmpty();
              assertThat(row.warnings())
                  .containsExactly(
                      new RowWarning(WarningCode.POSSIBLE_DUPLICATE_CHEQUE_REFERENCE, null, null));
            });
    assertThat(preview.totals())
        .containsExactly(
            new CurrencyTotal("GBP", 2, "400.00", "300.00", "100.00", "400.00", DECEMBER));
  }

  @Test
  void separatesCandidateTruncationFromTheBundleApplicationLimitAndExhaustion() {
    var candidates =
        IntStream.rangeClosed(1, 501)
            .mapToObj(i -> candidate(ORDER, "SO-001", "GBP", "1", DECEMBER))
            .toList();
    var limited =
        new CollectionChequeProposal(List.of(row("1", "501", DECEMBER)), candidates, Set.of())
            .result(List.of());
    assertThat(limited.notice()).isEqualTo(PreviewNotice.CHEQUE_HANDOVER_APPLICATION_LIMIT);
    assertThat(limited.rows().getFirst().applications()).hasSize(500);
    assertThat(limited.rows().getFirst().unallocatedAmount()).isEqualTo("1.00");
    assertThat(limited.candidates().getFirst().truncated()).isTrue();
    assertThat(limited.candidates().getFirst().obligations()).hasSize(500);
    var truncated =
        new CollectionChequeProposal(List.of(row("1", "499", DECEMBER)), candidates, Set.of())
            .result(List.of());
    assertThat(truncated.notice()).isNull();
    assertThat(truncated.candidates().getFirst().truncated()).isTrue();
    var exhausted =
        new CollectionChequeProposal(
                List.of(row("1", "501", DECEMBER)), candidates.subList(0, 500), Set.of())
            .result(List.of());
    assertThat(exhausted.notice()).isNull();
    assertThat(exhausted.candidates().getFirst().truncated()).isFalse();
  }

  @Test
  void incompleteFixedRowsEchoEntriesWithoutUsingAnAutomaticProposalSlot() {
    var candidates =
        IntStream.rangeClosed(1, 500)
            .mapToObj(i -> candidate(ORDER, "SO-001", "GBP", "1", DECEMBER))
            .toList();
    var fixed =
        new PreviewRowInput(
            "2",
            "CH-2",
            "GBP",
            money("1"),
            null,
            DrawerKind.OWN,
            RowIntent.FIXED,
            List.of(new ApplicationInput(candidates.getFirst().id(), money("1"))));

    var result =
        new CollectionChequeProposal(
                List.of(row("1", "500", DECEMBER), fixed), candidates, Set.of())
            .result(List.of());

    assertThat(result.rows().getFirst().applications()).hasSize(500);
    assertThat(result.rows().get(1).applications())
        .containsExactly(application(candidates.getFirst(), "1"));
    assertThat(result.rows().get(1).incomplete()).isTrue();
    assertThat(result.notice()).isNull();
  }

  @Test
  void comparesTheSameAppliedMoneyAndRoundsCalendarDaysHalfUp() {
    var first = candidate(ORDER, "SO-001", "GBP", "5000", DECEMBER);
    var second = candidate(ORDER, "SO-001", "GBP", "5000", MARCH);
    var rows = List.of(first.row(), second.row());
    var zero = balance("0", "0");
    var facts = Map.of(first.id(), zero, second.id(), zero);
    var full =
        new CollectionChequeProposal(
            List.of(row("1", "1000", DECEMBER), row("2", "9000", MARCH)),
            List.of(first, second),
            Set.of());
    var result = full.result(full.compare(rows, READ_ALL, facts));
    assertThat(result.totals().getFirst().averageDueOn()).isEqualTo("2027-02-23");
    assertThat(result.comparisons())
        .containsExactly(
            new MaturityComparison(
                ORDER,
                "SO-001",
                "GBP",
                false,
                "10000.00",
                "0.00",
                "10000.00",
                "0.00",
                LocalDate.of(2027, 1, 30),
                LocalDate.of(2027, 2, 23),
                24L));
    assertThat(result.rows().get(1).warnings())
        .containsExactly(
            new RowWarning(WarningCode.CHEQUE_DUE_AFTER_AGREED_DATE, first.id(), DECEMBER));

    var shortHandover =
        new CollectionChequeProposal(
            List.of(row("1", "3000", DECEMBER.minusDays(16))), List.of(first, second), Set.of());
    assertThat(shortHandover.compare(rows, READ_ALL, facts))
        .containsExactly(
            new MaturityComparison(
                ORDER,
                "SO-001",
                "GBP",
                false,
                "10000.00",
                "0.00",
                "3000.00",
                "0.00",
                DECEMBER,
                DECEMBER.minusDays(16),
                -16L));
    var halfDay =
        new CollectionChequeProposal(
                List.of(row("1", "1.01", DECEMBER), row("2", "1.01", DECEMBER.plusDays(1))),
                List.of(),
                Set.of())
            .result(List.of());
    assertThat(halfDay.totals().getFirst().averageDueOn()).isEqualTo(DECEMBER.plusDays(1));
  }

  @Test
  void splitsOrderComparisonsOmitsUndatedWeightsAndKeepsHiddenAgreementAmountsOut() {
    var first = candidate(ORDER, "SO-001", "GBP", "4000", DECEMBER.plusDays(30));
    var hidden = candidate(ORDER, "SO-001", "GBP", "6000", MARCH);
    var second = candidate(UUID.randomUUID(), "SO-002", "GBP", "2500", null);
    ReflectionTestUtils.setField(first.row(), "createdBy", ACTOR);
    ReflectionTestUtils.setField(second.row(), "createdBy", ACTOR);
    var none =
        new PreviewRowInput(
            "2", "CH-2", "GBP", money("1000"), MARCH, DrawerKind.OWN, RowIntent.NONE, List.of());
    var february = LocalDate.of(2027, 2, 23);
    var draft =
        new CollectionChequeProposal(
            List.of(row("1", "5000", february), none),
            List.of(
                new CollectionChequeProposal.Candidate(
                    first.row(), first.orderNumber(), money("3000")),
                second),
            Set.of());
    var comparison =
        draft.compare(
            List.of(first.row(), hidden.row(), second.row()),
            new Reach(TENANT, DataScope.OWN, Set.of(ACTOR)),
            Map.of(first.id(), balance("1000", "0"), second.id(), balance("0", "0")));
    assertThat(comparison)
        .containsExactly(
            new MaturityComparison(
                ORDER,
                "SO-001",
                "GBP",
                true,
                "4000.00",
                "1000.00",
                "3000.00",
                "0.00",
                DECEMBER.plusDays(30),
                february,
                24L),
            new MaturityComparison(
                second.row().getOrderId(),
                "SO-002",
                "GBP",
                false,
                "2500.00",
                "0.00",
                "2000.00",
                "2000.00",
                null,
                null,
                null));
    assertThat(draft.result(comparison).totals().getFirst().averageDueOn())
        .isEqualTo(february.plusDays(1));
  }

  static CollectionChequeProposal.Candidate candidate(
      UUID order, String number, String currency, String amount, LocalDate agreed) {
    var row =
        CollectionObligation.create(order, UUID.randomUUID(), UUID.randomUUID(), PARTNER, currency);
    ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(row, "tenantId", TENANT);
    ReflectionTestUtils.setField(row, "version", 2L);
    row.revise(
        UUID.randomUUID(),
        money(amount),
        Kind.INSTALMENT,
        Method.CHEQUE,
        DECEMBER,
        false,
        agreed,
        DECEMBER.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
        false);
    return new CollectionChequeProposal.Candidate(row, number, money(amount));
  }

  private static PreviewRowInput row(String key, String amount, LocalDate due) {
    return new PreviewRowInput(
        key, "CH-" + key, "GBP", money(amount), due, DrawerKind.OWN, RowIntent.AUTO, List.of());
  }

  private static PreviewApplication application(
      CollectionChequeProposal.Candidate target, String amount) {
    return new PreviewApplication(target.id(), amount, target.view());
  }

  private static CollectionChequeTargets.Facts balance(String received, String cash) {
    return new CollectionChequeTargets.Facts(true, BigDecimal.ZERO, money(received), money(cash));
  }

  private static BigDecimal money(String amount) {
    return new BigDecimal(amount);
  }
}
