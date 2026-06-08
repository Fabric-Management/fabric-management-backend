package com.fabricmanagement.common.infrastructure.web.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Custom ProblemDetail extension to provide strongly-typed fields for OpenAPI schema generation.
 * This ensures frontend clients (via openapi-typescript) see `code` and `errors` as typed
 * properties instead of a generic dynamic map.
 */
@Getter
@Setter
public class ApiProblemDetail extends ProblemDetail {

  @Schema(
      description = "Domain-specific error code (e.g., 'VALIDATION_ERROR', 'INSUFFICIENT_WEIGHT')",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String code;

  @Schema(description = "Validation field errors, if applicable")
  private Map<String, String> errors;

  @Schema(description = "Dynamic arguments for parameterized frontend messages")
  private Object[] args;

  protected ApiProblemDetail() {
    super();
  }

  public static ApiProblemDetail forStatusAndDetail(HttpStatus status, String detail) {
    ApiProblemDetail problemDetail = new ApiProblemDetail();
    problemDetail.setStatus(status.value());
    problemDetail.setDetail(detail);
    return problemDetail;
  }

  public static ApiProblemDetail forStatusAndDetail(int status, String detail) {
    ApiProblemDetail problemDetail = new ApiProblemDetail();
    problemDetail.setStatus(status);
    problemDetail.setDetail(detail);
    return problemDetail;
  }
}
