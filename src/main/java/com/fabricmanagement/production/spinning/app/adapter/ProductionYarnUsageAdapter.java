package com.fabricmanagement.production.spinning.app.adapter;

import com.fabricmanagement.product.yarn.app.port.YarnUsageDiscovery;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignal;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignalSource;
import com.fabricmanagement.production.core.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.core.inventory.infra.repository.InventoryTransactionRepository;
import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductionYarnUsageAdapter implements YarnUsageSignalSource {

  public static final Set<InventoryTransactionType> PHYSICAL_MOVEMENT_TYPES =
      Set.of(
          InventoryTransactionType.RECEIPT,
          InventoryTransactionType.SPLIT_IN,
          InventoryTransactionType.TRANSFER_IN,
          InventoryTransactionType.RETURN,
          InventoryTransactionType.SHIPMENT_RETURN,
          InventoryTransactionType.CONSUMPTION,
          InventoryTransactionType.WASTE,
          InventoryTransactionType.SPLIT_OUT,
          InventoryTransactionType.TRANSFER_OUT,
          InventoryTransactionType.SAMPLE,
          InventoryTransactionType.SHIPMENT_DISPATCH,
          InventoryTransactionType.ADJUSTMENT);

  private final WorkOrderRepository workOrderRepository;
  private final InventoryTransactionRepository inventoryTransactionRepository;
  private final Clock clock;

  @Override
  public YarnUsageDiscovery discover(UUID tenantId) {
    Instant cutoff =
        clock.instant().minus(YarnUsageSignalSource.MOVEMENT_WINDOW_DAYS, ChronoUnit.DAYS);
    EnumMap<YarnUsageSignal, Set<UUID>> referenced = new EnumMap<>(YarnUsageSignal.class);
    referenced.put(
        YarnUsageSignal.OPEN_WORK_ORDER,
        Set.copyOf(workOrderRepository.findOpenReferencedProductIds(tenantId)));
    referenced.put(
        YarnUsageSignal.RECENT_BATCH_MOVEMENT,
        Set.copyOf(
            inventoryTransactionRepository.findRecentlyMovedProductIds(
                tenantId,
                cutoff,
                PHYSICAL_MOVEMENT_TYPES.stream().map(Enum::name).collect(Collectors.toSet()))));
    Map<YarnUsageSignal, Long> unlinked =
        Map.of(
            YarnUsageSignal.OPEN_WORK_ORDER,
            workOrderRepository.countOpenUnlinkedSpinningWorkOrders(tenantId));
    return new YarnUsageDiscovery(referenced, unlinked);
  }
}
