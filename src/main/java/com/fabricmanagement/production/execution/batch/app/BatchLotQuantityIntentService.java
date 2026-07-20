package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.api.BatchLotQuantityIntentPort;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntent;
import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntentStatus;
import com.fabricmanagement.production.execution.batch.domain.event.BatchLotQuantityIntentPlacedEvent;
import com.fabricmanagement.production.execution.batch.domain.event.BatchLotQuantityIntentReleasedEvent;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchUnitMeasureMismatchException;
import com.fabricmanagement.production.execution.batch.domain.exception.LotIntentQuantityExceededException;
import com.fabricmanagement.production.execution.batch.domain.exception.LotIntentUnitMismatchException;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BatchLotQuantityIntentService implements BatchLotQuantityIntentPort {

  private final BatchLotQuantityIntentRepository intentRepository;
  private final BatchRepository batchRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final BatchPrimaryMeasureService primaryMeasureService;
  private final BatchCommitmentQuantityService commitmentQuantityService;

  @Override
  @Transactional(readOnly = true)
  public LotIntentCoverage checkCoverage(UUID quoteLineId, Collection<LotIntentRequest> intents) {
    UUID tenantId = TenantContext.requireTenantId();
    List<LotIntentRequest> requested = normalize(intents);
    if (requested.isEmpty()) {
      return new LotIntentCoverage(true);
    }
    Map<UUID, Batch> batches = loadBatches(tenantId, requested);
    requested = canonicalize(batches, requested);
    validateAgainstPhysicalQuantity(batches, requested);
    return new LotIntentCoverage(isCovered(tenantId, quoteLineId, batches, requested));
  }

  @Override
  @Transactional
  public LotIntentCoverage replaceIntents(
      UUID quoteId,
      String quoteNumber,
      UUID quoteLineId,
      UUID marketerId,
      String marketerName,
      LocalDate expiresAt,
      Collection<LotIntentRequest> intents) {
    UUID tenantId = TenantContext.requireTenantId();
    List<LotIntentRequest> requested = normalize(intents);
    Map<UUID, Batch> batches = loadBatches(tenantId, requested);
    requested = canonicalize(batches, requested);
    validateAgainstPhysicalQuantity(batches, requested);
    boolean covered = isCovered(tenantId, quoteLineId, batches, requested);

    Map<UUID, LotIntentRequest> requestedByBatch =
        requested.stream()
            .collect(
                Collectors.toMap(
                    LotIntentRequest::batchId, Function.identity(), (left, right) -> right));
    List<BatchLotQuantityIntent> existing =
        intentRepository.findByTenantIdAndQuoteLineId(tenantId, quoteLineId);

    existing.forEach(
        intent -> {
          LotIntentRequest replacement = requestedByBatch.remove(intent.getBatchId());
          if (replacement == null) {
            release(tenantId, intent, Instant.now());
            return;
          }
          if (intent.getStatus() == BatchLotQuantityIntentStatus.RELEASED) {
            intent.reactivate(
                quoteId,
                quoteNumber,
                marketerId,
                marketerName,
                replacement.quantity(),
                replacement.unit(),
                expiresAt);
            BatchLotQuantityIntent saved = intentRepository.save(intent);
            eventPublisher.publishEvent(
                new BatchLotQuantityIntentPlacedEvent(
                    tenantId, quoteId, quoteLineId, saved.getBatchId(), saved.getId()));
            return;
          }
          intent.update(
              quoteId,
              quoteNumber,
              marketerId,
              marketerName,
              replacement.quantity(),
              replacement.unit(),
              expiresAt);
          intentRepository.save(intent);
        });

    requestedByBatch
        .values()
        .forEach(
            request -> {
              BatchLotQuantityIntent saved =
                  intentRepository.save(
                      BatchLotQuantityIntent.place(
                          tenantId,
                          quoteId,
                          quoteNumber,
                          quoteLineId,
                          marketerId,
                          marketerName,
                          request.batchId(),
                          request.quantity(),
                          request.unit(),
                          expiresAt));
              eventPublisher.publishEvent(
                  new BatchLotQuantityIntentPlacedEvent(
                      tenantId, quoteId, quoteLineId, saved.getBatchId(), saved.getId()));
            });

    return new LotIntentCoverage(covered);
  }

  @Override
  @Transactional
  public void releaseIntents(UUID quoteLineId) {
    UUID tenantId = TenantContext.requireTenantId();
    intentRepository.findByTenantIdAndQuoteLineId(tenantId, quoteLineId).stream()
        .filter(intent -> intent.getStatus() == BatchLotQuantityIntentStatus.ACTIVE)
        .forEach(intent -> release(tenantId, intent, Instant.now()));
  }

  @Override
  @Transactional
  public void resyncExpiry(UUID quoteId, LocalDate expiresAt) {
    UUID tenantId = TenantContext.requireTenantId();
    intentRepository
        .findByTenantIdAndQuoteIdAndStatusAndIsActiveTrue(
            tenantId, quoteId, BatchLotQuantityIntentStatus.ACTIVE)
        .stream()
        .filter(intent -> intent.resyncExpiry(expiresAt))
        .forEach(intentRepository::save);
  }

  @Transactional
  public int releaseExpiredIntents(LocalDate expiredBefore) {
    UUID tenantId = TenantContext.requireTenantId();
    List<BatchLotQuantityIntent> expired =
        intentRepository.findByTenantIdAndStatusAndExpiresAtBeforeAndIsActiveTrue(
            tenantId, BatchLotQuantityIntentStatus.ACTIVE, expiredBefore);
    expired.forEach(intent -> release(intent.getTenantId(), intent, Instant.now()));
    return expired.size();
  }

  private List<LotIntentRequest> normalize(Collection<LotIntentRequest> intents) {
    if (intents == null || intents.isEmpty()) {
      return List.of();
    }
    Map<UUID, LotIntentRequest> byBatch = new LinkedHashMap<>();
    intents.stream()
        .filter(Objects::nonNull)
        .filter(intent -> intent.batchId() != null)
        .forEach(
            intent -> {
              if (intent.quantity() == null || intent.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Lot intent quantity must be positive");
              }
              if (intent.unit() == null || intent.unit().isBlank()) {
                throw new IllegalArgumentException("Lot intent unit is required");
              }
              byBatch.put(
                  intent.batchId(),
                  new LotIntentRequest(intent.batchId(), intent.quantity(), intent.unit().trim()));
            });
    return List.copyOf(byBatch.values());
  }

  private Map<UUID, Batch> loadBatches(UUID tenantId, List<LotIntentRequest> requested) {
    if (requested.isEmpty()) {
      return Map.of();
    }
    List<UUID> batchIds = requested.stream().map(LotIntentRequest::batchId).distinct().toList();
    Map<UUID, Batch> batches =
        batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, batchIds).stream()
            .collect(Collectors.toMap(Batch::getId, Function.identity()));
    batchIds.stream()
        .filter(batchId -> !batches.containsKey(batchId))
        .findFirst()
        .ifPresent(
            batchId -> {
              throw new NotFoundException("Lot not found: " + batchId);
            });
    return batches;
  }

  private List<LotIntentRequest> canonicalize(
      Map<UUID, Batch> batches, List<LotIntentRequest> requested) {
    return requested.stream()
        .map(
            request -> {
              Batch batch = batches.get(request.batchId());
              var resolution = primaryMeasureService.resolve(batch);
              BigDecimal canonicalQuantity =
                  primaryMeasureService
                      .toCanonical(request.quantity(), request.unit(), resolution.primaryMeasure())
                      .orElseThrow(
                          () ->
                              new LotIntentUnitMismatchException(
                                  batch.getId(),
                                  primaryMeasureService.normalizeUnit(request.unit()),
                                  resolution.primaryUnit()));
              return new LotIntentRequest(
                  request.batchId(), canonicalQuantity, resolution.primaryUnit());
            })
        .toList();
  }

  private void validateAgainstPhysicalQuantity(
      Map<UUID, Batch> batches, List<LotIntentRequest> requested) {
    requested.forEach(
        request -> {
          Batch batch = batches.get(request.batchId());
          BigDecimal physicalQuantity = physicalQuantity(batch);
          if (request.quantity().compareTo(physicalQuantity) > 0) {
            throw new LotIntentQuantityExceededException(
                batch.getId(), request.quantity(), physicalQuantity, request.unit());
          }
        });
  }

  private boolean isCovered(
      UUID tenantId, UUID quoteLineId, Map<UUID, Batch> batches, List<LotIntentRequest> requested) {
    List<UUID> batchIds = requested.stream().map(LotIntentRequest::batchId).distinct().toList();
    Map<UUID, BatchCommitmentQuantityService.Summary> commitments =
        commitmentQuantityService.summarize(tenantId, batches.values(), quoteLineId);
    return requested.stream()
        .allMatch(
            request -> {
              Batch batch = batches.get(request.batchId());
              BigDecimal free =
                  physicalQuantity(batch)
                      .subtract(commitments.get(request.batchId()).softIntent())
                      .subtract(commitments.get(request.batchId()).hardReserved());
              if (free.compareTo(BigDecimal.ZERO) < 0) {
                free = BigDecimal.ZERO;
              }
              return free.compareTo(request.quantity()) >= 0;
            });
  }

  private BigDecimal physicalQuantity(Batch batch) {
    var resolution = primaryMeasureService.resolve(batch);
    return primaryMeasureService
        .toCanonical(
            batch.getQuantity().subtract(batch.getConsumedQuantity()),
            batch.getUnit(),
            resolution.primaryMeasure())
        .orElseThrow(
            () ->
                new BatchUnitMeasureMismatchException(
                    batch.getId(), batch.getUnit(), resolution.primaryUnit()));
  }

  private void release(UUID tenantId, BatchLotQuantityIntent intent, Instant releasedAt) {
    if (intent.release(releasedAt)) {
      BatchLotQuantityIntent saved = intentRepository.save(intent);
      eventPublisher.publishEvent(
          new BatchLotQuantityIntentReleasedEvent(
              tenantId,
              saved.getQuoteId(),
              saved.getQuoteLineId(),
              saved.getBatchId(),
              saved.getId()));
    }
  }
}
