package com.fabricmanagement.product.yarn.app.backfill;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.domain.SourceDesignationPolicy;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueReason;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillReconciliation;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillResolutionAction;
import com.fabricmanagement.product.yarn.domain.exception.YarnReconciliationException;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationCandidateGroupDto;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationCandidatePageDto;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationChooseRequest;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationItemDto;
import com.fabricmanagement.product.yarn.dto.YarnReconciliationResolvedCandidateDto;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository.YarnCandidateGroupRow;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository.YarnCandidateIdentityRow;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository.YarnReconciliationListRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class YarnReconciliationService {

  private final YarnBackfillReconciliationRepository reconciliationRepository;
  private final YarnArticleService articleService;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public Page<YarnReconciliationItemDto> list(YarnBackfillQueueStatus status, Pageable pageable) {
    return reconciliationRepository
        .findListPage(TenantContext.requireTenantId(), status.name(), pageable)
        .map(this::toItem);
  }

  @Transactional(readOnly = true)
  public YarnReconciliationCandidatePageDto candidates(UUID id, int page, int size) {
    UUID tenantId = TenantContext.requireTenantId();
    List<YarnCandidateGroupRow> groupRows =
        reconciliationRepository.findCandidateGroupPage(
            tenantId, id, size, Math.multiplyExact((long) page, size));
    Long totalGroups = reconciliationRepository.countCandidateGroups(tenantId, id);
    if (totalGroups == null) {
      throw new NotFoundException("Yarn reconciliation not found: " + id);
    }
    if (groupRows.isEmpty()) {
      return new YarnReconciliationCandidatePageDto(List.of(), totalGroups, page, size);
    }

    List<Long> ordinals = groupRows.stream().map(YarnCandidateGroupRow::getFirstOrdinal).toList();
    Map<Long, YarnCandidateIdentityRow> identities = new LinkedHashMap<>();
    reconciliationRepository
        .findCandidateIdentities(tenantId, id, ordinals)
        .forEach(row -> identities.put(row.getOrdinality(), row));
    List<YarnReconciliationCandidateGroupDto> groups =
        groupRows.stream()
            .map(
                group -> {
                  YarnCandidateIdentityRow identity = identities.get(group.getFirstOrdinal());
                  if (identity == null) {
                    throw new IllegalStateException(
                        "Candidate identity missing for ordinal " + group.getFirstOrdinal());
                  }
                  return new YarnReconciliationCandidateGroupDto(
                      identity.getRawValue(),
                      group.getOccurrences(),
                      LegacyDesignationSourceKind.valueOf(identity.getSourceKind()),
                      Instant.parse(identity.getRecordedAt()),
                      identity.getSourceRecordId(),
                      SourceDesignationPolicy.isOverlength(identity.getRawValue()));
                })
            .toList();
    return new YarnReconciliationCandidatePageDto(groups, totalGroups, page, size);
  }

  @Transactional
  public void choose(UUID id, YarnReconciliationChooseRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnBackfillReconciliation reconciliation = requireOpenForUpdate(tenantId, id);
    JsonNode candidate =
        StreamSupport.stream(reconciliation.getCandidates().path("candidates").spliterator(), false)
            .filter(
                stored ->
                    request.sourceKind().name().equals(stored.path("sourceKind").asText())
                        && request.sourceRecordId().equals(stored.path("sourceRecordId").asText()))
            .findFirst()
            .orElseThrow(
                () ->
                    new YarnReconciliationException(
                        "Stored yarn reconciliation candidate was not found",
                        "YARN_RECONCILIATION_CANDIDATE_NOT_FOUND"));
    String rawValue = candidate.path("rawValue").asText(null);
    if (SourceDesignationPolicy.isOverlength(rawValue)) {
      throw new YarnReconciliationException(
          "Stored yarn reconciliation candidate is overlength",
          "YARN_RECONCILIATION_CANDIDATE_OVERLENGTH");
    }
    articleService.adoptSourceDesignation(reconciliation.getArticleId(), rawValue);
    reconciliation.resolveChosen(candidate);
    reconciliationRepository.flush();
  }

  @Transactional
  public void dismiss(UUID id) {
    YarnBackfillReconciliation reconciliation =
        requireOpenForUpdate(TenantContext.requireTenantId(), id);
    reconciliation.resolveDismissed();
    reconciliationRepository.flush();
  }

  private YarnBackfillReconciliation requireOpenForUpdate(UUID tenantId, UUID id) {
    YarnBackfillReconciliation reconciliation =
        reconciliationRepository
            .findByTenantIdAndIdForUpdate(tenantId, id)
            .orElseThrow(() -> new NotFoundException("Yarn reconciliation not found: " + id));
    if (reconciliation.getStatus() != YarnBackfillQueueStatus.OPEN) {
      throw new YarnReconciliationException(
          "Yarn reconciliation row is not open", "YARN_RECONCILIATION_NOT_OPEN");
    }
    return reconciliation;
  }

  private YarnReconciliationItemDto toItem(YarnReconciliationListRow row) {
    return new YarnReconciliationItemDto(
        row.getId(),
        row.getProductId(),
        row.getProductUid(),
        row.getArticleId(),
        row.getArticleName(),
        YarnArticleStatus.valueOf(row.getArticleStatus()),
        YarnBackfillQueueReason.valueOf(row.getReason()),
        YarnBackfillQueueStatus.valueOf(row.getStatus()),
        row.getCreatedAt(),
        row.getResolutionAction() == null
            ? null
            : YarnBackfillResolutionAction.valueOf(row.getResolutionAction()),
        resolvedCandidate(row.getResolvedCandidateJson()),
        row.getCandidateOccurrenceCount());
  }

  private YarnReconciliationResolvedCandidateDto resolvedCandidate(String json) {
    if (json == null) {
      return null;
    }
    try {
      JsonNode candidate = objectMapper.readTree(json);
      return new YarnReconciliationResolvedCandidateDto(
          candidate.path("rawValue").asText(),
          LegacyDesignationSourceKind.valueOf(candidate.path("sourceKind").asText()),
          Instant.parse(candidate.path("recordedAt").asText()),
          candidate.path("sourceRecordId").asText());
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Stored resolved candidate is not valid JSON", exception);
    }
  }
}
