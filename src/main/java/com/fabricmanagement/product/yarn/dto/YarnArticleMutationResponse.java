package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Successful yarn article write plus non-blocking duplicate advice")
public record YarnArticleMutationResponse(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) YarnArticleDto article,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<YarnDuplicateCandidateDto> duplicateCandidates) {}
