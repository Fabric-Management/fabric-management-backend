package com.fabricmanagement.product.yarn.infra.repository;

import com.fabricmanagement.product.yarn.domain.article.YarnArticleAudit;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleAuditEventType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface YarnArticleAuditRepository extends JpaRepository<YarnArticleAudit, UUID> {

  List<YarnArticleAudit> findByTenantIdAndArticle_IdOrderByCreatedAtAsc(
      UUID tenantId, UUID articleId);

  Optional<YarnArticleAudit> findByTenantIdAndArticle_IdAndEventTypeInAndSpecVersionTo(
      UUID tenantId, UUID articleId, List<YarnArticleAuditEventType> eventTypes, int specVersionTo);
}
