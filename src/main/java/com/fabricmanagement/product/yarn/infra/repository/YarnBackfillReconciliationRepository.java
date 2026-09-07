package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillReconciliation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YarnBackfillReconciliationRepository
    extends JpaRepository<YarnBackfillReconciliation, UUID> {

  List<YarnBackfillReconciliation> findByTenantIdAndStatus(
      UUID tenantId, YarnBackfillQueueStatus status);

  long countByTenantIdAndStatus(UUID tenantId, YarnBackfillQueueStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT r FROM YarnBackfillReconciliation r " + "WHERE r.tenantId = :tenantId AND r.id = :id")
  Optional<YarnBackfillReconciliation> findByTenantIdAndIdForUpdate(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  @Query(
      value =
          """
          SELECT r.id AS "id",
                 r.product_id AS "productId",
                 p.uid AS "productUid",
                 r.article_id AS "articleId",
                 a.name AS "articleName",
                 a.status AS "articleStatus",
                 r.reason AS "reason",
                 r.status AS "status",
                 r.created_at AS "createdAt",
                 r.resolution_action AS "resolutionAction",
                 r.resolved_candidate::text AS "resolvedCandidateJson",
                 r.candidate_occurrence_count AS "candidateOccurrenceCount"
          FROM production.prod_yarn_backfill_reconciliation r
          JOIN production.prod_product p
            ON p.id = r.product_id AND p.tenant_id = r.tenant_id
          JOIN production.prod_yarn_article a
            ON a.id = r.article_id AND a.tenant_id = r.tenant_id
          WHERE r.tenant_id = :tenantId
            AND r.status = :status
            AND r.is_active = TRUE
          ORDER BY r.created_at ASC, r.id ASC
          """,
      countQuery =
          """
          SELECT count(*)
          FROM production.prod_yarn_backfill_reconciliation r
          JOIN production.prod_product p
            ON p.id = r.product_id AND p.tenant_id = r.tenant_id
          JOIN production.prod_yarn_article a
            ON a.id = r.article_id AND a.tenant_id = r.tenant_id
          WHERE r.tenant_id = :tenantId
            AND r.status = :status
            AND r.is_active = TRUE
          """,
      nativeQuery = true)
  Page<YarnReconciliationListRow> findListPage(
      @Param("tenantId") UUID tenantId, @Param("status") String status, Pageable pageable);

  @Query(
      value =
          """
          SELECT min(candidate.ordinality)::bigint AS "firstOrdinal",
                 count(*)::bigint AS "occurrences"
          FROM production.prod_yarn_backfill_reconciliation reconciliation
          CROSS JOIN LATERAL jsonb_array_elements(
              reconciliation.candidates -> 'candidates'
          ) WITH ORDINALITY AS candidate(elem, ordinality)
          WHERE reconciliation.tenant_id = :tenantId
            AND reconciliation.id = :id
            AND reconciliation.is_active = TRUE
          GROUP BY convert_to(candidate.elem ->> 'rawValue', 'UTF8')
          ORDER BY min(candidate.ordinality)
          LIMIT :limit OFFSET :offset
          """,
      nativeQuery = true)
  List<YarnCandidateGroupRow> findCandidateGroupPage(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(
      value =
          """
          SELECT candidate.ordinality::bigint AS "ordinality",
                 candidate.elem ->> 'rawValue' AS "rawValue",
                 candidate.elem ->> 'sourceKind' AS "sourceKind",
                 candidate.elem ->> 'recordedAt' AS "recordedAt",
                 candidate.elem ->> 'sourceRecordId' AS "sourceRecordId"
          FROM production.prod_yarn_backfill_reconciliation reconciliation
          CROSS JOIN LATERAL jsonb_array_elements(
              reconciliation.candidates -> 'candidates'
          ) WITH ORDINALITY AS candidate(elem, ordinality)
          WHERE reconciliation.tenant_id = :tenantId
            AND reconciliation.id = :id
            AND reconciliation.is_active = TRUE
            AND candidate.ordinality IN (:ordinals)
          ORDER BY candidate.ordinality
          """,
      nativeQuery = true)
  List<YarnCandidateIdentityRow> findCandidateIdentities(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("ordinals") Collection<Long> ordinals);

  @Query(
      value =
          """
          SELECT CASE
              WHEN EXISTS (
                  SELECT 1
                  FROM production.prod_yarn_backfill_reconciliation existing
                  WHERE existing.tenant_id = :tenantId
                    AND existing.id = :id
                    AND existing.is_active = TRUE
              )
              THEN (
                  SELECT count(*)
                  FROM (
                      SELECT 1
                      FROM production.prod_yarn_backfill_reconciliation reconciliation
                      CROSS JOIN LATERAL jsonb_array_elements(
                          reconciliation.candidates -> 'candidates'
                      ) WITH ORDINALITY AS candidate(elem, ordinality)
                      WHERE reconciliation.tenant_id = :tenantId
                        AND reconciliation.id = :id
                        AND reconciliation.is_active = TRUE
                      GROUP BY convert_to(candidate.elem ->> 'rawValue', 'UTF8')
                  ) grouped_candidates
              )
              ELSE NULL
          END
          """,
      nativeQuery = true)
  Long countCandidateGroups(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

  interface YarnReconciliationListRow {
    UUID getId();

    UUID getProductId();

    String getProductUid();

    UUID getArticleId();

    String getArticleName();

    String getArticleStatus();

    String getReason();

    String getStatus();

    Instant getCreatedAt();

    String getResolutionAction();

    String getResolvedCandidateJson();

    Integer getCandidateOccurrenceCount();
  }

  interface YarnCandidateGroupRow {
    Long getFirstOrdinal();

    Long getOccurrences();
  }

  interface YarnCandidateIdentityRow {
    Long getOrdinality();

    String getRawValue();

    String getSourceKind();

    String getRecordedAt();

    String getSourceRecordId();
  }
}
