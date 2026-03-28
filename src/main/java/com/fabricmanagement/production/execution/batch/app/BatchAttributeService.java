package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchAttribute;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.dto.AddBatchAttributeRequest;
import com.fabricmanagement.production.execution.batch.dto.BatchAttributeDto;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchAttributeRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberAttribute;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberAttributeRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAttributeService {

  private final BatchAttributeRepository attributeRepository;
  private final BatchRepository batchRepository;
  private final FiberAttributeRepository fiberAttributeRepository;

  @Transactional(readOnly = true)
  public List<BatchAttributeDto> findByBatchId(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

    return attributeRepository.findByBatch_IdAndIsActiveTrue(batch.getId()).stream()
        .map(BatchAttributeDto::from)
        .toList();
  }

  @Transactional
  public BatchAttributeDto add(UUID batchId, AddBatchAttributeRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

    FiberAttribute attribute =
        fiberAttributeRepository
            .findById(request.getAttributeId())
            .orElseThrow(
                () -> new NotFoundException("Attribute not found: " + request.getAttributeId()));

    if (attributeRepository
        .findByBatch_IdAndAttribute_Id(batch.getId(), attribute.getId())
        .isPresent()) {
      throw new BatchDomainException(
          "Batch already has this attribute",
          "BATCH_ATTRIBUTE_DUPLICATE",
          409,
          new Object[] {attribute.getAttributeCode()});
    }

    BatchAttribute entity =
        BatchAttribute.builder()
            .batch(batch)
            .attribute(attribute)
            .value(request.getValue())
            .build();

    BatchAttribute saved = attributeRepository.save(entity);
    log.info(
        "Added attribute {} to batch {}: {}",
        attribute.getAttributeCode(),
        batch.getBatchCode(),
        saved.getId());

    return BatchAttributeDto.from(saved);
  }

  @Transactional
  public void delete(UUID batchId, UUID attributeId) {
    UUID tenantId = TenantContext.getCurrentTenantId();

    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

    BatchAttribute entity =
        attributeRepository
            .findById(attributeId)
            .filter(a -> a.getBatch().getId().equals(batch.getId()))
            .filter(a -> tenantId.equals(a.getTenantId()))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Batch attribute not found: " + attributeId + " for batch " + batchId));

    entity.delete();
    attributeRepository.save(entity);
    log.info("Deleted batch attribute: batch={}, attr={}", batch.getBatchCode(), attributeId);
  }
}
