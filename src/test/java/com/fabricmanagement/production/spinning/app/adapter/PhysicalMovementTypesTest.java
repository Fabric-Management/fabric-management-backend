package com.fabricmanagement.production.spinning.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.inventory.app.command.InventoryBalanceUpdater;
import com.fabricmanagement.production.core.inventory.domain.InventoryBalance;
import com.fabricmanagement.production.core.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.core.inventory.domain.event.InventoryTransactionCreatedEvent;
import com.fabricmanagement.production.core.inventory.infra.repository.InventoryBalanceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhysicalMovementTypesTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();

  @Mock private InventoryBalanceRepository balanceRepository;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void allowlistEqualsExactlyTheTypesThatChangeOnHandQuantity() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    Set<InventoryTransactionType> changedQuantity = EnumSet.noneOf(InventoryTransactionType.class);

    for (InventoryTransactionType type : InventoryTransactionType.values()) {
      reset(balanceRepository);
      InventoryBalance balance = InventoryBalance.create(TENANT_ID, BATCH_ID, null, "KG");
      when(balanceRepository.findByBatchIdAndLocationIdIsNull(BATCH_ID))
          .thenReturn(Optional.of(balance));
      when(balanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      BigDecimal before = balance.getQuantity();

      new InventoryBalanceUpdater(balanceRepository)
          .onTransactionCreated(
              InventoryTransactionCreatedEvent.builder()
                  .tenantId(TENANT_ID)
                  .transactionId(UUID.randomUUID())
                  .batchId(BATCH_ID)
                  .transactionType(type)
                  .quantity(BigDecimal.ONE)
                  .unit("KG")
                  .transactionDate(Instant.parse("2026-09-01T00:00:00Z"))
                  .build());

      if (balance.getQuantity().compareTo(before) != 0) {
        changedQuantity.add(type);
      }
    }

    assertThat(ProductionYarnUsageAdapter.PHYSICAL_MOVEMENT_TYPES)
        .containsExactlyInAnyOrderElementsOf(changedQuantity);
  }
}
