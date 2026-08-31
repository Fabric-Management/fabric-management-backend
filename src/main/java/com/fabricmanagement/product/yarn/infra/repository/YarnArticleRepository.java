package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YarnArticleRepository extends JpaRepository<YarnArticle, UUID> {

  Optional<YarnArticle> findByTenantIdAndId(UUID tenantId, UUID id);

  boolean existsByTenantIdAndProduct_Id(UUID tenantId, UUID productId);
}
