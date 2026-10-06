package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.Map;

/** A line's specification; moduleSpecs is free JSON, everything else is typed. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineSpecificationValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderLineSpecificationValue(
    @Schema(description = "Production module of the line") ModuleType moduleType,
    @Schema(
            additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
            description = "Free module specs, compared and saved as one whole value")
        Map<String, Object> moduleSpecs,
    @Valid
        @Schema(
            description =
                "Typed requirement profile input, resolved against the base's profile; omitted"
                    + " keeps the base profile")
        RequirementProfileInput requirementProfile) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException(
        "Unknown SalesOrderLineSpecificationValue property: " + name);
  }
}
