package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Slim in-process yarn article reference")
public record YarnArticleSummaryDto(
    UUID productId,
    UUID articleId,
    YarnArticleStatus status,
    String name,
    String canonicalDesignation) {

  public static YarnArticleSummaryDto from(YarnArticle article) {
    return new YarnArticleSummaryDto(
        article.getProductId(),
        article.getId(),
        article.getStatus(),
        article.getName(),
        article.getCanonicalDesignation());
  }
}
