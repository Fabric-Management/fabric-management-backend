package com.fabricmanagement.product.yarn.app.adapter;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.common.infrastructure.ai.AIToolProvider;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.yarn.app.EndUseCatalogService;
import com.fabricmanagement.product.yarn.app.SpinningSystemCatalogService;
import com.fabricmanagement.product.yarn.app.TestMethodCatalogService;
import com.fabricmanagement.product.yarn.app.YarnAIDraftService;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountBasis;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import com.fabricmanagement.product.yarn.domain.vocabulary.FilamentForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistDirection;
import com.fabricmanagement.product.yarn.domain.vocabulary.TwistStageType;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnMaterialForm;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnStructureType;
import com.fabricmanagement.product.yarn.dto.YarnArticleDto;
import com.fabricmanagement.product.yarn.dto.YarnArticleListItemDto;
import com.fabricmanagement.product.yarn.dto.YarnDuplicateCandidateDto;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** AI tool adapter for tenant-scoped yarn article discovery and draft capture. */
@Component
@RequiredArgsConstructor
@Slf4j
public class YarnAIToolProvider implements AIToolProvider {

  static final int AI_SEARCH_LIMIT = 500;
  private static final int AI_RESPONSE_LIMIT = 5;
  private static final Set<String> SUPPORTED_TOOLS =
      Set.of("search_yarns", "get_yarn_info", "list_yarn_vocabularies", "create_yarn_article");
  private static final List<Class<? extends Enum<?>>> YARN_VOCABULARIES =
      List.of(
          CountSystem.class,
          CountBasis.class,
          YarnStructureType.class,
          TwistStageType.class,
          TwistDirection.class,
          YarnMaterialForm.class,
          SpinningTechnologyFamily.class,
          YarnConstructionFeature.class,
          FilamentForm.class);

  private final YarnArticleService articleService;
  private final SpinningSystemCatalogService spinningSystemCatalogService;
  private final EndUseCatalogService endUseCatalogService;
  private final TestMethodCatalogService testMethodCatalogService;
  private final YarnAIDraftService draftService;
  private final AIQueryNormalizer queryNormalizer;

  @Override
  public Set<String> getSupportedTools() {
    return SUPPORTED_TOOLS;
  }

  @Override
  public String execute(UUID tenantId, String toolName, Map<String, Object> parameters) {
    return switch (toolName) {
      case "search_yarns" -> searchYarns(parameters);
      case "get_yarn_info" -> getYarnInfo(parameters);
      case "list_yarn_vocabularies" -> listYarnVocabularies();
      case "create_yarn_article" -> createYarnArticle(parameters);
      default -> throw new IllegalArgumentException("Unknown AI tool: " + toolName);
    };
  }

  private String searchYarns(Map<String, Object> parameters) {
    String query = stringParameter(parameters, "query").orElse("");
    Optional<String> statusValue =
        stringParameter(parameters, "status").filter(value -> !value.isBlank());
    Optional<YarnArticleStatus> status = parseStatus(statusValue);
    if (statusValue.isPresent() && status.isEmpty()) {
      return "Invalid yarn status. Valid values: "
          + Arrays.stream(YarnArticleStatus.values())
              .map(Enum::name)
              .collect(Collectors.joining(", "));
    }

    String normalizedQuery = queryNormalizer.normalizeYarnQuery(query);
    Page<YarnArticleListItemDto> matches =
        articleService.list(
            status.orElse(null),
            normalizedQuery,
            null,
            null,
            PageRequest.of(0, AI_SEARCH_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")));

    if (query.isBlank()) {
      return String.format("Total yarn article count: %d", matches.getTotalElements());
    }
    if (matches.isEmpty()) {
      if (!normalizedQuery.equalsIgnoreCase(query)) {
        return String.format("No yarn article found for '%s' (or '%s').", query, normalizedQuery);
      }
      return String.format("No yarn article found for '%s'.", query);
    }

    StringBuilder result =
        new StringBuilder(String.format("Found %d yarn article(s):\n", matches.getTotalElements()));
    matches.getContent().stream()
        .limit(AI_RESPONSE_LIMIT)
        .forEach(
            article ->
                result.append(
                    String.format(
                        "- %s (UID: %s, status: %s, resultant tex: %s)\n",
                        displayName(article),
                        article.uid(),
                        article.status(),
                        article.resultantLinearDensityTex() == null
                            ? "not confirmed"
                            : article.resultantLinearDensityTex().toPlainString())));
    if (matches.getTotalElements() > AI_RESPONSE_LIMIT) {
      result.append(String.format("(Showing top %d)\n", AI_RESPONSE_LIMIT));
    }
    return result.toString();
  }

  private Optional<YarnArticleStatus> parseStatus(Optional<String> statusValue) {
    return statusValue.flatMap(
        value -> {
          try {
            return Optional.of(YarnArticleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
          } catch (IllegalArgumentException exception) {
            return Optional.empty();
          }
        });
  }

  private String getYarnInfo(Map<String, Object> parameters) {
    Optional<String> uid = stringParameter(parameters, "uid").filter(value -> !value.isBlank());
    Optional<String> name = stringParameter(parameters, "name").filter(value -> !value.isBlank());
    Optional<String> productId =
        stringParameter(parameters, "productId").filter(value -> !value.isBlank());
    if (List.of(uid, name, productId).stream().filter(Optional::isPresent).count() != 1) {
      return "Exactly one of uid, name, or productId is required.";
    }

    YarnArticleDto article;
    if (uid.isPresent()) {
      article = articleService.findViewByUid(uid.orElseThrow()).orElse(null);
    } else if (name.isPresent()) {
      List<YarnArticleDto> matches = articleService.findViewsByName(name.orElseThrow());
      if (matches.size() > 1) {
        String choices =
            matches.stream()
                .map(match -> String.format("- %s (%s)", match.name(), match.uid()))
                .collect(Collectors.joining("\n"));
        return String.format(
            "Found %d yarn articles named '%s':\n%s\nPlease use uid or productId.",
            matches.size(), name.orElseThrow(), choices);
      }
      article = matches.stream().findFirst().orElse(null);
    } else {
      try {
        article = articleService.getViewByProductId(UUID.fromString(productId.orElseThrow()));
      } catch (IllegalArgumentException exception) {
        return "Invalid UUID format for productId.";
      } catch (NotFoundException exception) {
        log.debug("Yarn article lookup by product failed", exception);
        article = null;
      }
    }

    if (article == null) {
      return "Yarn article not found.";
    }
    return formatArticle(article, articleService.duplicateCandidates(article.id()));
  }

  private String formatArticle(
      YarnArticleDto article, List<YarnDuplicateCandidateDto> duplicateCandidates) {
    StringBuilder info = new StringBuilder();
    info.append(String.format("Yarn article: %s\n", displayName(article)));
    info.append(String.format("UID: %s\n", article.uid()));
    info.append(String.format("Product ID: %s\n", article.productId()));
    info.append(String.format("Name: %s\n", article.name()));
    appendValue(info, "Description", article.description());
    info.append(String.format("Status: %s\n", article.status()));
    info.append(String.format("Article spec version: %d\n", article.articleSpecVersion()));
    appendValue(info, "Canonical designation", article.canonicalDesignation());
    appendValue(info, "Source designation", article.sourceDesignation());
    appendValue(info, "Original count system", article.originalCountSystem());
    appendValue(info, "Original count value", article.originalCountValue());
    appendValue(info, "Count basis", article.countBasis());
    appendValue(info, "Structure type", article.structureType());
    appendValue(info, "Fold count", article.foldCount());
    appendValue(info, "Filament count", article.filamentCount());
    appendValue(info, "Twist contraction percent", article.twistContractionPercent());
    appendValue(info, "Resultant linear density tex", article.resultantLinearDensityTex());
    appendValue(info, "Material form", article.materialForm());
    appendValue(info, "Spinning technology family", article.spinningTechnologyFamily());
    appendValue(info, "Filament form", article.filamentForm());
    appendValue(info, "Canonical key", article.canonicalKey());
    if (article.spinningSystem() != null) {
      info.append(
          String.format(
              "Spinning system: %s (%s, %s)\n",
              article.spinningSystem().name(),
              article.spinningSystem().code(),
              article.spinningSystem().id()));
    }

    if (!article.constructionFeatures().isEmpty()) {
      info.append("Construction features: ")
          .append(
              article.constructionFeatures().stream()
                  .map(Enum::name)
                  .collect(Collectors.joining(", ")))
          .append('\n');
    }
    if (!article.composition().isEmpty()) {
      info.append("Composition:\n");
      article
          .composition()
          .forEach(
              row ->
                  info.append(
                      String.format(
                          "- %s / %s: %s%% (source: %s, fiber: %s)\n",
                          row.fiberIsoCode(),
                          row.fiberName(),
                          row.percentage().toPlainString(),
                          row.materialSource(),
                          row.fiberId())));
    }
    if (!article.structureComponents().isEmpty()) {
      info.append("Structure components:\n");
      article
          .structureComponents()
          .forEach(
              row ->
                  info.append(
                      String.format(
                          "- %s %d: role=%s, count=%s %s, tex=%s, fiber=%s / %s, source=%s, label=%s\n",
                          row.kind(),
                          row.componentIndex(),
                          value(row.layerRole()),
                          value(row.componentCountValue()),
                          value(row.componentCountSystem()),
                          value(row.componentLinearDensityTex()),
                          value(row.fiberIsoCode()),
                          value(row.fiberName()),
                          value(row.materialSource()),
                          value(row.label()))));
    }
    if (!article.twistStages().isEmpty()) {
      info.append("Twist stages:\n");
      article
          .twistStages()
          .forEach(
              row ->
                  info.append(
                      String.format(
                          "- %s %d: direction=%s, turns/m=%s, strand=%s, test method=%s\n",
                          row.stageType(),
                          row.sequence(),
                          row.direction(),
                          value(row.turnsPerMeter()),
                          value(row.strandComponentIndex()),
                          value(row.testMethodCode()))));
    }
    if (!duplicateCandidates.isEmpty()) {
      info.append(
          String.format(
              "Advice: %d similar article(s) share this canonical key.\n",
              duplicateCandidates.size()));
      duplicateCandidates.forEach(
          candidate ->
              info.append(
                  String.format(
                      "- %s (%s, %s)\n",
                      candidate.name(), candidate.uid(), candidate.articleId())));
    }
    return info.toString();
  }

  private String listYarnVocabularies() {
    StringBuilder result = new StringBuilder("Yarn vocabularies:\n");
    YARN_VOCABULARIES.forEach(
        vocabulary -> {
          String members =
              Arrays.stream(vocabulary.getEnumConstants())
                  .map(Enum::name)
                  .collect(Collectors.joining(", "));
          result.append(String.format("- %s: %s\n", vocabulary.getSimpleName(), members));
        });

    result.append("\nSpinning systems:\n");
    spinningSystemCatalogService
        .list()
        .forEach(
            system ->
                result.append(
                    String.format(
                        "- %s: %s (family: %s, id: %s)\n",
                        system.getCode(),
                        system.getName(),
                        system.getTechnologyFamily(),
                        system.getId())));
    result.append("\nEnd uses:\n");
    endUseCatalogService
        .list()
        .forEach(
            endUse ->
                result.append(
                    String.format(
                        "- %s: %s (id: %s)\n",
                        endUse.getCode(), endUse.getName(), endUse.getId())));
    result.append("\nTwist test methods:\n");
    testMethodCatalogService
        .list()
        .forEach(
            method ->
                result.append(
                    String.format(
                        "- %s: %s (standard: %s, instrument: %s, property: %s, id: %s)\n",
                        method.getCode(),
                        method.getName(),
                        value(method.getStandardRef()),
                        value(method.getInstrument()),
                        value(method.getApplicablePropertyKey()),
                        method.getId())));
    return result.toString();
  }

  private String createYarnArticle(Map<String, Object> parameters) {
    try {
      String name = stringParameter(parameters, "name").orElse(null);
      String productIdValue = stringParameter(parameters, "productId").orElse(null);
      String unit = stringParameter(parameters, "unit").orElse(null);
      String sourceDesignation = stringParameter(parameters, "sourceDesignation").orElse(null);
      boolean hasProductId = productIdValue != null && !productIdValue.isBlank();
      boolean hasUnit = unit != null && !unit.isBlank();

      if (name == null || name.isBlank()) {
        return "Yarn article name is required.";
      }
      if (hasProductId == hasUnit) {
        return "Exactly one of productId or unit is required. Provide productId to bind an "
            + "existing YARN Product, or unit to auto-create one.";
      }

      UUID productId = null;
      if (hasProductId) {
        try {
          productId = UUID.fromString(productIdValue);
        } catch (IllegalArgumentException exception) {
          return "Invalid UUID format for productId.";
        }
      }

      YarnArticle article = draftService.createDraft(productId, unit, name, sourceDesignation);
      return String.format(
          "Yarn article created successfully.\n"
              + "Article ID: %s\n"
              + "UID: %s\n"
              + "Product ID: %s\n"
              + "Name: %s\n"
              + "Status: %s\n"
              + "Source designation: %s\n"
              + "Canonical fields await human confirmation.",
          article.getId(),
          article.getUid(),
          article.getProductId(),
          article.getName(),
          article.getStatus(),
          value(article.getSourceDesignation()));
    } catch (RuntimeException exception) {
      log.error("Error creating yarn article", exception);
      return "Error creating yarn article: " + exception.getMessage();
    }
  }

  private static Optional<String> stringParameter(
      Map<String, Object> parameters, String parameterName) {
    Object value = parameters.get(parameterName);
    return value == null ? Optional.empty() : Optional.of(value.toString());
  }

  private static void appendValue(StringBuilder target, String label, Object fieldValue) {
    if (fieldValue != null) {
      target.append(String.format("%s: %s\n", label, fieldValue));
    }
  }

  private static String displayName(YarnArticleListItemDto article) {
    if (article.canonicalDesignation() != null && !article.canonicalDesignation().isBlank()) {
      return article.canonicalDesignation();
    }
    if (article.name() != null && !article.name().isBlank()) {
      return article.name();
    }
    return article.uid();
  }

  private static String displayName(YarnArticleDto article) {
    if (article.canonicalDesignation() != null && !article.canonicalDesignation().isBlank()) {
      return article.canonicalDesignation();
    }
    if (article.name() != null && !article.name().isBlank()) {
      return article.name();
    }
    return article.uid();
  }

  private static String value(Object fieldValue) {
    return fieldValue == null ? "not set" : fieldValue.toString();
  }
}
