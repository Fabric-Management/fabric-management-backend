package com.fabricmanagement.product.fabric.dto;

import com.fabricmanagement.product.fabric.domain.article.FabricArticle;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricConstructionFamily;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "FabricArticleListItem")
public record FabricArticleListItemDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String articleCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricArticleStatus status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FabricFormation formation,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String constructionName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        FabricConstructionFamily constructionFamily,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) BigDecimal gsmFinished,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        BigDecimal nominalFinishedWidthCm,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {

  public static FabricArticleListItemDto from(FabricArticle article) {
    var construction = article.getConstruction();
    return new FabricArticleListItemDto(
        article.getId(),
        article.getProductId(),
        article.getProduct().getName(),
        article.getArticleCode(),
        article.getStatus(),
        article.getFormation(),
        construction == null ? null : construction.getName(),
        construction == null ? null : construction.getFamily(),
        article.getGsmFinished(),
        article.getNominalFinishedWidthCm(),
        article.getUpdatedAt());
  }
}
