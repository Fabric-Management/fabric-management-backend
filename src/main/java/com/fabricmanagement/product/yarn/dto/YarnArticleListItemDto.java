package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Compact yarn article row for paged lists")
public record YarnArticleListItemDto(
    UUID id,
    String uid,
    UUID productId,
    String name,
    String canonicalDesignation,
    YarnArticleStatus status,
    BigDecimal resultantLinearDensityTex,
    int articleSpecVersion) {

  public static YarnArticleListItemDto from(YarnArticle article) {
    return new YarnArticleListItemDto(
        article.getId(),
        article.getUid(),
        article.getProductId(),
        article.getName(),
        article.getCanonicalDesignation(),
        article.getStatus(),
        article.getResultantLinearDensityTex(),
        article.getArticleSpecVersion());
  }
}
