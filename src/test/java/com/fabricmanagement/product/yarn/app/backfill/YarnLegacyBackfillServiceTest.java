package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.yarn.app.YarnArticleService;
import com.fabricmanagement.product.yarn.app.YarnArticleSpecCommand;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationDiscovery;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationRecord;
import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import com.fabricmanagement.product.yarn.app.port.LegacyYarnDesignationSource;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueReason;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillReconciliation;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillLockRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YarnLegacyBackfillServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

  @Mock private ProductRepository productRepository;
  @Mock private YarnArticleRepository articleRepository;
  @Mock private YarnBackfillReconciliationRepository reconciliationRepository;
  @Mock private YarnBackfillLockRepository lockRepository;
  @Mock private YarnArticleService articleService;
  @Mock private LegacyYarnDesignationSource firstSource;
  @Mock private LegacyYarnDesignationSource secondSource;

  private Locale originalLocale;

  @BeforeEach
  void setUp() {
    originalLocale = Locale.getDefault();
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    Locale.setDefault(originalLocale);
    TenantContext.clear();
  }

  @Test
  void turkishLocaleStillGroupsRingSpellingsAndLoopUsesNoPerProductLookup() {
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    Product product = product("YARN-LOCALE");
    givenNormalInfrastructure();
    givenProducts(List.of(), product);
    when(firstSource.discover(TENANT_ID))
        .thenReturn(
            discovery(
                record(product, LegacyDesignationSourceKind.BATCH_ACTUAL, "RING NE 30/2", NOW),
                record(
                    product,
                    LegacyDesignationSourceKind.PURCHASE_ORDER_AGREED,
                    "ring ne 30/2",
                    NOW.minusSeconds(1))));

    YarnLegacyBackfillReport report = service(List.of(firstSource)).backfillTenant(TENANT_ID);

    ArgumentCaptor<YarnArticleSpecCommand> command =
        ArgumentCaptor.forClass(YarnArticleSpecCommand.class);
    verify(articleService)
        .createDraft(eq(product), eq(product.getUid()), eq(null), command.capture());
    assertThat(command.getValue().sourceDesignation()).isEqualTo("RING NE 30/2");
    assertThat(report.productsScanned()).isEqualTo(1);
    assertThat(report.articlesCreated()).isEqualTo(1);
    assertThat(report.totalQueueRowsCreated()).isZero();
    verify(productRepository, never()).findByTenantIdAndId(any(), any());
    verify(articleRepository, never()).existsByTenantIdAndProduct_Id(any(), any());
  }

  @Test
  void ambiguityWinsOverOverlengthAndPayloadRetainsEveryVerbatimCandidate() {
    Product product = product("YARN-CONFLICT");
    String overlength = "\uD83E\uDDF5".repeat(256);
    givenNormalInfrastructure();
    givenProducts(List.of(), product);
    when(firstSource.discover(TENANT_ID))
        .thenReturn(
            discovery(
                record(product, LegacyDesignationSourceKind.BATCH_ACTUAL, "Ne 30/2", NOW),
                record(product, LegacyDesignationSourceKind.RFQ_REQUESTED, "Ne 20/1", NOW),
                record(product, LegacyDesignationSourceKind.WORK_ORDER_TARGET, overlength, NOW)));

    YarnLegacyBackfillReport report = service(List.of(firstSource)).backfillTenant(TENANT_ID);

    ArgumentCaptor<YarnArticleSpecCommand> command =
        ArgumentCaptor.forClass(YarnArticleSpecCommand.class);
    verify(articleService)
        .createDraft(eq(product), eq(product.getUid()), eq(null), command.capture());
    assertThat(command.getValue().sourceDesignation()).isNull();
    ArgumentCaptor<YarnBackfillReconciliation> queue =
        ArgumentCaptor.forClass(YarnBackfillReconciliation.class);
    verify(reconciliationRepository).save(queue.capture());
    assertThat(queue.getValue().getReason()).isEqualTo(YarnBackfillQueueReason.AMBIGUOUS);
    assertThat(queue.getValue().getCandidates().path("schemaVersion").asInt()).isEqualTo(1);
    assertThat(queue.getValue().getCandidates().path("candidates").size()).isEqualTo(3);
    assertThat(queue.getValue().getCandidates().toString()).contains(overlength);
    assertThat(report.queueRowsCreated()).containsEntry(YarnBackfillQueueReason.AMBIGUOUS, 1L);
  }

  @Test
  void scanCountsExistingArticleAsSkippedInsteadOfFilteringProductOut() {
    Product missing = product("YARN-MISSING");
    Product existing = product("YARN-EXISTING");
    givenNormalInfrastructure();
    YarnArticle existingArticle = mock(YarnArticle.class);
    when(existingArticle.getProductId()).thenReturn(existing.getId());
    givenProducts(List.of(existingArticle), missing, existing);
    when(firstSource.discover(TENANT_ID)).thenReturn(LegacyDesignationDiscovery.empty());

    YarnLegacyBackfillReport report = service(List.of(firstSource)).backfillTenant(TENANT_ID);

    assertThat(report.productsScanned()).isEqualTo(2);
    assertThat(report.productsSkipped()).isEqualTo(1);
    assertThat(report.articlesCreated()).isEqualTo(1);
    verify(articleService).createDraft(eq(missing), eq(missing.getUid()), eq(null), any());
  }

  @Test
  void nullAndWhitespaceRawsContributeNothingAndTakeEmptyDraftBranch() {
    Product product = product("YARN-BLANK");
    givenNormalInfrastructure();
    givenProducts(List.of(), product);
    when(firstSource.discover(TENANT_ID))
        .thenReturn(
            discovery(
                record(product, LegacyDesignationSourceKind.BATCH_ACTUAL, null, NOW),
                record(product, LegacyDesignationSourceKind.RFQ_REQUESTED, "   ", NOW)));

    YarnLegacyBackfillReport report = service(List.of(firstSource)).backfillTenant(TENANT_ID);

    ArgumentCaptor<YarnArticleSpecCommand> command =
        ArgumentCaptor.forClass(YarnArticleSpecCommand.class);
    verify(articleService)
        .createDraft(eq(product), eq(product.getUid()), eq(null), command.capture());
    assertThat(command.getValue().sourceDesignation()).isNull();
    assertThat(report.recordsContributed().values()).containsOnly(0L);
    assertThat(report.totalQueueRowsCreated()).isZero();
  }

  @Test
  void providerOrderDoesNotChangeChoiceAndUnlinkedCountsAreSummed() {
    Product product = product("YARN-COMPOSED");
    givenNormalInfrastructure();
    givenProducts(List.of(), product);
    when(firstSource.discover(TENANT_ID))
        .thenReturn(
            new LegacyDesignationDiscovery(
                List.of(
                    record(
                        product,
                        LegacyDesignationSourceKind.PURCHASE_ORDER_AGREED,
                        "Ne 30/2",
                        NOW)),
                Map.of(LegacyDesignationSourceKind.RFQ_REQUESTED, 2L)));
    when(secondSource.discover(TENANT_ID))
        .thenReturn(
            new LegacyDesignationDiscovery(
                List.of(
                    record(
                        product,
                        LegacyDesignationSourceKind.BATCH_ACTUAL,
                        "NE 30/2",
                        NOW.minusSeconds(60))),
                Map.of(LegacyDesignationSourceKind.RFQ_REQUESTED, 3L)));

    YarnLegacyBackfillReport forward =
        service(List.of(firstSource, secondSource)).backfillTenant(TENANT_ID);
    ArgumentCaptor<YarnArticleSpecCommand> forwardCommand =
        ArgumentCaptor.forClass(YarnArticleSpecCommand.class);
    verify(articleService)
        .createDraft(eq(product), eq(product.getUid()), eq(null), forwardCommand.capture());

    reset(articleService);
    when(articleService.createDraft(any(Product.class), any(), any(), any()))
        .thenReturn(mock(YarnArticle.class));
    YarnLegacyBackfillReport reverse =
        service(List.of(secondSource, firstSource)).backfillTenant(TENANT_ID);
    ArgumentCaptor<YarnArticleSpecCommand> reverseCommand =
        ArgumentCaptor.forClass(YarnArticleSpecCommand.class);
    verify(articleService)
        .createDraft(eq(product), eq(product.getUid()), eq(null), reverseCommand.capture());

    assertThat(forwardCommand.getValue().sourceDesignation()).isEqualTo("NE 30/2");
    assertThat(reverseCommand.getValue().sourceDesignation())
        .isEqualTo(forwardCommand.getValue().sourceDesignation());
    assertThat(forward.recordsWithoutLinkage())
        .containsEntry(LegacyDesignationSourceKind.RFQ_REQUESTED, 5L);
    assertThat(reverse.recordsWithoutLinkage()).isEqualTo(forward.recordsWithoutLinkage());
  }

  @Test
  void providerOrderDoesNotChangeAmbiguousQueuePayload() {
    Product product = product("YARN-PAYLOAD");
    givenNormalInfrastructure();
    givenProducts(List.of(), product);
    when(firstSource.discover(TENANT_ID))
        .thenReturn(
            discovery(record(product, LegacyDesignationSourceKind.RFQ_REQUESTED, "Ne 20/1", NOW)));
    when(secondSource.discover(TENANT_ID))
        .thenReturn(
            discovery(
                record(
                    product,
                    LegacyDesignationSourceKind.BATCH_ACTUAL,
                    "Ne 40/1",
                    NOW.minusSeconds(60))));

    service(List.of(firstSource, secondSource)).backfillTenant(TENANT_ID);
    service(List.of(secondSource, firstSource)).backfillTenant(TENANT_ID);

    ArgumentCaptor<YarnBackfillReconciliation> queues =
        ArgumentCaptor.forClass(YarnBackfillReconciliation.class);
    verify(reconciliationRepository, times(2)).save(queues.capture());
    assertThat(queues.getAllValues().get(1).getCandidates())
        .isEqualTo(queues.getAllValues().get(0).getCandidates());
  }

  @Test
  void lockLoserReturnsZeroReportWithoutScanningOrDiscovering() {
    when(lockRepository.tryAcquire(TENANT_ID)).thenReturn(false);

    YarnLegacyBackfillReport report = service(List.of(firstSource)).backfillTenant(TENANT_ID);

    assertThat(report.outcome()).isEqualTo(YarnLegacyBackfillOutcome.LOCK_SKIPPED);
    assertThat(report.productsScanned()).isZero();
    assertThat(report.productsSkipped()).isZero();
    assertThat(report.articlesCreated()).isZero();
    assertThat(report.candidatesWritten()).isZero();
    assertThat(report.totalQueueRowsCreated()).isZero();
    verifyNoInteractions(
        firstSource, productRepository, articleRepository, reconciliationRepository);
    verifyNoInteractions(articleService);
  }

  private YarnLegacyBackfillService service(List<LegacyYarnDesignationSource> sources) {
    return new YarnLegacyBackfillService(
        sources,
        new DesignationProvenancePolicy(),
        productRepository,
        articleRepository,
        reconciliationRepository,
        lockRepository,
        articleService);
  }

  private void givenProducts(List<YarnArticle> existingArticles, Product... products) {
    when(productRepository.findByTenantIdAndProductTypeAndIsActiveTrue(TENANT_ID, ProductType.YARN))
        .thenReturn(List.of(products));
    when(articleRepository.findByTenantIdAndProduct_IdIn(eq(TENANT_ID), any()))
        .thenReturn(existingArticles);
  }

  private void givenNormalInfrastructure() {
    when(lockRepository.tryAcquire(TENANT_ID)).thenReturn(true);
    when(reconciliationRepository.findByTenantIdAndStatus(TENANT_ID, YarnBackfillQueueStatus.OPEN))
        .thenReturn(List.of());
    when(articleService.createDraft(any(Product.class), any(), any(), any()))
        .thenReturn(mock(YarnArticle.class));
  }

  private Product product(String uid) {
    Product product = Product.create(ProductType.YARN, "KG");
    product.setId(UUID.randomUUID());
    product.setTenantId(TENANT_ID);
    product.setUid(uid);
    product.setIsActive(true);
    return product;
  }

  private LegacyDesignationDiscovery discovery(LegacyDesignationRecord... records) {
    return new LegacyDesignationDiscovery(List.of(records), Map.of());
  }

  private LegacyDesignationRecord record(
      Product product, LegacyDesignationSourceKind kind, String rawValue, Instant recordedAt) {
    return new LegacyDesignationRecord(
        product.getId(), kind, rawValue, recordedAt, UUID.randomUUID().toString());
  }
}
