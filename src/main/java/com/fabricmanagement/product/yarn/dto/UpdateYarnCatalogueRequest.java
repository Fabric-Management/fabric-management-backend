package com.fabricmanagement.product.yarn.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    description = "Replace the mutable presentation fields of a yarn catalogue row",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UpdateYarnCatalogueRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 100) String name,
    String description,
    Integer displayOrder) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown yarn catalogue patch field: " + name);
  }
}
