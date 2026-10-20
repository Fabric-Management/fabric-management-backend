package com.fabricmanagement.product.fabric.infra.repository;

import com.fabricmanagement.product.fabric.domain.article.FabricArticle;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FabricArticleRepository extends JpaRepository<FabricArticle, UUID> {

  Optional<FabricArticle> findByTenantIdAndIdAndDeletedAtIsNull(UUID tenantId, UUID id);

  @Query(
      "SELECT a FROM FabricArticle a WHERE a.tenantId = :tenantId AND a.product.id = :productId"
          + " AND a.deletedAt IS NULL")
  Optional<FabricArticle> findLiveByProduct(
      @Param("tenantId") UUID tenantId, @Param("productId") UUID productId);

  boolean existsByTenantIdAndArticleCodeIgnoreCaseAndDeletedAtIsNull(UUID tenantId, String code);

  boolean existsByTenantIdAndArticleCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(
      UUID tenantId, String code, UUID id);

  /** Live articles whose product name or article code contains the pattern (lower case). */
  @Query(
      value =
          "SELECT a FROM FabricArticle a JOIN a.product p WHERE a.tenantId = :tenantId"
              + " AND a.deletedAt IS NULL AND (LOWER(COALESCE(p.name, '')) LIKE :pattern"
              + " OR LOWER(COALESCE(a.articleCode, '')) LIKE :pattern)",
      countQuery =
          "SELECT COUNT(a) FROM FabricArticle a JOIN a.product p WHERE a.tenantId = :tenantId"
              + " AND a.deletedAt IS NULL AND (LOWER(COALESCE(p.name, '')) LIKE :pattern"
              + " OR LOWER(COALESCE(a.articleCode, '')) LIKE :pattern)")
  Page<FabricArticle> search(
      @Param("tenantId") UUID tenantId, @Param("pattern") String pattern, Pageable pageable);

  @Query(
      value =
          "SELECT a FROM FabricArticle a JOIN a.product p WHERE a.tenantId = :tenantId"
              + " AND a.deletedAt IS NULL AND a.status = :status"
              + " AND (LOWER(COALESCE(p.name, '')) LIKE :pattern"
              + " OR LOWER(COALESCE(a.articleCode, '')) LIKE :pattern)",
      countQuery =
          "SELECT COUNT(a) FROM FabricArticle a JOIN a.product p WHERE a.tenantId = :tenantId"
              + " AND a.deletedAt IS NULL AND a.status = :status"
              + " AND (LOWER(COALESCE(p.name, '')) LIKE :pattern"
              + " OR LOWER(COALESCE(a.articleCode, '')) LIKE :pattern)")
  Page<FabricArticle> searchByStatus(
      @Param("tenantId") UUID tenantId,
      @Param("status") FabricArticleStatus status,
      @Param("pattern") String pattern,
      Pageable pageable);
}
