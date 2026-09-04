package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleSpecSerializer;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleAuditRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnSpinningSystemRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnTestMethodRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class YarnArticleServiceBackfillTest {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Mock private YarnArticleRepository articleRepository;
  @Mock private YarnArticleAuditRepository auditRepository;
  @Mock private ProductRepository productRepository;
  @Mock private FiberRepository fiberRepository;
  @Mock private YarnSpinningSystemRepository spinningSystemRepository;
  @Mock private YarnTestMethodRepository testMethodRepository;
  @Mock private YarnArticleSpecSerializer serializer;

  @InjectMocks private YarnArticleService service;

  @BeforeEach
  void setTenant() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void productOverloadRejectsNonYarnBeforeResolveOrAnyRepositoryCall() {
    Product product = product(ProductType.FIBER, true);

    assertThatThrownBy(
            () ->
                service.createDraft(
                    product, product.getUid(), null, YarnArticleSpecCommand.draftCapture(null)))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertI18((YarnDomainException) error));

    verifyNoRepositoryInteractions();
  }

  @Test
  void productOverloadNullCheckIsFirstAndUsesI18WithoutRepositoryCalls() {
    assertThatThrownBy(
            () ->
                service.createDraft(
                    (Product) null, "ignored", null, YarnArticleSpecCommand.draftCapture(null)))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertI18((YarnDomainException) error));

    verifyNoRepositoryInteractions();
  }

  @Test
  void productOverloadRejectsInactiveProductBeforeResolveOrAnyRepositoryCall() {
    Product product = product(ProductType.YARN, false);

    assertThatThrownBy(
            () ->
                service.createDraft(
                    product, product.getUid(), null, YarnArticleSpecCommand.draftCapture(null)))
        .isInstanceOf(YarnDomainException.class)
        .satisfies(error -> assertI18((YarnDomainException) error));

    verifyNoRepositoryInteractions();
  }

  @Test
  void uuidOverloadKeepsItsChecksAndDelegatesToTheSingleProductWritePath() {
    UUID productId = UUID.randomUUID();
    Product product = product(ProductType.YARN, true);
    product.setId(productId);
    YarnArticle expected = mock(YarnArticle.class);
    YarnArticleService spy =
        spy(
            new YarnArticleService(
                articleRepository,
                auditRepository,
                productRepository,
                fiberRepository,
                spinningSystemRepository,
                testMethodRepository,
                serializer));
    when(productRepository.findByTenantIdAndId(TENANT_ID, productId))
        .thenReturn(Optional.of(product));
    when(articleRepository.existsByTenantIdAndProduct_Id(TENANT_ID, productId)).thenReturn(false);
    doReturn(expected).when(spy).createDraft(eq(product), eq(product.getUid()), eq(null), any());

    assertThat(
            spy.createDraft(
                productId, product.getUid(), null, YarnArticleSpecCommand.draftCapture("Ne 30/2")))
        .isSameAs(expected);

    verify(spy).createDraft(eq(product), eq(product.getUid()), eq(null), any());
    verify(articleRepository, never()).save(any());
  }

  @Test
  void productWritePathJoinsTheSurroundingTransaction() throws NoSuchMethodException {
    Transactional transactional =
        YarnArticleService.class
            .getMethod(
                "createDraft",
                Product.class,
                String.class,
                String.class,
                YarnArticleSpecCommand.class)
            .getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
  }

  private Product product(ProductType type, boolean active) {
    Product product = Product.create(type, "KG");
    product.setTenantId(TENANT_ID);
    product.setUid("YARN-BACKFILL-PRODUCT");
    product.setIsActive(active);
    return product;
  }

  private void verifyNoRepositoryInteractions() {
    verifyNoInteractions(
        articleRepository,
        auditRepository,
        productRepository,
        fiberRepository,
        spinningSystemRepository,
        testMethodRepository,
        serializer);
  }

  private static void assertI18(YarnDomainException error) {
    org.assertj.core.api.Assertions.assertThat(error.getInvariantIds()).containsExactly("I18");
  }
}
