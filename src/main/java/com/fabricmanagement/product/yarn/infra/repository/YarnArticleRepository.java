package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface YarnArticleRepository
    extends JpaRepository<YarnArticle, UUID>, JpaSpecificationExecutor<YarnArticle> {

  Optional<YarnArticle> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<YarnArticle> findByTenantIdAndUidIgnoreCase(UUID tenantId, String uid);

  List<YarnArticle> findByTenantIdAndNameIgnoreCaseOrderByCreatedAtAsc(UUID tenantId, String name);

  Optional<YarnArticle> findByTenantIdAndProduct_Id(UUID tenantId, UUID productId);

  List<YarnArticle> findByTenantIdAndProduct_IdIn(UUID tenantId, Collection<UUID> productIds);

  List<YarnArticle> findByTenantIdAndCanonicalKeyAndIdNotOrderByCreatedAtAsc(
      UUID tenantId, String canonicalKey, UUID articleId);

  boolean existsByTenantIdAndProduct_Id(UUID tenantId, UUID productId);
}
