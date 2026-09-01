package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.CreateProductRequest;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YarnAIDraftServiceTest {

  @Mock private ProductFacade productFacade;
  @Mock private YarnArticleService articleService;

  @InjectMocks private YarnAIDraftService service;

  @Test
  void existingProductIsBoundWithoutCreatingAnotherProduct() {
    UUID productId = UUID.randomUUID();
    YarnArticle article = mock(YarnArticle.class);
    when(articleService.createDraft(eq(productId), eq("Draft yarn"), isNull(), any()))
        .thenReturn(article);

    assertThat(service.createDraft(productId, null, "Draft yarn", "Ne 30/2")).isSameAs(article);

    verifyNoInteractions(productFacade);
    ArgumentCaptor<YarnArticleSpecCommand> command =
        ArgumentCaptor.forClass(YarnArticleSpecCommand.class);
    verify(articleService)
        .createDraft(eq(productId), eq("Draft yarn"), isNull(), command.capture());
    assertEmptyCanonicalCapture(command.getValue(), "Ne 30/2");
  }

  @Test
  void unitCreatesYarnProductThenBindsTheEmptyDraftInTheSameApplicationBoundary() {
    UUID productId = UUID.randomUUID();
    YarnArticle article = mock(YarnArticle.class);
    when(productFacade.createProduct(any())).thenReturn(ProductDto.builder().id(productId).build());
    when(articleService.createDraft(eq(productId), eq("Draft yarn"), isNull(), any()))
        .thenReturn(article);

    assertThat(service.createDraft(null, "kg", "Draft yarn", "Ne 30/2")).isSameAs(article);

    ArgumentCaptor<CreateProductRequest> productRequest =
        ArgumentCaptor.forClass(CreateProductRequest.class);
    verify(productFacade).createProduct(productRequest.capture());
    assertThat(productRequest.getValue().getProductType()).isEqualTo(ProductType.YARN);
    assertThat(productRequest.getValue().getUnit()).isEqualTo("kg");
  }

  private void assertEmptyCanonicalCapture(
      YarnArticleSpecCommand command, String sourceDesignation) {
    assertThat(command.sourceDesignation()).isEqualTo(sourceDesignation);
    assertThat(command.originalCountSystem()).isNull();
    assertThat(command.originalCountValue()).isNull();
    assertThat(command.countBasis()).isNull();
    assertThat(command.structureType()).isNull();
    assertThat(command.foldCount()).isNull();
    assertThat(command.filamentCount()).isNull();
    assertThat(command.twistContractionPercent()).isNull();
    assertThat(command.materialForm()).isNull();
    assertThat(command.spinningTechnologyFamily()).isNull();
    assertThat(command.spinningSystemId()).isNull();
    assertThat(command.filamentForm()).isNull();
    assertThat(command.constructionFeatures()).isEmpty();
    assertThat(command.composition()).isEmpty();
    assertThat(command.structureComponents()).isEmpty();
    assertThat(command.twistStages()).isEmpty();
  }
}
