package com.fabricmanagement.production.core.batch.app.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.app.ProductEvidenceQueryService;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.core.batch.app.*;
import com.fabricmanagement.production.core.batch.domain.*;
import com.fabricmanagement.production.core.batch.infra.repository.*;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrderCoverEvidenceAdapterLockTest {
  @Test
  void nativeLockTablesTrackEntityMappingsAndUseOnlyFourOrderedBulkReads() {
    UUID tenant = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    try {
      var manager = mock(EntityManager.class);
      List<String> statements = new ArrayList<>();
      when(manager.createNativeQuery(anyString()))
          .thenAnswer(
              call -> {
                String sql = call.getArgument(0);
                statements.add(sql);
                Query query = mock(Query.class);
                when(query.setParameter(anyString(), any())).thenReturn(query);
                if (sql.contains("production_execution_batch ")) {
                  when(query.getResultList())
                      .thenReturn(Collections.singletonList(new Object[] {UUID.randomUUID(), 0L}));
                } else {
                  when(query.getResultList()).thenReturn(List.of());
                }
                return query;
              });
      var adapter =
          new OrderCoverEvidenceAdapter(
              mock(StockAvailabilityQueryService.class),
              new BatchPrimaryMeasureService(),
              mock(BatchRepository.class),
              mock(StockUnitRepository.class),
              mock(BatchLotQuantityIntentRepository.class),
              mock(BatchReservationRepository.class),
              mock(QualityGradeQueryService.class),
              mock(ProductEvidenceQueryService.class),
              manager);
      var requirements =
          new Requirements(
              tenant,
              UUID.randomUUID(),
              UUID.randomUUID(),
              0,
              List.of(
                  new Requirement(
                      UUID.randomUUID(),
                      0,
                      UUID.randomUUID(),
                      Instant.EPOCH,
                      BigDecimal.ONE,
                      "M",
                      true,
                      null,
                      "test",
                      null)));
      // The mocked locked batch is intentionally missing from the managed population.
      assertThatThrownBy(() -> adapter.lockAndInspect(requirements))
          .isInstanceOf(OptimisticLockException.class);
      assertThat(statements)
          .containsExactly(
              "select set_config('lock_timeout', '5s', true)",
              expectedSql(Batch.class, "product_id"),
              expectedSql(StockUnit.class, "batch_id"),
              expectedSql(BatchLotQuantityIntent.class, "batch_id"),
              expectedSql(BatchReservation.class, "batch_id"));
      verify(manager).flush();
      verify(manager, never()).refresh(any(), any(LockModeType.class));
      verify(manager, never()).clear();
    } finally {
      TenantContext.clear();
    }
  }

  private String expectedSql(Class<?> entity, String foreignKey) {
    Table table = entity.getAnnotation(Table.class);
    return "select id, version from "
        + table.schema()
        + "."
        + table.name()
        + " where tenant_id = :tenant and is_active = true and "
        + foreignKey
        + " in (:ids) order by id for update";
  }
}
