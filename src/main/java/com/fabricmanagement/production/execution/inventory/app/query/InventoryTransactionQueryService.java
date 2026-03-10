package com.fabricmanagement.production.execution.inventory.app.query;

import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.execution.inventory.domain.enums.ReferenceType;
import com.fabricmanagement.production.execution.inventory.dto.InventoryTransactionDto;
import com.fabricmanagement.production.execution.inventory.infra.repository.InventoryTransactionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryTransactionQueryService {

  private final InventoryTransactionRepository inventoryTransactionRepository;

  @Transactional(readOnly = true)
  public Page<InventoryTransactionDto> getAll(Pageable pageable) {
    return inventoryTransactionRepository.findAll(pageable).map(InventoryTransactionDto::from);
  }

  @Transactional(readOnly = true)
  public Page<InventoryTransactionDto> getByBatchId(UUID batchId, Pageable pageable) {
    return inventoryTransactionRepository
        .findByBatchId(batchId, pageable)
        .map(InventoryTransactionDto::from);
  }

  @Transactional(readOnly = true)
  public Page<InventoryTransactionDto> getByBatchIdAndType(
      UUID batchId, InventoryTransactionType type, Pageable pageable) {
    return inventoryTransactionRepository
        .findByBatchIdAndTransactionType(batchId, type, pageable)
        .map(InventoryTransactionDto::from);
  }

  @Transactional(readOnly = true)
  public List<InventoryTransactionDto> getTransactionsByReference(
      UUID referenceId, String referenceType) {
    return inventoryTransactionRepository
        .findByReferenceIdAndReferenceType(referenceId, parseReferenceType(referenceType))
        .stream()
        .map(InventoryTransactionDto::from)
        .toList();
  }

  private ReferenceType parseReferenceType(String value) {
    if (value == null) {
      return null;
    }
    try {
      return ReferenceType.valueOf(value);
    } catch (IllegalArgumentException e) {
      log.warn("Unknown reference type '{}', defaulting to null", value);
      return null;
    }
  }
}
