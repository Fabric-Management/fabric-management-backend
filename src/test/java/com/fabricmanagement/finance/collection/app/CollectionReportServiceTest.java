package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.finance.collection.domain.CollectionReportFacts.Row;
import com.fabricmanagement.finance.collection.infra.repository.CollectionReportRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

class CollectionReportServiceTest {
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), partner = UUID.randomUUID();
  final CollectionReportRepository repo = mock(CollectionReportRepository.class);
  final CollectionAccessService access = mock(CollectionAccessService.class);
  final TradingPartnerResolver partners = mock(TradingPartnerResolver.class);
  final UserQueryService users = mock(UserQueryService.class);
  final Instant now = Instant.parse("2026-11-03T12:00:00Z");
  final CollectionReconciliationHealthService health =
      mock(CollectionReconciliationHealthService.class);
  final CollectionReportService service =
      new CollectionReportService(
          repo,
          access,
          () -> ZoneId.of("Europe/London"),
          partners,
          users,
          Clock.fixed(now, ZoneOffset.UTC),
          health);

  @BeforeEach
  void setup() {
    when(access.reach(actor, "read"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));
    when(repo.rows(any())).thenReturn(List.of());
    when(repo.totals(any())).thenReturn(List.of());
    when(partners.resolveDisplayNames(any(), any())).thenReturn(Map.of());
    when(users.findRecordedActorNames(any(), any())).thenReturn(Map.of());
  }

  CollectionReportService.Filter filter(LocalDate date, Instant cutoff) {
    return new CollectionReportService.Filter(
        date, cutoff, null, null, null, null, null, null, null, 0, 20);
  }

  @Test
  void tenantDayUsesLondonDstAndScopeIsNotElevatedForHistoricalCuts() {
    var report = service.report(actor, filter(LocalDate.parse("2026-10-24"), now));
    var captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
    verify(repo).rows(captor.capture());
    assertThat(captor.getValue().getValue("endAt"))
        .isEqualTo(Timestamp.from(Instant.parse("2026-10-24T23:00:00Z")));
    assertThat(captor.getValue().getValue("allScope")).isEqualTo(false);
    assertThat(captor.getValue().getValue("principals")).isEqualTo(Set.of(actor));
    assertThat(report.legacyOrderCount()).isNull();
    assertThat(report.warnings())
        .contains(
            "NO_INVOICE_FORECAST_INCLUDED",
            "NO_FX_CONSOLIDATED_TOTAL",
            "LEGACY_AND_UNPROJECTED_COVERAGE_UNKNOWN");
  }

  @Test
  void deniedCurrentPermissionCannotReadEvenAnOldCut() {
    when(access.reach(actor, "read"))
        .thenReturn(new CollectionAccessService.Reach(tenant, null, Set.of(actor)));
    assertThatThrownBy(() -> service.report(actor, filter(LocalDate.parse("2026-10-20"), now)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(repo);
  }

  @Test
  void rejectsFutureKnowledgeAndInvalidPagesWithoutQuerying() {
    assertThatThrownBy(() -> service.report(actor, filter(null, now.plusSeconds(1))))
        .isInstanceOf(RuntimeException.class);
    var invalid =
        new CollectionReportService.Filter(
            null, null, null, null, null, "GBP", null, null, null, 0, 101);
    assertThatThrownBy(() -> service.report(actor, invalid)).isInstanceOf(RuntimeException.class);
    verifyNoInteractions(repo);
  }

  @Test
  void batchNamesAreCurrentAndNeverGuessedWhenMissing() {
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    var rows = List.of(row(first), row(second));
    when(repo.rows(any())).thenReturn(rows);
    when(partners.resolveDisplayNames(tenant, List.of(partner)))
        .thenReturn(Map.of(partner, "Northbridge Cloth"));
    when(users.findRecordedActorNames(tenant, List.of(actor)))
        .thenReturn(Map.of(actor, "Harriet Ward"));
    var result = service.report(actor, filter(null, null));
    assertThat(result.content())
        .extracting(
            com.fabricmanagement.finance.collection.dto.CollectionReportDtos.Row::customerLabel)
        .containsExactly("Northbridge Cloth", "Northbridge Cloth");
    assertThat(result.content())
        .extracting(
            com.fabricmanagement.finance.collection.dto.CollectionReportDtos.Row
                ::responsibleDisplayName)
        .containsOnly("Harriet Ward");
    verify(partners, times(1)).resolveDisplayNames(tenant, List.of(partner));
    verify(users, times(1)).findRecordedActorNames(tenant, List.of(actor));
  }

  private Row row(UUID id) {
    return new Row(
        id,
        UUID.randomUUID(),
        partner,
        null,
        actor,
        null,
        "GBP",
        "BANK_TRANSFER",
        LocalDate.parse("2026-10-30"),
        null,
        "4284.00",
        "1000.00",
        "3284.00",
        "0",
        "0",
        "0",
        false,
        false,
        false,
        now,
        List.of("CASH_OVERDUE"),
        List.of());
  }

  @Test
  void theTotalIsTheSumOfTheCurrencyGroupsSoNoSeparateCountQueryRuns() {
    when(repo.totals(any()))
        .thenReturn(
            List.of(
                new com.fabricmanagement.finance.collection.domain.CollectionReportFacts.Currency(
                    "GBP", 3, 0, "1", "1", "1", "1", "1", "0"),
                new com.fabricmanagement.finance.collection.domain.CollectionReportFacts.Currency(
                    "USD", 2, 1, null, null, null, null, null, "0")));

    var report = service.report(actor, filter(null, null));

    assertThat(report.totalElements()).isEqualTo(5);
    assertThat(report.currencies()).hasSize(2);
  }

  @Test
  void incompleteFinanceDiscoveryIsVisibleEvenWithNoReportRows() {
    when(health.stale()).thenReturn(true);
    assertThat(service.report(actor, filter(null, null)).warnings())
        .contains("SOURCE_DISCOVERY_STALE");
  }
}
