package com.fabricmanagement.production.core.stockunit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCut;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StockUnitCutServiceTest {
  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void verificationWritesCurrentSeventyAndPreservesInitialHundred() {
    UUID tenant = UUID.randomUUID(),
        actor = UUID.randomUUID(),
        id = UUID.randomUUID(),
        cutId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentUserId(actor);
    Instant now = Instant.parse("2026-10-18T10:00:00Z");
    StockUnit unit = StockUnit.builder().build();
    unit.recordInitialLength(new BigDecimal("100"), "M");
    unit.setId(id);
    StockUnitCut cut =
        StockUnitCut.record(id, new BigDecimal("30"), new BigDecimal("70"), "M", actor, now);
    cut.setId(cutId);
    StockUnitRepository units = mock(StockUnitRepository.class);
    StockUnitCutRepository cuts = mock(StockUnitCutRepository.class);
    when(units.findByIdAndTenantIdAndIsActiveTrue(id, tenant)).thenReturn(Optional.of(unit));
    when(cuts.findByTenantIdAndIdAndStockUnitId(tenant, cutId, id)).thenReturn(Optional.of(cut));
    when(cuts.save(any())).thenAnswer(call -> call.getArgument(0));
    assertThat(unit.getLength()).isEqualByComparingTo("100");
    assertThat(cut.isRemainingVerified()).isFalse();
    new StockUnitCutService(
            units,
            cuts,
            Clock.fixed(now, ZoneOffset.UTC),
            mock(com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort.class))
        .verifyRemaining(id, cutId);
    assertThat(unit.getLength()).isEqualByComparingTo("70");
    assertThat(unit.getInitialLength()).isEqualByComparingTo("100");
    assertThat(cut.isRemainingVerified()).isTrue();
  }
}
