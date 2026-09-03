package com.fabricmanagement.product.yarn.app;

import com.fabricmanagement.product.core.api.facade.ProductFacade;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.CreateProductRequest;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic application boundary for the AI tool's Product plus empty YarnArticle draft creation. */
@Service
@RequiredArgsConstructor
public class YarnAIDraftService {

  private final ProductFacade productFacade;
  private final YarnArticleService articleService;

  @Transactional
  public YarnArticle createDraft(
      UUID productId, String unit, String name, String sourceDesignation) {
    UUID resolvedProductId = productId;
    if (resolvedProductId == null) {
      resolvedProductId =
          productFacade
              .createProduct(
                  CreateProductRequest.builder().productType(ProductType.YARN).unit(unit).build())
              .getId();
    }
    return articleService.createDraft(
        resolvedProductId, name, null, YarnArticleSpecCommand.draftCapture(sourceDesignation));
  }
}
