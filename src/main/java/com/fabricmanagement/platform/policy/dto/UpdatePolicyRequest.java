package com.fabricmanagement.platform.policy.dto;

import com.fabricmanagement.platform.policy.domain.PolicyEffect;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for updating a policy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdatePlatformPolicyRequest")
public class UpdatePolicyRequest {

  private String resource;

  private String action;

  private Integer priority;

  private PolicyEffect effect;

  private Boolean enabled;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> conditions;

  private String description;
}
