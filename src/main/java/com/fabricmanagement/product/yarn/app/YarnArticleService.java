package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.common.infrastructure.persistence.LikePattern;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberStatus;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleAudit;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleAuditEventType;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleSpec;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleSpecSerializer;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
import com.fabricmanagement.product.yarn.dto.YarnArticleDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleHistoryDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleHistorySnapshotDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleListItemDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleMutationResponse;
import com.fabricmanagement.product.yarn.dto.YarnDuplicateCandidateDto;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleAuditRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnSpinningSystemRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnTestMethodRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class YarnArticleService {

  private final YarnArticleRepository articleRepository;
  private final YarnArticleAuditRepository auditRepository;
  private final ProductRepository productRepository;
  private final FiberRepository fiberRepository;
  private final YarnSpinningSystemRepository spinningSystemRepository;
  private final YarnTestMethodRepository testMethodRepository;
  private final YarnArticleSpecSerializer serializer;

  @Transactional
  public YarnArticle createDraft(
      UUID productId, String name, String description, YarnArticleSpecCommand command) {
    UUID tenantId = TenantContext.requireTenantId();
    Product product =
        productRepository
            .findByTenantIdAndId(tenantId, productId)
            .filter(candidate -> candidate.getProductType() == ProductType.YARN)
            .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
            .orElseThrow(
                () ->
                    new YarnDomainException(
                        "I18", "I18: active current-tenant YARN Product not found"));
    if (articleRepository.existsByTenantIdAndProduct_Id(tenantId, productId)) {
      throw new YarnDomainException("I18", "I18: Product is already bound to a YarnArticle");
    }
    YarnArticle article =
        YarnArticle.createDraft(product, name, description, resolve(command, tenantId), serializer);
    articleRepository.saveAndFlush(article);
    appendAudit(article, YarnArticleAuditEventType.CREATED, 1, 1, null);
    return article;
  }

  @Transactional
  public YarnArticleMutationResponse createDraftResponse(
      UUID productId, String name, String description, YarnArticleSpecCommand command) {
    return mutationResponse(createDraft(productId, name, description, command));
  }

  @Transactional
  public YarnArticle updateSpec(UUID articleId, YarnArticleSpecCommand command) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnArticle article = requireArticle(articleId, tenantId);
    JsonNode before = serializer.auditSnapshot(article);
    int versionFrom = article.getArticleSpecVersion();
    article.updateSpec(resolve(command, tenantId), serializer);
    articleRepository.flush();
    appendAudit(
        article,
        YarnArticleAuditEventType.SPEC_UPDATED,
        versionFrom,
        article.getArticleSpecVersion(),
        before);
    return article;
  }

  @Transactional
  public YarnArticleMutationResponse updateSpecResponse(
      UUID articleId, YarnArticleSpecCommand command) {
    return mutationResponse(updateSpec(articleId, command));
  }

  @Transactional
  public YarnArticle updateMetadata(UUID articleId, String name, String description) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnArticle article = requireArticle(articleId, tenantId);
    JsonNode before = serializer.auditSnapshot(article);
    article.updateMetadata(name, description);
    articleRepository.flush();
    appendAudit(
        article,
        YarnArticleAuditEventType.METADATA_UPDATED,
        article.getArticleSpecVersion(),
        article.getArticleSpecVersion(),
        before);
    return article;
  }

  @Transactional
  public YarnArticleDto updateMetadataView(UUID articleId, String name, String description) {
    return YarnArticleDto.from(updateMetadata(articleId, name, description));
  }

  @Transactional
  public YarnArticle activate(UUID articleId) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnArticle article = requireArticle(articleId, tenantId);
    JsonNode before = serializer.auditSnapshot(article);
    article.activate();
    articleRepository.flush();
    appendAudit(
        article,
        YarnArticleAuditEventType.ACTIVATED,
        article.getArticleSpecVersion(),
        article.getArticleSpecVersion(),
        before);
    return article;
  }

  @Transactional
  public YarnArticleDto activateView(UUID articleId) {
    return YarnArticleDto.from(activate(articleId));
  }

  @Transactional
  public YarnArticle markObsolete(UUID articleId) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnArticle article = requireArticle(articleId, tenantId);
    JsonNode before = serializer.auditSnapshot(article);
    article.markObsolete();
    articleRepository.flush();
    appendAudit(
        article,
        YarnArticleAuditEventType.OBSOLETED,
        article.getArticleSpecVersion(),
        article.getArticleSpecVersion(),
        before);
    return article;
  }

  @Transactional
  public YarnArticleDto markObsoleteView(UUID articleId) {
    return YarnArticleDto.from(markObsolete(articleId));
  }

  @Transactional(readOnly = true)
  public Optional<YarnArticle> findById(UUID articleId) {
    return articleRepository.findByTenantIdAndId(TenantContext.requireTenantId(), articleId);
  }

  @Transactional(readOnly = true)
  public YarnArticleDto getViewById(UUID articleId) {
    return YarnArticleDto.from(requireArticle(articleId, TenantContext.requireTenantId()));
  }

  @Transactional(readOnly = true)
  public YarnArticleDto getViewByProductId(UUID productId) {
    UUID tenantId = TenantContext.requireTenantId();
    YarnArticle article =
        articleRepository
            .findByTenantIdAndProduct_Id(tenantId, productId)
            .orElseThrow(
                () -> new NotFoundException("YarnArticle not found for product: " + productId));
    return YarnArticleDto.from(article);
  }

  @Transactional(readOnly = true)
  public Optional<YarnArticleDto> findViewByUid(String uid) {
    if (uid == null || uid.isBlank()) {
      return Optional.empty();
    }
    return articleRepository
        .findByTenantIdAndUidIgnoreCase(TenantContext.requireTenantId(), uid.trim())
        .map(YarnArticleDto::from);
  }

  @Transactional(readOnly = true)
  public List<YarnArticleDto> findViewsByName(String name) {
    if (name == null || name.isBlank()) {
      return List.of();
    }
    return articleRepository
        .findByTenantIdAndNameIgnoreCaseOrderByCreatedAtAsc(
            TenantContext.requireTenantId(), name.trim())
        .stream()
        .map(YarnArticleDto::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<YarnDuplicateCandidateDto> duplicateCandidates(UUID articleId) {
    YarnArticle article = requireArticle(articleId, TenantContext.requireTenantId());
    return duplicateCandidates(article);
  }

  @Transactional(readOnly = true)
  public Page<YarnArticleListItemDto> list(
      com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus status,
      String q,
      BigDecimal texMin,
      BigDecimal texMax,
      Pageable pageable) {
    if ((texMin != null && texMin.signum() < 0) || (texMax != null && texMax.signum() < 0)) {
      throw new YarnDomainException("Yarn tex filters must not be negative");
    }
    if (texMin != null && texMax != null && texMin.compareTo(texMax) > 0) {
      throw new YarnDomainException("texMin must not be greater than texMax");
    }

    UUID tenantId = TenantContext.requireTenantId();
    Specification<YarnArticle> specification =
        (root, query, criteriaBuilder) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
          predicates.add(criteriaBuilder.isTrue(root.get("isActive")));
          if (status != null) {
            predicates.add(criteriaBuilder.equal(root.get("status"), status));
          }
          if (q != null && !q.isBlank()) {
            String pattern = LikePattern.literalContains(q.trim().toLowerCase(Locale.ROOT));
            predicates.add(
                criteriaBuilder.or(
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("name")),
                        pattern,
                        LikePattern.ESCAPE_CHARACTER),
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("canonicalDesignation")),
                        pattern,
                        LikePattern.ESCAPE_CHARACTER),
                    criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("sourceDesignation")),
                        pattern,
                        LikePattern.ESCAPE_CHARACTER)));
          }
          if (texMin != null) {
            predicates.add(
                criteriaBuilder.greaterThanOrEqualTo(
                    root.<BigDecimal>get("resultantLinearDensityTex"), texMin));
          }
          if (texMax != null) {
            predicates.add(
                criteriaBuilder.lessThanOrEqualTo(
                    root.<BigDecimal>get("resultantLinearDensityTex"), texMax));
          }
          return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    return articleRepository.findAll(specification, pageable).map(YarnArticleListItemDto::from);
  }

  @Transactional(readOnly = true)
  public Page<YarnArticleHistoryDto> history(UUID articleId, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    requireArticle(articleId, tenantId);
    return auditRepository
        .findByTenantIdAndArticle_Id(tenantId, articleId, pageable)
        .map(YarnArticleHistoryDto::from);
  }

  @Transactional(readOnly = true)
  public YarnArticleHistorySnapshotDto historyVersion(UUID articleId, int specVersion) {
    UUID tenantId = TenantContext.requireTenantId();
    requireArticle(articleId, tenantId);
    YarnArticleAudit audit =
        auditRepository
            .findByTenantIdAndArticle_IdAndEventTypeInAndSpecVersionTo(
                tenantId,
                articleId,
                List.of(YarnArticleAuditEventType.CREATED, YarnArticleAuditEventType.SPEC_UPDATED),
                specVersion)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "YarnArticle spec version not found: " + articleId + "/" + specVersion));
    return YarnArticleHistorySnapshotDto.from(audit);
  }

  private YarnArticle requireArticle(UUID articleId, UUID tenantId) {
    return articleRepository
        .findByTenantIdAndId(tenantId, articleId)
        .orElseThrow(() -> new NotFoundException("YarnArticle not found: " + articleId));
  }

  private YarnArticleSpec resolve(YarnArticleSpecCommand command, UUID tenantId) {
    command = command == null ? YarnArticleSpecCommand.empty() : command;
    YarnSpinningSystem spinningSystem =
        command.spinningSystemId() == null
            ? null
            : spinningSystemRepository
                .findByIdAndTenantId(command.spinningSystemId(), tenantId)
                .filter(system -> Boolean.TRUE.equals(system.getIsActive()))
                .orElseThrow(
                    () -> new YarnDomainException("I13", "I13: spinning system is not available"));

    List<YarnArticleSpec.CompositionInput> composition =
        command.composition().stream()
            .map(
                row ->
                    new YarnArticleSpec.CompositionInput(
                        resolveFiber(row.fiberId(), tenantId), row.percentage()))
            .toList();
    List<YarnArticleSpec.ComponentInput> components =
        command.structureComponents().stream()
            .map(
                row ->
                    new YarnArticleSpec.ComponentInput(
                        row.kind(),
                        row.componentIndex(),
                        row.layerRole(),
                        row.componentCountSystem(),
                        row.componentCountValue(),
                        row.fiberId() == null ? null : resolveFiber(row.fiberId(), tenantId),
                        row.label()))
            .toList();
    List<YarnArticleSpec.TwistStageInput> stages =
        command.twistStages().stream()
            .map(
                row ->
                    new YarnArticleSpec.TwistStageInput(
                        row.stageType(),
                        row.sequence(),
                        row.direction(),
                        row.turnsPerMeter(),
                        row.strandComponentIndex(),
                        resolveTestMethod(row.testMethodId(), tenantId)))
            .toList();

    return new YarnArticleSpec(
        command.originalCountSystem(),
        command.originalCountValue(),
        command.countBasis(),
        command.structureType(),
        command.foldCount(),
        command.filamentCount(),
        command.twistContractionPercent(),
        command.sourceDesignation(),
        command.materialForm(),
        command.spinningTechnologyFamily(),
        spinningSystem,
        command.filamentForm(),
        command.constructionFeatures(),
        composition,
        components,
        stages);
  }

  private Fiber resolveFiber(UUID fiberId, UUID tenantId) {
    if (fiberId == null) {
      throw new YarnDomainException("I14", "I14: composition Fiber is required");
    }
    return fiberRepository
        .findByTenantIdInAndId(List.of(tenantId, TenantContext.TEMPLATE_TENANT_ID), fiberId)
        .filter(fiber -> Boolean.TRUE.equals(fiber.getIsActive()))
        .filter(fiber -> fiber.getStatus() == FiberStatus.ACTIVE)
        .filter(Fiber::isPure)
        .orElseThrow(
            () -> new YarnDomainException("I14", "I14: active pure Fiber is not available"));
  }

  private YarnTestMethod resolveTestMethod(UUID testMethodId, UUID tenantId) {
    if (testMethodId == null) {
      return null;
    }
    return testMethodRepository
        .findByIdAndTenantId(testMethodId, tenantId)
        .filter(method -> Boolean.TRUE.equals(method.getIsActive()))
        .orElseThrow(() -> new YarnDomainException("I11", "I11: test method is not available"));
  }

  private void appendAudit(
      YarnArticle article,
      YarnArticleAuditEventType eventType,
      int versionFrom,
      int versionTo,
      JsonNode before) {
    JsonNode after = serializer.auditSnapshot(article);
    auditRepository.save(
        YarnArticleAudit.create(
            article,
            eventType,
            versionFrom,
            versionTo,
            after,
            serializer.changedSummary(before, after)));
  }

  private YarnArticleMutationResponse mutationResponse(YarnArticle article) {
    return new YarnArticleMutationResponse(
        YarnArticleDto.from(article), duplicateCandidates(article));
  }

  private List<YarnDuplicateCandidateDto> duplicateCandidates(YarnArticle article) {
    return article.getCanonicalKey() == null
        ? List.of()
        : articleRepository
            .findByTenantIdAndCanonicalKeyAndIdNotOrderByCreatedAtAsc(
                article.getTenantId(), article.getCanonicalKey(), article.getId())
            .stream()
            .map(YarnDuplicateCandidateDto::from)
            .toList();
  }
}
