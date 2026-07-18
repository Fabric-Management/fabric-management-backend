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
import com.fabricmanagement.production.masterdata.product.domain.reference.ProductAttribute;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductAttributeRepository;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchAttributeService {

  static final String LEGACY_COLOR_ATTRIBUTE_WRITE = "LEGACY_COLOR_ATTRIBUTE_WRITE";
  static final Set<String> LEGACY_COLOR_ATTRIBUTE_CODES =
      Set.of("COLOR", "COLOUR", "COLOR_ID", "COLOUR_ID", "SHADE");

  private final BatchAttributeRepository attributeRepository;
  private final BatchRepository batchRepository;
  private final ProductAttributeRepository productAttributeRepository;

  @Transactional(readOnly = true)
  public List<BatchAttributeDto> findByBatchId(UUID batchId) {
    UUID tenantId = TenantContext.requireTenantId();
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
    UUID tenantId = TenantContext.requireTenantId();

    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

    ProductAttribute attribute =
        productAttributeRepository
            .findById(request.attributeId())
            .filter(a -> tenantId.equals(a.getTenantId()))
            .orElseThrow(
                () -> new NotFoundException("Attribute not found: " + request.attributeId()));

    rejectLegacyColorMutation(tenantId, batch, attribute);

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
        BatchAttribute.builder().batch(batch).attribute(attribute).value(request.value()).build();

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
    UUID tenantId = TenantContext.requireTenantId();

    Batch batch =
        batchRepository
            .findByIdAndTenantId(batchId, tenantId)
            .orElseThrow(() -> new NotFoundException("Batch not found: " + batchId));

    BatchAttribute entity =
        attributeRepository
            .findByIdAndBatch_IdAndTenantId(attributeId, batch.getId(), tenantId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Batch attribute not found: " + attributeId + " for batch " + batchId));

    rejectLegacyColorMutation(tenantId, batch, entity.getAttribute());

    entity.delete();
    attributeRepository.save(entity);
    log.info("Deleted batch attribute: batch={}, attr={}", batch.getBatchCode(), attributeId);
  }

  /**
   * Guards every generic attribute mutation against the legacy color family. Any future generic
   * update path must invoke this method before mutating or persisting the attribute.
   */
  private void rejectLegacyColorMutation(UUID tenantId, Batch batch, ProductAttribute attribute) {
    String rawCode = attribute.getAttributeCode();
    String normalizedCode = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    if (!LEGACY_COLOR_ATTRIBUTE_CODES.contains(normalizedCode)) {
      return;
    }

    log.warn(
        "Rejected legacy color attribute mutation: tenantId={}, batchId={}, batchCode={}, attributeCode={}",
        tenantId,
        batch.getId(),
        batch.getBatchCode(),
        rawCode);
    throw new BatchDomainException(
        "Legacy color attributes are read-only after the batch color cutover",
        LEGACY_COLOR_ATTRIBUTE_WRITE,
        409,
        new Object[] {rawCode});
  }
}
