package com.fabricmanagement.production.execution.inventory.app.command;

import com.fabricmanagement.production.execution.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.production.execution.inventory.domain.InventoryTransaction;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionReasonCode;
import com.fabricmanagement.production.execution.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.execution.inventory.domain.enums.ReferenceType;
import com.fabricmanagement.production.execution.inventory.domain.event.InventoryTransactionCreatedEvent;
import com.fabricmanagement.production.execution.inventory.infra.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryTransactionCommandService implements InventoryFacade {

  private final InventoryTransactionRepository inventoryTransactionRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public InventoryTransaction createTransaction(
      UUID tenantId,
      UUID batchId,
      InventoryTransactionType transactionType,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      ReferenceType referenceType,
      UUID referenceId,
      String remarks,
      InventoryTransactionReasonCode reasonCode,
      String idempotencyKey) {

    log.info(
        "Creating inventory transaction for batch: {}, type: {}, quantity: {}",
        batchId,
        transactionType,
        quantity);

    // Idempotency check handled by DB unique constraint (tenant_id, idempotency_key)

    InventoryTransaction transaction =
        InventoryTransaction.create(
            tenantId,
            batchId,
            transactionType,
            quantity,
            unit,
            locationId,
            referenceType,
            referenceId,
            Instant.now(),
            remarks,
            reasonCode,
            idempotencyKey);

    InventoryTransaction saved = inventoryTransactionRepository.save(transaction);

    eventPublisher.publishEvent(
        InventoryTransactionCreatedEvent.builder()
            .transactionId(saved.getId())
            .tenantId(saved.getTenantId())
            .batchId(saved.getBatchId())
            .transactionType(saved.getTransactionType())
            .quantity(saved.getQuantity())
            .unit(saved.getUnit())
            .locationId(saved.getLocationId())
            .transactionDate(saved.getTransactionDate())
            .build());

    return saved;
  }

  /**
   * Simplified logging facade used by domain services (e.g. BatchService). Accepts the domain-level
   * InventoryTransactionType and a string referenceType, mapping them to the persistence-layer
   * enums automatically.
   */
  @Transactional
  public void logTransaction(
      UUID tenantId,
      UUID batchId,
      InventoryTransactionType txType,
      BigDecimal quantity,
      String unit,
      UUID locationId,
      UUID referenceId,
      String referenceTypeStr,
      String remarks,
      InventoryTransactionReasonCode reasonCode,
      String idempotencyKey) {

    ReferenceType refType = parseReferenceType(referenceTypeStr);

    createTransaction(
        tenantId,
        batchId,
        txType,
        quantity,
        unit,
        locationId,
        refType,
        referenceId,
        remarks,
        reasonCode,
        idempotencyKey);
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
