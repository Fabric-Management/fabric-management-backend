package com.fabricmanagement.production.execution.output.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.common.exception.ProductionDomainException;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputItem;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputRecord;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputStatus;
import com.fabricmanagement.production.execution.output.domain.event.ProductionOutputConfirmedEvent;
import com.fabricmanagement.production.execution.output.dto.AddOutputItemRequest;
import com.fabricmanagement.production.execution.output.dto.CreateProductionOutputRequest;
import com.fabricmanagement.production.execution.output.infra.repository.ProductionOutputItemRepository;
import com.fabricmanagement.production.execution.output.infra.repository.ProductionOutputRecordRepository;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionOutputService {

  private final ProductionOutputRecordRepository recordRepo;
  private final ProductionOutputItemRepository itemRepo;
  private final DomainEventPublisher eventPublisher;
  private final ProductFacade productFacade;

  @Transactional
  public ProductionOutputRecord create(CreateProductionOutputRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    ProductDto product =
        productFacade
            .findById(tenantId, request.outputProductId())
            .orElseThrow(
                () ->
                    new ProductionDomainException(
                        "Product not found: " + request.outputProductId()));

    ProductionOutputRecord record =
        ProductionOutputRecord.create(
            tenantId,
            request.workOrderId(),
            request.workOrderNumber(),
            request.batchId(),
            request.outputProductId(),
            request.outputProductType(),
            product.getUnit(),
            request.notes());

    return recordRepo.save(record);
  }

  @Transactional
  public ProductionOutputRecord addItem(UUID recordId, AddOutputItemRequest request) {
    UUID tenantId = TenantContext.requireTenantId();

    ProductionOutputRecord record =
        recordRepo
            .findByIdAndTenantIdAndIsActiveTrue(recordId, tenantId)
            .orElseThrow(() -> new ProductionDomainException("Output record not found"));

    ProductionOutputItem item =
        ProductionOutputItem.create(
            request.packageType(),
            request.netWeight(),
            request.grossWeight(),
            request.locationId(),
            0, // sequence to be updated on confirm
            request.notes());

    record.addItem(item);
    return recordRepo.save(record);
  }

  @Transactional
  public ProductionOutputRecord removeItem(UUID recordId, UUID itemId) {
    UUID tenantId = TenantContext.requireTenantId();

    ProductionOutputRecord record =
        recordRepo
            .findByIdAndTenantIdAndIsActiveTrue(recordId, tenantId)
            .orElseThrow(() -> new ProductionDomainException("Output record not found"));

    record.removeItem(itemId);
    return recordRepo.save(record);
  }

  @Transactional
  public ProductionOutputRecord confirm(UUID recordId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID userId = TenantContext.getCurrentUserId();

    ProductionOutputRecord record =
        recordRepo
            .findByIdAndTenantIdAndIsActiveTrue(recordId, tenantId)
            .orElseThrow(() -> new ProductionDomainException("Output record not found"));

    int existingCount = 0;
    if (record.getWorkOrderId() != null) {
      existingCount =
          itemRepo.countConfirmedItemsByWorkOrderId(
              tenantId, record.getWorkOrderId(), ProductionOutputStatus.CONFIRMED);
    }

    int currentSeq = existingCount + 1;
    for (ProductionOutputItem item : record.getItems()) {
      item.setSequenceNo(currentSeq);

      String barcode;
      if (record.getWorkOrderNumber() != null && !record.getWorkOrderNumber().isBlank()) {
        barcode = String.format("WO-%s-%04d", record.getWorkOrderNumber(), currentSeq);
      } else {
        barcode = String.format("OUT-%s-%04d", record.getUid(), currentSeq);
      }

      if (itemRepo.existsByTenantIdAndBarcodeAndIsActiveTrue(tenantId, barcode)) {
        throw new ProductionDomainException("Barcode already exists: " + barcode);
      }

      item.setBarcode(barcode);
      currentSeq++;
    }

    ProductionOutputConfirmedEvent event = record.confirm(userId);
    ProductionOutputRecord saved = recordRepo.save(record);
    eventPublisher.publish(event);

    return saved;
  }

  @Transactional(readOnly = true)
  public ProductionOutputRecord getById(UUID recordId) {
    UUID tenantId = TenantContext.requireTenantId();
    return recordRepo
        .findByIdAndTenantIdAndIsActiveTrue(recordId, tenantId)
        .orElseThrow(() -> new ProductionDomainException("Output record not found"));
  }

  @Transactional(readOnly = true)
  public List<ProductionOutputRecord> getByWorkOrderId(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    return recordRepo.findByTenantIdAndWorkOrderIdAndIsActiveTrue(tenantId, workOrderId);
  }
}
