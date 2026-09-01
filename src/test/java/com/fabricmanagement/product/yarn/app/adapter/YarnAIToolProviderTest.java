package com.fabricmanagement.product.yarn.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.product.yarn.app.EndUseCatalogService;
import com.fabricmanagement.product.yarn.app.SpinningSystemCatalogService;
import com.fabricmanagement.product.yarn.app.TestMethodCatalogService;
import com.fabricmanagement.product.yarn.app.YarnAIDraftService;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.domain.reference.YarnEndUse;
import com.fabricmanagement.product.yarn.domain.reference.YarnSpinningSystem;
import com.fabricmanagement.product.yarn.domain.reference.YarnTestMethod;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class YarnAIToolProviderTest {

  @Mock private YarnArticleService articleService;
  @Mock private SpinningSystemCatalogService spinningSystemCatalogService;
  @Mock private EndUseCatalogService endUseCatalogService;
  @Mock private TestMethodCatalogService testMethodCatalogService;
  @Mock private YarnAIDraftService draftService;
  @Mock private AIQueryNormalizer queryNormalizer;

  @InjectMocks private YarnAIToolProvider provider;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
  }

  @Test
  void supportsExactlyTheFourYarnTools() {
    assertThat(provider.getSupportedTools())
        .containsExactlyInAnyOrder(
            "search_yarns", "get_yarn_info", "list_yarn_vocabularies", "create_yarn_article");
  }

  @Test
  void searchNormalizesFiltersAndCapsTheTenantScopedServiceQueryAtFiveHundred() {
    String query = "penye pamuk iplik 30/2";
    String normalized = "combed cotton yarn 30/2";
    YarnArticleListItemDto row =
        new YarnArticleListItemDto(
            UUID.randomUUID(),
            "YART-001",
            UUID.randomUUID(),
            "Combed cotton yarn 30/2",
            null,
            YarnArticleStatus.DRAFT,
            null,
            1);
    when(queryNormalizer.normalizeYarnQuery(query)).thenReturn(normalized);
    when(articleService.list(
            eq(YarnArticleStatus.DRAFT), eq(normalized), isNull(), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of(row)));

    String result =
        provider.execute(tenantId, "search_yarns", Map.of("query", query, "status", "draft"));

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(articleService)
        .list(eq(YarnArticleStatus.DRAFT), eq(normalized), isNull(), isNull(), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(YarnAIToolProvider.AI_SEARCH_LIMIT);
    assertThat(result)
        .contains("Found 1 yarn article(s)")
        .contains("Combed cotton yarn 30/2")
        .contains("YART-001")
        .contains("not confirmed");
  }

  @Test
  void noResultNamesOriginalAndNormalizedQueries() {
    when(queryNormalizer.normalizeYarnQuery("penye iplik")).thenReturn("combed yarn");
    when(articleService.list(isNull(), eq("combed yarn"), isNull(), isNull(), any()))
        .thenReturn(new PageImpl<>(List.of()));

    String result = provider.execute(tenantId, "search_yarns", Map.of("query", "penye iplik"));

    assertThat(result).contains("'penye iplik'").contains("'combed yarn'");
  }

  @Test
  void infoResolvesTheSameArticleByUidNameAndProductId() {
    YarnArticleDto article = articleDto();
    when(articleService.findViewByUid(article.uid())).thenReturn(Optional.of(article));
    when(articleService.findViewsByName(article.name())).thenReturn(List.of(article));
    when(articleService.getViewByProductId(article.productId())).thenReturn(article);
    when(articleService.duplicateCandidates(article.id())).thenReturn(List.of());

    assertThat(provider.execute(tenantId, "get_yarn_info", Map.of("uid", article.uid())))
        .contains(article.uid(), "Ne 30/2", "Article spec version: 3")
        .doesNotContain("Advice:");
    assertThat(provider.execute(tenantId, "get_yarn_info", Map.of("name", article.name())))
        .contains(article.uid());
    assertThat(
            provider.execute(
                tenantId, "get_yarn_info", Map.of("productId", article.productId().toString())))
        .contains(article.uid());
  }

  @Test
  void infoAddsAdvisoryDuplicateLineOnlyWhenCandidatesExist() {
    YarnArticleDto article = articleDto();
    YarnDuplicateCandidateDto candidate =
        new YarnDuplicateCandidateDto(UUID.randomUUID(), "YART-002", "Similar yarn");
    when(articleService.findViewByUid(article.uid())).thenReturn(Optional.of(article));
    when(articleService.duplicateCandidates(article.id())).thenReturn(List.of(candidate));

    String result = provider.execute(tenantId, "get_yarn_info", Map.of("uid", article.uid()));

    assertThat(result)
        .contains("Advice: 1 similar article(s)")
        .contains("Similar yarn", "YART-002");
  }

  @Test
  void vocabularyMembersAreReflectedAndTenantCataloguesAreIncluded() {
    YarnSpinningSystem spinningSystem = mock(YarnSpinningSystem.class);
    when(spinningSystem.getCode()).thenReturn("TENANT_RING");
    when(spinningSystem.getName()).thenReturn("Tenant ring");
    when(spinningSystem.getTechnologyFamily()).thenReturn(SpinningTechnologyFamily.RING);
    when(spinningSystem.getId()).thenReturn(UUID.randomUUID());
    YarnEndUse endUse = mock(YarnEndUse.class);
    when(endUse.getCode()).thenReturn("HOSIERY");
    when(endUse.getName()).thenReturn("Hosiery");
    when(endUse.getId()).thenReturn(UUID.randomUUID());
    YarnTestMethod testMethod = mock(YarnTestMethod.class);
    when(testMethod.getCode()).thenReturn("TENANT_TWIST");
    when(testMethod.getName()).thenReturn("Tenant twist test");
    when(testMethod.getId()).thenReturn(UUID.randomUUID());
    when(spinningSystemCatalogService.list()).thenReturn(List.of(spinningSystem));
    when(endUseCatalogService.list()).thenReturn(List.of(endUse));
    when(testMethodCatalogService.list()).thenReturn(List.of(testMethod));

    String result = provider.execute(tenantId, "list_yarn_vocabularies", Map.of());

    List<Class<? extends Enum<?>>> vocabularyTypes =
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
    vocabularyTypes.forEach(
        type -> {
          assertThat(result).contains(type.getSimpleName());
          Arrays.stream(type.getEnumConstants())
              .forEach(member -> assertThat(result).contains(member.name()));
        });
    assertThat(result)
        .contains("TENANT_RING", "family: RING")
        .contains("HOSIERY")
        .contains("TENANT_TWIST");
  }

  @Test
  void createPassesVerbatimDesignationToAtomicDraftBoundaryForEitherProductChoice() {
    UUID productId = UUID.randomUUID();
    YarnArticle created = createdArticle(productId);
    when(draftService.createDraft(productId, null, "Combed cotton", "Ne 30/2")).thenReturn(created);
    when(draftService.createDraft(null, "kg", "Rotor yarn", "OE 20/1")).thenReturn(created);

    String existingProductResult =
        provider.execute(
            tenantId,
            "create_yarn_article",
            Map.of(
                "name", "Combed cotton",
                "productId", productId.toString(),
                "sourceDesignation", "Ne 30/2"));
    String autoProductResult =
        provider.execute(
            tenantId,
            "create_yarn_article",
            Map.of(
                "name", "Rotor yarn",
                "unit", "kg",
                "sourceDesignation", "OE 20/1"));

    assertThat(existingProductResult).contains("Status: DRAFT", "await human confirmation");
    assertThat(autoProductResult).contains("Status: DRAFT");
    verify(draftService).createDraft(productId, null, "Combed cotton", "Ne 30/2");
    verify(draftService).createDraft(null, "kg", "Rotor yarn", "OE 20/1");
  }

  @Test
  void createRejectsBothOrNeitherProductChoice() {
    String neither =
        provider.execute(tenantId, "create_yarn_article", Map.of("name", "Draft yarn"));
    String both =
        provider.execute(
            tenantId,
            "create_yarn_article",
            Map.of(
                "name", "Draft yarn",
                "productId", UUID.randomUUID().toString(),
                "unit", "kg"));

    assertThat(neither).contains("Exactly one of productId or unit is required");
    assertThat(both).contains("Exactly one of productId or unit is required");
    verify(draftService, never()).createDraft(any(), any(), any(), any());
  }

  private YarnArticleDto articleDto() {
    return new YarnArticleDto(
        UUID.randomUUID(),
        "YART-001",
        UUID.randomUUID(),
        YarnArticleStatus.ACTIVE,
        3,
        CountSystem.NE,
        new BigDecimal("30"),
        CountBasis.COMPONENT,
        YarnStructureType.PLIED,
        2,
        null,
        null,
        new BigDecimal("39.37"),
        "Ne 30/2",
        "Ne 30/2",
        YarnMaterialForm.STAPLE_SPUN,
        SpinningTechnologyFamily.RING,
        null,
        null,
        "Combed cotton yarn",
        null,
        "canonical-key",
        (short) 1,
        List.of(YarnConstructionFeature.CORE_SPUN),
        List.of(),
        List.of(),
        List.of(),
        Instant.EPOCH,
        UUID.randomUUID(),
        Instant.EPOCH,
        UUID.randomUUID());
  }

  private YarnArticle createdArticle(UUID productId) {
    YarnArticle article = mock(YarnArticle.class);
    when(article.getId()).thenReturn(UUID.randomUUID());
    when(article.getUid()).thenReturn("YART-NEW");
    when(article.getProductId()).thenReturn(productId);
    when(article.getName()).thenReturn("Created yarn");
    when(article.getStatus()).thenReturn(YarnArticleStatus.DRAFT);
    when(article.getSourceDesignation()).thenReturn("Ne 30/2");
    return article;
  }
}
