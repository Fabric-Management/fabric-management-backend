package com.fabricmanagement.production.execution.inventory.app.query;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.inventory.domain.InventoryBalance;
import com.fabricmanagement.production.execution.inventory.dto.InventoryBalanceDto;
import com.fabricmanagement.production.execution.inventory.infra.repository.InventoryBalanceRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryBalanceQueryService {

  private final InventoryBalanceRepository inventoryBalanceRepository;

  @Transactional(readOnly = true)
  public InventoryBalanceDto getByBatchAndLocation(UUID batchId, UUID locationId) {
    Optional<InventoryBalance> balanceOpt =
        locationId == null
            ? inventoryBalanceRepository.findByBatchIdAndLocationIdIsNull(batchId)
            : inventoryBalanceRepository.findByBatchIdAndLocationId(batchId, locationId);

    return balanceOpt
        .map(InventoryBalanceDto::from)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Inventory balance not found for batch "
                        + batchId
                        + " and location "
                        + locationId));
  }

  @Transactional(readOnly = true)
  public Page<InventoryBalanceDto> getByBatchId(UUID batchId, Pageable pageable) {
    return inventoryBalanceRepository
        .findByBatchId(batchId, pageable)
        .map(InventoryBalanceDto::from);
  }

  @Transactional(readOnly = true)
  public Page<InventoryBalanceDto> getByLocationId(UUID locationId, Pageable pageable) {
    return inventoryBalanceRepository
        .findByLocationId(locationId, pageable)
        .map(InventoryBalanceDto::from);
  }
}
