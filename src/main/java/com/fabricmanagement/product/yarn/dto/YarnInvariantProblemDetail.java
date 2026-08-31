package com.fabricmanagement.product.yarn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

@Schema(description = "RFC 9457 conflict response for one or more yarn invariant violations")
public record YarnInvariantProblemDetail(
    URI type,
    String title,
    int status,
    String detail,
    URI instance,
    String code,
    Object[] args,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> invariantIds) {}
