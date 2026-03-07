package com.fabricmanagement.production.execution.inventory.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.fiber.domain.FiberBatch;
import com.fabricmanagement.production.execution.fiber.infra.repository.FiberBatchRepository;
import com.fabricmanagement.production.execution.inventory.domain.InventoryTransaction;
import com.fabricmanagement.production.execution.inventory.domain.InventoryTransactionType;
import com.fabricmanagement.production.execution.inventory.dto.CreateInventoryTransactionRequest;
import com.fabricmanagement.production.execution.inventory.dto.InventoryTransactionDto;
import com.fabricmanagement.production.execution.inventory.infra.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryTransactionService {

  private final InventoryTransactionRepository transactionRepository;
  private final FiberBatchRepository fiberBatchRepository;

  /**
   * Record a manual inventory transaction and apply side effects to the batch. WASTE transactions
   * update the batch's waste_quantity. Other types are logged without modifying batch aggregates
   * (consume/reserve use their dedicated FiberBatchService methods).
   */
  @Transactional
  public InventoryTransactionDto create(CreateInventoryTransactionRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Creating inventory transaction: tenantId={}, batchId={}, type={}, qty={}",
        tenantId,
        request.getBatchId(),
        request.getTransactionType(),
        request.getQuantity());

    FiberBatch batch =
        fiberBatchRepository
            .findByIdAndTenantId(request.getBatchId(), tenantId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + request.getBatchId()));

    if (request.getTransactionType() == InventoryTransactionType.WASTE) {
      batch.recordWaste(request.getQuantity());
      fiberBatchRepository.save(batch);
    }

    InventoryTransaction txn =
        InventoryTransaction.create(
            tenantId,
            request.getBatchId(),
            request.getTransactionType(),
            request.getQuantity(),
            request.getUnit(),
            request.getTransactionDate(),
            request.getReferenceId(),
            request.getReferenceType(),
            request.getReason(),
            request.getRemarks());

    txn = transactionRepository.save(txn);
    log.info(
        "Inventory transaction created: id={}, batch={}, type={}, qty={} {}",
        txn.getId(),
        txn.getBatchId(),
        txn.getTransactionType(),
        txn.getQuantity(),
        txn.getUnit());

    return InventoryTransactionDto.from(txn);
  }

  /**
   * Internal method: log a transaction without side effects. Used by FiberBatchService to
   * automatically log consume/reserve/release operations.
   */
  @Transactional
  public InventoryTransaction logTransaction(
      UUID tenantId,
      UUID batchId,
      InventoryTransactionType type,
      BigDecimal quantity,
      String unit,
      UUID referenceId,
      String referenceType,
      String reason) {

    InventoryTransaction txn =
        InventoryTransaction.create(
            tenantId,
            batchId,
            type,
            quantity,
            unit,
            Instant.now(),
            referenceId,
            referenceType,
            reason,
            null);

    return transactionRepository.save(txn);
  }

  @Transactional(readOnly = true)
  public List<InventoryTransactionDto> getByBatchId(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return transactionRepository
        .findByTenantIdAndBatchIdAndIsActiveTrueOrderByTransactionDateDesc(tenantId, batchId)
        .stream()
        .map(InventoryTransactionDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<InventoryTransactionDto> getByBatchIdAndType(
      UUID batchId, InventoryTransactionType type) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return transactionRepository
        .findByTenantIdAndBatchIdAndTransactionTypeAndIsActiveTrue(tenantId, batchId, type)
        .stream()
        .map(InventoryTransactionDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<InventoryTransactionDto> getAll() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return transactionRepository
        .findByTenantIdAndIsActiveTrueOrderByTransactionDateDesc(tenantId)
        .stream()
        .map(InventoryTransactionDto::from)
        .toList();
  }
}
