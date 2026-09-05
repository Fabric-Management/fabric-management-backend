package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface YarnArticleRepository
    extends JpaRepository<YarnArticle, UUID>, JpaSpecificationExecutor<YarnArticle> {

  Optional<YarnArticle> findByTenantIdAndId(UUID tenantId, UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM YarnArticle a WHERE a.tenantId = :tenantId AND a.id = :id")
  Optional<YarnArticle> findByTenantIdAndIdForUpdate(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  @Query(
      """
      SELECT a.id AS id, a.sourceDesignation AS sourceDesignation
      FROM YarnArticle a
      WHERE a.tenantId = :tenantId
        AND a.status IN (com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus.DRAFT,
                         com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus.ACTIVE)
        AND a.sourceDesignation IS NOT NULL
      ORDER BY a.id
      """)
  List<SourceDesignationCandidate> findWritableSourceDesignationCandidates(
      @Param("tenantId") UUID tenantId);

  Optional<YarnArticle> findByTenantIdAndUidIgnoreCase(UUID tenantId, String uid);

  List<YarnArticle> findByTenantIdAndNameIgnoreCaseOrderByCreatedAtAsc(UUID tenantId, String name);

  Optional<YarnArticle> findByTenantIdAndProduct_Id(UUID tenantId, UUID productId);

  List<YarnArticle> findByTenantIdAndProduct_IdIn(UUID tenantId, Collection<UUID> productIds);

  List<YarnArticle> findByTenantIdAndCanonicalKeyAndIdNotOrderByCreatedAtAsc(
      UUID tenantId, String canonicalKey, UUID articleId);

  boolean existsByTenantIdAndProduct_Id(UUID tenantId, UUID productId);

  long countByTenantIdAndStatus(
      UUID tenantId, com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus status);

  interface SourceDesignationCandidate {
    UUID getId();

    String getSourceDesignation();
  }
}
