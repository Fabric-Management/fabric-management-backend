package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Advisory same-canonical-key article candidate")
public record YarnDuplicateCandidateDto(UUID articleId, String uid, String name) {

  public static YarnDuplicateCandidateDto from(YarnArticle article) {
    return new YarnDuplicateCandidateDto(article.getId(), article.getUid(), article.getName());
  }
}
