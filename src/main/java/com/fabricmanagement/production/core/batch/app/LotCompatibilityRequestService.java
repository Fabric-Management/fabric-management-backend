package com.fabricmanagement.production.core.batch.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.core.batch.api.LotCompatibilityRequestPort;
import com.fabricmanagement.production.core.batch.domain.LotCompatibilityConfirmation;
import com.fabricmanagement.production.core.batch.domain.LotCompatibilityRequest;
import com.fabricmanagement.production.core.batch.domain.LotCompatibilityRequestStatus;
import com.fabricmanagement.production.core.batch.dto.LotEvidenceDtos;
import com.fabricmanagement.production.core.batch.infra.repository.LotCompatibilityRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Open lot-compatibility questions and their answers (SOI A04, IK-14). */
@Service
@RequiredArgsConstructor
public class LotCompatibilityRequestService implements LotCompatibilityRequestPort {

  private final LotCompatibilityRequestRepository repository;
  private final Clock clock;

  @Override
  @Transactional
  public RequestView request(
      UUID tenantId,
      Collection<UUID> batchIds,
      UUID productId,
      UUID customerId,
      String sourceType,
      UUID sourceId,
      String note,
      UUID requestedBy) {
    String key = LotCompatibilityRequest.key(batchIds);
    return repository
        .findFirstByTenantIdAndSourceIdAndBatchKeyAndStatus(
            tenantId, sourceId, key, LotCompatibilityRequestStatus.OPEN)
        .map(LotCompatibilityRequestService::view)
        .orElseGet(
            () -> {
              LotCompatibilityRequest opened =
                  LotCompatibilityRequest.open(
                      batchIds,
                      productId,
                      customerId,
                      sourceType,
                      sourceId,
                      note,
                      requestedBy,
                      Instant.now(clock));
              opened.setTenantId(tenantId);
              return view(repository.save(opened));
            });
  }

  @Override
  @Transactional(readOnly = true)
  public List<RequestView> forSource(UUID tenantId, UUID sourceId) {
    return repository
        .findByTenantIdAndSourceIdOrderByRequestedAtDescIdDesc(tenantId, sourceId)
        .stream()
        .map(LotCompatibilityRequestService::view)
        .toList();
  }

  @Override
  @Transactional
  public void withdrawOpen(UUID tenantId, UUID sourceId, UUID actor) {
    repository.findByTenantIdAndSourceIdOrderByRequestedAtDescIdDesc(tenantId, sourceId).stream()
        .filter(request -> request.getStatus() == LotCompatibilityRequestStatus.OPEN)
        .forEach(
            request -> {
              request.withdraw(actor, Instant.now(clock));
              repository.save(request);
            });
  }

  @Transactional(readOnly = true)
  public List<LotEvidenceDtos.CompatibilityRequestDto> open() {
    return repository
        .findByTenantIdAndStatusOrderByRequestedAtAscIdAsc(
            TenantContext.requireTenantId(), LotCompatibilityRequestStatus.OPEN)
        .stream()
        .map(LotCompatibilityRequestService::toDto)
        .toList();
  }

  @Transactional
  public LotEvidenceDtos.CompatibilityRequestDto decline(UUID requestId, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    LotCompatibilityRequest request =
        repository
            .findByTenantIdAndId(tenantId, requestId)
            .orElseThrow(
                () -> new NotFoundException("Compatibility request not found: " + requestId));
    UUID actor = TenantContext.getCurrentUserId();
    if (actor == null) {
      throw new IllegalStateException("An authenticated actor is required");
    }
    request.decline(actor, Instant.now(clock), reason);
    return toDto(repository.save(request));
  }

  /** Closes the open requests a new confirmation answers. */
  @Transactional
  public void answeredBy(UUID tenantId, LotCompatibilityConfirmation confirmation) {
    repository
        .findByTenantIdAndStatusOrderByRequestedAtAscIdAsc(
            tenantId, LotCompatibilityRequestStatus.OPEN)
        .stream()
        .filter(request -> request.isAnsweredBy(confirmation))
        .forEach(
            request -> {
              request.confirmedBy(confirmation);
              repository.save(request);
            });
  }

  private static RequestView view(LotCompatibilityRequest request) {
    return new RequestView(
        request.getId(),
        request.getBatchIds(),
        request.getStatus().name(),
        request.getRequestedAt(),
        request.getResolvedAt(),
        request.getResolutionNote());
  }

  static LotEvidenceDtos.CompatibilityRequestDto toDto(LotCompatibilityRequest request) {
    return new LotEvidenceDtos.CompatibilityRequestDto(
        request.getId(),
        request.getBatchIds(),
        request.getProductId(),
        request.getCustomerId(),
        request.getSourceType(),
        request.getSourceId(),
        request.getNote(),
        request.getRequestedBy(),
        request.getRequestedAt(),
        request.getStatus(),
        request.getResolvedAt(),
        request.getResolutionNote());
  }
}
