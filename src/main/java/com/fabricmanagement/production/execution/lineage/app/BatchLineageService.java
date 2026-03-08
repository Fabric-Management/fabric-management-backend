package com.fabricmanagement.production.execution.lineage.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.common.exception.InsufficientStockException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.lineage.domain.BatchLineage;
import com.fabricmanagement.production.execution.lineage.domain.event.BatchLineageCreatedEvent;
import com.fabricmanagement.production.execution.lineage.domain.event.BatchLineageDeletedEvent;
import com.fabricmanagement.production.execution.lineage.dto.BatchLineageDetailDto;
import com.fabricmanagement.production.execution.lineage.dto.BatchLineageDto;
import com.fabricmanagement.production.execution.lineage.dto.CreateBatchLineageRequest;
import com.fabricmanagement.production.execution.lineage.dto.LineageNodeDto;
import com.fabricmanagement.production.execution.lineage.dto.TraceNodeDto;
import com.fabricmanagement.production.execution.lineage.infra.repository.BatchLineageRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchLineageService {

  private final BatchLineageRepository batchLineageRepository;
  private final BatchRepository batchRepository;
  private final ApplicationEventPublisher eventPublisher;

  private Batch loadBatchForUpdate(UUID batchId, UUID tenantId, String label) {
    return batchRepository
        .findByIdAndTenantIdForUpdate(batchId, tenantId)
        .orElseThrow(
            () -> new NotFoundException(String.format("%s batch not found: %s", label, batchId)));
  }

  @Transactional
  public BatchLineageDto create(CreateBatchLineageRequest request) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Creating batch lineage: tenantId={}, parent={}, child={}",
        tenantId,
        request.getParentBatchId(),
        request.getChildBatchId());

    if (request.getParentBatchId().equals(request.getChildBatchId())) {
      throw new BatchDomainException("A batch cannot be its own parent");
    }

    if (batchLineageRepository.existsByParentBatchIdAndChildBatchId(
        request.getParentBatchId(), request.getChildBatchId())) {
      throw new BatchDomainException(
          String.format(
              "Lineage already exists: parent=%s → child=%s",
              request.getParentBatchId(), request.getChildBatchId()));
    }

    Batch parentBatch = loadBatch(request.getParentBatchId(), tenantId, "Parent");
    loadBatchForUpdate(request.getChildBatchId(), tenantId, "Child");

    if (parentBatch.getStatus() == BatchStatus.DEPLETED
        && parentBatch.getAvailableQuantity().compareTo(BigDecimal.ZERO) <= 0) {
      throw new InsufficientStockException(
          parentBatch.getBatchCode(),
          request.getConsumedQuantity(),
          parentBatch.getAvailableQuantity(),
          parentBatch.getUnit());
    }

    if (request.getConsumptionPercentage() != null) {
      BigDecimal currentTotal =
          batchLineageRepository.sumConsumptionPercentageByChildBatchId(request.getChildBatchId());
      BigDecimal newTotal = currentTotal.add(request.getConsumptionPercentage());
      if (newTotal.compareTo(new BigDecimal("100")) > 0) {
        throw new BatchDomainException(
            String.format(
                "Total consumption percentage would exceed 100%%: current=%.2f%%, adding=%.2f%%",
                currentTotal, request.getConsumptionPercentage()));
      }
    }

    BatchLineage lineage =
        BatchLineage.create(
            tenantId,
            request.getParentBatchId(),
            request.getChildBatchId(),
            request.getConsumedQuantity(),
            request.getUnit(),
            request.getConsumptionPercentage(),
            request.getConsumedAt(),
            request.getProcessReference(),
            request.getRemarks());

    lineage = batchLineageRepository.save(lineage);
    log.info(
        "Created batch lineage: id={}, parent={} → child={}, qty={} {}",
        lineage.getId(),
        lineage.getParentBatchId(),
        lineage.getChildBatchId(),
        lineage.getConsumedQuantity(),
        lineage.getUnit());

    eventPublisher.publishEvent(
        new BatchLineageCreatedEvent(
            tenantId,
            lineage.getId(),
            lineage.getParentBatchId(),
            lineage.getChildBatchId(),
            lineage.getConsumedQuantity(),
            lineage.getUnit(),
            lineage.getConsumptionPercentage(),
            lineage.getConsumedAt()));

    return BatchLineageDto.from(lineage);
  }

  /** Forward trace: what input batches were consumed to produce this child batch? */
  @Transactional(readOnly = true)
  public List<BatchLineageDto> getParents(UUID childBatchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Getting parents for child batch: tenantId={}, childBatchId={}", tenantId, childBatchId);

    return batchLineageRepository
        .findByTenantIdAndChildBatchIdAndIsActiveTrue(tenantId, childBatchId)
        .stream()
        .map(BatchLineageDto::from)
        .toList();
  }

  /** Backward trace: where was this parent batch consumed? */
  @Transactional(readOnly = true)
  public List<BatchLineageDto> getChildren(UUID parentBatchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug(
        "Getting children for parent batch: tenantId={}, parentBatchId={}",
        tenantId,
        parentBatchId);

    return batchLineageRepository
        .findByTenantIdAndParentBatchIdAndIsActiveTrue(tenantId, parentBatchId)
        .stream()
        .map(BatchLineageDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public Page<BatchLineageDto> getAll(Pageable pageable) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting all batch lineage records: tenantId={}", tenantId);

    return batchLineageRepository
        .findByTenantIdAndIsActiveTrue(tenantId, pageable)
        .map(BatchLineageDto::from);
  }

  /**
   * Aggregated lineage view: the focal batch plus enriched parent &amp; child nodes. Allows the UI
   * to render the full one-step-back / one-step-forward traceability panel in one call.
   */
  @Transactional(readOnly = true)
  public BatchLineageDetailDto getLineageDetail(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Getting lineage detail: tenantId={}, batchId={}", tenantId, batchId);

    Batch focalBatch = loadBatch(batchId, tenantId, "Focal");

    List<BatchLineage> parentLineages =
        batchLineageRepository.findByTenantIdAndChildBatchIdAndIsActiveTrue(tenantId, batchId);
    List<BatchLineage> childLineages =
        batchLineageRepository.findByTenantIdAndParentBatchIdAndIsActiveTrue(tenantId, batchId);

    List<UUID> relatedBatchIds =
        java.util.stream.Stream.concat(
                parentLineages.stream().map(BatchLineage::getParentBatchId),
                childLineages.stream().map(BatchLineage::getChildBatchId))
            .distinct()
            .toList();

    Map<UUID, Batch> batchMap =
        batchRepository.findAllById(relatedBatchIds).stream()
            .collect(Collectors.toMap(Batch::getId, Function.identity()));

    List<LineageNodeDto> parents =
        parentLineages.stream()
            .filter(l -> batchMap.containsKey(l.getParentBatchId()))
            .map(l -> LineageNodeDto.from(l, batchMap.get(l.getParentBatchId())))
            .toList();

    List<LineageNodeDto> children =
        childLineages.stream()
            .filter(l -> batchMap.containsKey(l.getChildBatchId()))
            .map(l -> LineageNodeDto.from(l, batchMap.get(l.getChildBatchId())))
            .toList();

    return BatchLineageDetailDto.builder()
        .batch(BatchDto.from(focalBatch))
        .parents(parents)
        .children(children)
        .build();
  }

  @Transactional
  public void delete(UUID lineageId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Deleting batch lineage: tenantId={}, id={}", tenantId, lineageId);

    BatchLineage lineage =
        batchLineageRepository
            .findByIdAndTenantId(lineageId, tenantId)
            .orElseThrow(() -> new NotFoundException("Batch lineage not found: " + lineageId));

    lineage.delete();
    batchLineageRepository.save(lineage);
    log.info(
        "Deleted batch lineage: id={}, parent={} → child={}",
        lineage.getId(),
        lineage.getParentBatchId(),
        lineage.getChildBatchId());

    eventPublisher.publishEvent(
        new BatchLineageDeletedEvent(
            tenantId, lineage.getId(), lineage.getParentBatchId(), lineage.getChildBatchId()));
  }

  private static final int MAX_TRACE_DEPTH = 10;

  /**
   * Recursive backward trace: full ancestry tree for a batch.
   *
   * <p>Answers: "This fabric was made from which yarns? Those yarns from which fibers?" etc.
   */
  @Transactional(readOnly = true)
  public TraceNodeDto traceBackward(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Tracing backward: tenantId={}, batchId={}", tenantId, batchId);

    Batch root = loadBatch(batchId, tenantId, "Trace root");
    TraceNodeDto rootNode = TraceNodeDto.fromBatch(root, 0);

    List<BatchLineage> lineages =
        batchLineageRepository.findAncestorsWithDepthLimit(batchId, tenantId);
    if (lineages.isEmpty()) {
      return rootNode;
    }

    Set<UUID> batchIds =
        lineages.stream().map(BatchLineage::getParentBatchId).collect(Collectors.toSet());

    Map<UUID, Batch> batchMap =
        batchRepository.findAllById(batchIds).stream()
            .collect(Collectors.toMap(Batch::getId, Function.identity()));

    buildBackwardTree(rootNode, lineages, batchMap, 1, new HashSet<>());
    return rootNode;
  }

  /**
   * Recursive forward trace: full descendant tree for a batch.
   *
   * <p>Answers: "This cotton lot was used in which yarns? Those yarns in which fabrics?" etc.
   */
  @Transactional(readOnly = true)
  public TraceNodeDto traceForward(UUID batchId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Tracing forward: tenantId={}, batchId={}", tenantId, batchId);

    Batch root = loadBatch(batchId, tenantId, "Trace root");
    TraceNodeDto rootNode = TraceNodeDto.fromBatch(root, 0);

    List<BatchLineage> lineages =
        batchLineageRepository.findDescendantsWithDepthLimit(batchId, tenantId);
    if (lineages.isEmpty()) {
      return rootNode;
    }

    Set<UUID> batchIds =
        lineages.stream().map(BatchLineage::getChildBatchId).collect(Collectors.toSet());

    Map<UUID, Batch> batchMap =
        batchRepository.findAllById(batchIds).stream()
            .collect(Collectors.toMap(Batch::getId, Function.identity()));

    buildForwardTree(rootNode, lineages, batchMap, 1, new HashSet<>());
    return rootNode;
  }

  private void buildBackwardTree(
      TraceNodeDto node,
      List<BatchLineage> allLineages,
      Map<UUID, Batch> batchMap,
      int depth,
      Set<UUID> visited) {
    if (depth > MAX_TRACE_DEPTH) return;
    visited.add(node.getBatchId());

    List<BatchLineage> parentLinks =
        allLineages.stream().filter(l -> l.getChildBatchId().equals(node.getBatchId())).toList();

    for (BatchLineage link : parentLinks) {
      if (visited.contains(link.getParentBatchId())) continue;
      Batch parentBatch = batchMap.get(link.getParentBatchId());
      if (parentBatch == null) continue;

      TraceNodeDto parentNode = TraceNodeDto.fromBatch(parentBatch, depth);
      parentNode.setConsumedQuantity(link.getConsumedQuantity());
      parentNode.setConsumptionPercentage(link.getConsumptionPercentage());
      parentNode.setProcessReference(link.getProcessReference());

      buildBackwardTree(parentNode, allLineages, batchMap, depth + 1, visited);
      node.getChildren().add(parentNode);
    }
  }

  private void buildForwardTree(
      TraceNodeDto node,
      List<BatchLineage> allLineages,
      Map<UUID, Batch> batchMap,
      int depth,
      Set<UUID> visited) {
    if (depth > MAX_TRACE_DEPTH) return;
    visited.add(node.getBatchId());

    List<BatchLineage> childLinks =
        allLineages.stream().filter(l -> l.getParentBatchId().equals(node.getBatchId())).toList();

    for (BatchLineage link : childLinks) {
      if (visited.contains(link.getChildBatchId())) continue;
      Batch childBatch = batchMap.get(link.getChildBatchId());
      if (childBatch == null) continue;

      TraceNodeDto childNode = TraceNodeDto.fromBatch(childBatch, depth);
      childNode.setConsumedQuantity(link.getConsumedQuantity());
      childNode.setConsumptionPercentage(link.getConsumptionPercentage());
      childNode.setProcessReference(link.getProcessReference());

      buildForwardTree(childNode, allLineages, batchMap, depth + 1, visited);
      node.getChildren().add(childNode);
    }
  }

  private Batch loadBatch(UUID batchId, UUID tenantId, String label) {
    return batchRepository
        .findByIdAndTenantId(batchId, tenantId)
        .orElseThrow(
            () -> new NotFoundException(String.format("%s batch not found: %s", label, batchId)));
  }
}
